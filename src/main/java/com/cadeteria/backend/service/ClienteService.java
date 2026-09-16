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
import java.util.List;
import java.util.Map;
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

    public record ClientesPagina(List<ClienteResponse> items, long total, int pagina, int totalPaginas) {}

    /**
     * Listado de "Clientes" paginado (mejora 2026-09-16) — reemplaza al viejo
     * {@code listar(q)} que traía TODA la tabla `pedido` a memoria en cada apertura de
     * la pantalla. El agregado (cantidad/monto/último pedido) se calcula en SQL
     * ({@link ClienteRepository#paginaResumen}); acá solo se completan nombre/empresa/etc.
     * para las filas de la página actual, no para todas — si el teléfono no tiene una
     * ficha de {@link Cliente} guardada, el nombre se resuelve con una única consulta
     * puntual (indexada) al último pedido de ESE teléfono, no a toda la tabla.
     */
    @Transactional(readOnly = true)
    public ClientesPagina listarPaginado(String q, int pagina, int tamano) {
        String qNormalizado = (q == null || q.isBlank()) ? null : q.trim();
        int paginaSegura = Math.max(0, pagina);
        int tamanoSeguro = Math.min(Math.max(1, tamano), 100);
        var page = repo.paginaResumen(qNormalizado,
                org.springframework.data.domain.PageRequest.of(paginaSegura, tamanoSeguro));

        List<String> telefonos = page.getContent().stream().map(ClienteRepository.ClienteResumenRow::getTelefono).toList();
        Map<String, Cliente> guardados = repo.findAllById(telefonos).stream()
                .collect(Collectors.toMap(Cliente::getTelefono, c -> c));

        List<ClienteResponse> items = page.getContent().stream()
                .map(fila -> construirResumenDesdeFila(fila, guardados.get(fila.getTelefono())))
                .toList();
        return new ClientesPagina(items, page.getTotalElements(), paginaSegura, page.getTotalPages());
    }

    private ClienteResponse construirResumenDesdeFila(ClienteRepository.ClienteResumenRow fila, Cliente guardado) {
        String telefono = fila.getTelefono();
        String nombreManual = guardado != null ? guardado.getNombreContacto() : null;
        String nombre = (nombreManual != null && !nombreManual.isBlank())
                ? nombreManual
                : pedidoRepo.findFirstByClienteTelefonoOrderByCreadoEnDesc(telefono).map(Pedido::getClienteNombre).orElse(null);
        return new ClienteResponse(
                telefono, nombre,
                guardado != null ? guardado.getEmpresa() : null,
                guardado != null ? guardado.getTarifaEspecial() : null,
                guardado != null && guardado.isProblematico(),
                guardado != null ? guardado.getNotasProblematico() : null,
                guardado == null || guardado.isActivo(),
                fila.getCantidadPedidos() != null ? fila.getCantidadPedidos() : 0,
                fila.getMontoTotal() != null ? fila.getMontoTotal() : BigDecimal.ZERO,
                fila.getUltimoPedidoEn(),
                guardado != null ? guardado.getModalidadFacturacion() : "CONTADO",
                saldoPendiente(telefono, guardado));
    }

    @Transactional(readOnly = true)
    public ClienteFichaResponse ficha(String telefono) {
        // El resumen (cantidad/monto total/último pedido) sí necesita el historial completo para sumar bien —
        // pero los "recientes" que se muestran en pantalla se piden ya recortados a 30 en la base, no en Java
        // (antes traía TODO el historial del cliente para descartar el resto acá con .limit(30)).
        List<Pedido> pedidosCompletos = pedidoRepo.findByClienteTelefonoOrderByCreadoEnDesc(telefono);
        Cliente guardado = repo.findById(telefono).orElse(null);
        ClienteResponse resumen = construirResumen(telefono, pedidosCompletos, guardado);
        List<Pedido> recientesPedidos = pedidoRepo.findByClienteTelefonoOrderByCreadoEnDesc(
                telefono, org.springframework.data.domain.PageRequest.of(0, 30));
        List<PedidoResumenResponse> recientes = recientesPedidos.stream()
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
