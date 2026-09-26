package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.IncidenciaDtos.IncidenciaRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.Incidencia;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.IncidenciaRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Incidencias/tickets del admin (ronda 4, punto 63; ronda 7: ligadas a un pedido
 * puntual) — para reclamos o notas operativas, atadas o no a un envío específico.
 */
@Service
@Transactional
public class IncidenciaService {

    private final IncidenciaRepository repo;
    private final PedidoRepository pedidoRepo;
    private final CadeteRepository cadeteRepo;

    public IncidenciaService(IncidenciaRepository repo, PedidoRepository pedidoRepo, CadeteRepository cadeteRepo) {
        this.repo = repo;
        this.pedidoRepo = pedidoRepo;
        this.cadeteRepo = cadeteRepo;
    }

    /** Para bloquear la asignación automática (ronda 10, punto 108). */
    @Transactional(readOnly = true)
    public boolean tieneIncidenciaGraveAbierta(String cadeteId) {
        return repo.existsByCadeteIdAndPrioridadAndEstado(cadeteId, "GRAVE", "ABIERTA");
    }

    @Transactional(readOnly = true)
    public List<Incidencia> listar(String estado) {
        if (estado == null || estado.isBlank()) {
            return repo.findAllByOrderByCreadaEnDesc();
        }
        return repo.findByEstadoOrderByCreadaEnDesc(estado.toUpperCase());
    }

    @Transactional(readOnly = true)
    public List<Incidencia> porPedido(String pedidoId) {
        return repo.findByPedidoIdOrderByCreadaEnDesc(pedidoId);
    }

    @Transactional(readOnly = true)
    public List<Incidencia> porCadete(String cadeteId) {
        return repo.findByCadeteIdOrderByCreadaEnDesc(cadeteId);
    }

    public Incidencia crear(IncidenciaRequest req, String adminUsername) {
        Incidencia i = new Incidencia();
        i.setId(UUID.randomUUID().toString());
        i.setTitulo(req.titulo());
        i.setDescripcion(req.descripcion());
        i.setEstado("ABIERTA");
        i.setPrioridad(req.prioridad() == null || req.prioridad().isBlank() ? "NORMAL" : req.prioridad());
        i.setCreadaEn(Instant.now());
        i.setCreadaPorUsername(adminUsername);
        if (req.pedidoId() != null && !req.pedidoId().isBlank()) {
            Pedido pedido = pedidoRepo.findById(req.pedidoId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Pedido", req.pedidoId()));
            i.setPedidoId(pedido.getId());
            i.setPedidoNumero(pedido.getNumero());
        }
        if (req.cadeteId() != null && !req.cadeteId().isBlank()) {
            Cadete cadete = cadeteRepo.findById(req.cadeteId())
                    .orElseThrow(() -> ResourceNotFoundException.of("Cadete", req.cadeteId()));
            i.setCadeteId(cadete.getId());
            i.setCadeteNombre(cadete.getNombre() + " " + cadete.getApellido());
        }
        return repo.save(i);
    }

    public Incidencia cerrar(String id, String adminUsername) {
        Incidencia i = get(id);
        if ("CERRADA".equals(i.getEstado())) {
            throw new BadRequestException("La incidencia ya está cerrada.");
        }
        i.setEstado("CERRADA");
        i.setCerradaEn(Instant.now());
        i.setCerradaPorUsername(adminUsername);
        // Incidente de un reclamo del cliente (2026-09-26): el pedido deja de figurar con reclamo abierto.
        if ("RECLAMO_CLIENTE".equals(i.getOrigen()) && i.getPedidoId() != null) {
            if (i.getMotivoCierre() == null) i.setMotivoCierre("Cerrado desde el panel");
            pedidoRepo.findById(i.getPedidoId()).ifPresent(p -> {
                p.setReclamoEstado("CERRADO");
                pedidoRepo.save(p);
            });
        }
        return repo.save(i);
    }

    public Incidencia reabrir(String id) {
        Incidencia i = get(id);
        i.setEstado("ABIERTA");
        i.setCerradaEn(null);
        i.setCerradaPorUsername(null);
        return repo.save(i);
    }

    private Incidencia get(String id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Incidencia", id));
    }
}
