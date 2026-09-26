package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.ReporteCliente;
import com.cadeteria.backend.repository.ReporteClienteRepository;
import com.cadeteria.backend.util.TelefonoUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Reporte del cadete sobre el cliente de un viaje propio (spec-antiabuso §4 Fase 3). Solo
 * acumula: no bloquea ni marca nada por sí solo — el admin lo ve en el aviso del cliente
 * (ver {@link ClienteService#avisos}) y decide.
 */
@Service
@Transactional
public class ReporteClienteService {

    public static final Set<String> TIPOS = Set.of("DEMORO", "NO_DECLARO_VALORES", "PEDIDO_FALSO", "OTRO");

    private final ReporteClienteRepository repo;
    private final PedidoService pedidoService;

    public ReporteClienteService(ReporteClienteRepository repo, PedidoService pedidoService) {
        this.repo = repo;
        this.pedidoService = pedidoService;
    }

    public ReporteCliente reportar(String pedidoId, String cadeteUsername, String tipo, String nota) {
        if (tipo == null || !TIPOS.contains(tipo)) {
            throw new BadRequestException("Tipo de reporte inválido.");
        }
        String notaLimpia = nota == null || nota.isBlank() ? null : nota.trim();
        if ("OTRO".equals(tipo) && notaLimpia == null) {
            throw new BadRequestException("Contanos qué pasó en la nota.");
        }
        if (notaLimpia != null && notaLimpia.length() > 500) {
            notaLimpia = notaLimpia.substring(0, 500);
        }
        // getDeCadete valida que el pedido sea del cadete (si no, 403/404).
        Pedido pedido = pedidoService.getDeCadete(pedidoId, cadeteUsername);
        Cadete cadete = pedido.getCadeteAsignado();
        if (!"OTRO".equals(tipo) && repo.existsByPedidoIdAndCadeteIdAndTipo(pedido.getId(), cadete.getId(), tipo)) {
            throw new ConflictException("Ya reportaste esto para este viaje.");
        }

        ReporteCliente r = new ReporteCliente();
        r.setId(UUID.randomUUID().toString());
        r.setTelefono(TelefonoUtils.normalizar(pedido.getClienteTelefono()));
        r.setTipo(tipo);
        r.setPedidoId(pedido.getId());
        r.setPedidoNumero(pedido.getNumero());
        r.setCadeteId(cadete.getId());
        r.setCadeteNombre((cadete.getNombre() + " " + cadete.getApellido()).trim());
        r.setNota(notaLimpia);
        return repo.save(r);
    }

    @Transactional(readOnly = true)
    public List<ReporteCliente> deTelefono(String telefonoCrudo) {
        return repo.findByTelefonoOrderByCreadoEnDesc(TelefonoUtils.normalizar(telefonoCrudo));
    }
}
