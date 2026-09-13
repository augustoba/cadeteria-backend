package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.ClienteDtos.ClienteAvisoResponse;
import com.cadeteria.backend.dto.ClienteDtos.ClienteFichaResponse;
import com.cadeteria.backend.dto.ClienteDtos.ClienteRequest;
import com.cadeteria.backend.dto.ClienteDtos.ClienteResponse;
import com.cadeteria.backend.dto.ClienteDtos.PedidoResumenResponse;
import com.cadeteria.backend.model.Cliente;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.ClienteRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Ficha de cliente por teléfono (ronda 4, puntos 44 ficha/historial, 58 marcar
 * problemático, 67 ABM) — el teléfono es la clave natural: el nombre y el historial se
 * derivan de Pedido en tiempo real, y `Cliente` solo guarda lo que se carga a mano.
 */
@Service
@Transactional
public class ClienteService {

    private final ClienteRepository repo;
    private final PedidoRepository pedidoRepo;

    public ClienteService(ClienteRepository repo, PedidoRepository pedidoRepo) {
        this.repo = repo;
        this.pedidoRepo = pedidoRepo;
    }

    @Transactional(readOnly = true)
    public List<ClienteResponse> listar(String q) {
        Map<String, List<Pedido>> porTelefono = pedidoRepo.findAll().stream()
                .filter(p -> p.getClienteTelefono() != null && !p.getClienteTelefono().isBlank())
                .collect(Collectors.groupingBy(Pedido::getClienteTelefono));
        Map<String, Cliente> guardados = repo.findAll().stream()
                .collect(Collectors.toMap(Cliente::getTelefono, c -> c));

        TreeSet<String> telefonos = new TreeSet<>();
        telefonos.addAll(porTelefono.keySet());
        telefonos.addAll(guardados.keySet());

        List<ClienteResponse> resultado = new ArrayList<>();
        for (String telefono : telefonos) {
            List<Pedido> pedidos = porTelefono.getOrDefault(telefono, List.of()).stream()
                    .sorted(Comparator.comparing(Pedido::getCreadoEn).reversed())
                    .toList();
            resultado.add(construirResumen(telefono, pedidos, guardados.get(telefono)));
        }

        if (q == null || q.isBlank()) {
            return resultado.stream().sorted(Comparator.comparing(
                    ClienteResponse::ultimoPedidoEn, Comparator.nullsLast(Comparator.reverseOrder()))).toList();
        }
        String needle = q.trim().toLowerCase();
        return resultado.stream()
                .filter(c -> contiene(c.telefono(), needle) || contiene(c.nombreContacto(), needle) || contiene(c.empresa(), needle))
                .sorted(Comparator.comparing(
                        ClienteResponse::ultimoPedidoEn, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private boolean contiene(String valor, String needle) {
        return valor != null && valor.toLowerCase().contains(needle);
    }

    @Transactional(readOnly = true)
    public ClienteFichaResponse ficha(String telefono) {
        List<Pedido> pedidos = pedidoRepo.findByClienteTelefonoOrderByCreadoEnDesc(telefono);
        Cliente guardado = repo.findById(telefono).orElse(null);
        ClienteResponse resumen = construirResumen(telefono, pedidos, guardado);
        List<PedidoResumenResponse> recientes = pedidos.stream()
                .limit(30)
                .map(p -> new PedidoResumenResponse(p.getId(), p.getNumero(), p.getCreadoEn(), p.getPrecio(), p.getEstado().getId()))
                .toList();
        return new ClienteFichaResponse(resumen, recientes);
    }

    public ClienteResponse guardar(ClienteRequest req) {
        Cliente c = repo.findById(req.telefono()).orElseGet(Cliente::new);
        c.setTelefono(req.telefono());
        c.setNombreContacto(req.nombreContacto());
        c.setEmpresa(req.empresa());
        c.setTarifaEspecial(req.tarifaEspecial());
        c.setProblematico(req.problematico());
        c.setNotasProblematico(req.notasProblematico());
        c.setActivo(req.activo());
        c.setModalidadFacturacion(req.modalidadFacturacion() == null || req.modalidadFacturacion().isBlank()
                ? "CONTADO" : req.modalidadFacturacion());
        repo.save(c);
        List<Pedido> pedidos = pedidoRepo.findByClienteTelefonoOrderByCreadoEnDesc(req.telefono());
        return construirResumen(req.telefono(), pedidos, c);
    }

    /**
     * Cierra el período de cuenta corriente: todo lo FINALIZADO hasta ahora queda
     * marcado como ya cobrado, arrancando de cero para el próximo mes (ronda 10, punto 100).
     */
    public com.cadeteria.backend.dto.ClienteDtos.LiquidarCuentaCorrienteResponse liquidarCuentaCorriente(String telefono) {
        Cliente c = repo.findById(telefono)
                .orElseThrow(() -> com.cadeteria.backend.common.ResourceNotFoundException.of("Cliente", telefono));
        BigDecimal saldo = saldoPendiente(telefono, c);
        Instant ahora = Instant.now();
        c.setCuentaCorrienteLiquidadaHasta(ahora);
        repo.save(c);
        return new com.cadeteria.backend.dto.ClienteDtos.LiquidarCuentaCorrienteResponse(saldo, ahora);
    }

    private BigDecimal saldoPendiente(String telefono, Cliente c) {
        if (c == null || !"CUENTA_CORRIENTE".equals(c.getModalidadFacturacion())) return BigDecimal.ZERO;
        Instant desde = c.getCuentaCorrienteLiquidadaHasta();
        return pedidoRepo.findByClienteTelefonoOrderByCreadoEnDesc(telefono).stream()
                .filter(p -> "FINALIZADO".equals(p.getEstado().getId()))
                .filter(p -> desde == null || p.getCreadoEn().isAfter(desde))
                .map(Pedido::getPrecio)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    /** Aviso rápido al cargar un pedido nuevo — si el teléfono es problemático o tiene tarifa especial. */
    @Transactional(readOnly = true)
    public ClienteAvisoResponse aviso(String telefono) {
        Cliente c = repo.findById(telefono).orElse(null);
        if (c == null || !c.isActivo()) {
            return new ClienteAvisoResponse(false, null, null);
        }
        return new ClienteAvisoResponse(c.isProblematico(), c.getNotasProblematico(), c.getTarifaEspecial());
    }

    private ClienteResponse construirResumen(String telefono, List<Pedido> pedidos, Cliente guardado) {
        String nombreManual = guardado != null ? guardado.getNombreContacto() : null;
        String nombre = (nombreManual != null && !nombreManual.isBlank())
                ? nombreManual
                : pedidos.stream().findFirst().map(Pedido::getClienteNombre).orElse(null);
        BigDecimal montoTotal = pedidos.stream().map(Pedido::getPrecio).reduce(BigDecimal.ZERO, BigDecimal::add);
        var ultimoPedidoEn = pedidos.stream().findFirst().map(Pedido::getCreadoEn).orElse(null);
        return new ClienteResponse(
                telefono,
                nombre,
                guardado != null ? guardado.getEmpresa() : null,
                guardado != null ? guardado.getTarifaEspecial() : null,
                guardado != null && guardado.isProblematico(),
                guardado != null ? guardado.getNotasProblematico() : null,
                guardado == null || guardado.isActivo(),
                pedidos.size(),
                montoTotal,
                ultimoPedidoEn,
                guardado != null ? guardado.getModalidadFacturacion() : "CONTADO",
                saldoPendiente(telefono, guardado));
    }
}
