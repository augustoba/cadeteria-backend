package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.EnElLugarDtos.FichaCadete;
import com.cadeteria.backend.dto.EnElLugarDtos.RegistroPedido;
import com.cadeteria.backend.dto.EnElLugarDtos.ResumenCadete;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoParada;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Registros del control "Retirado / Entregado solo en el lugar" (carril B, 2026-09-28) para la ficha
 * del cadete y Métricas. Salen de los pedidos (no hay contadores en el cadete), salvo los intentos con
 * GPS falso, que no llegan a marcar nada y por eso se cuentan en el cadete.
 */
@Service
@Transactional(readOnly = true)
public class EnElLugarService {

    /** Desde siempre para la ficha (la fecha más vieja que MySQL acepta sin problemas). */
    private static final Instant DESDE_SIEMPRE = Instant.parse("2000-01-01T00:00:00Z");
    private static final int ULTIMOS_EN_FICHA = 20;

    private final PedidoRepository pedidoRepo;
    private final CadeteRepository cadeteRepo;

    public EnElLugarService(PedidoRepository pedidoRepo, CadeteRepository cadeteRepo) {
        this.pedidoRepo = pedidoRepo;
        this.cadeteRepo = cadeteRepo;
    }

    public FichaCadete fichaCadete(String cadeteId) {
        Cadete cadete = cadeteRepo.findById(cadeteId).orElseThrow(() -> ResourceNotFoundException.of("Cadete", cadeteId));
        List<RegistroPedido> registros = pedidoRepo.conRegistrosEnElLugar(cadeteId, DESDE_SIEMPRE, Instant.now().plusSeconds(60))
                .stream().map(EnElLugarService::registro).toList();
        return new FichaCadete(resumen(cadete, registros),
                registros.subList(0, Math.min(ULTIMOS_EN_FICHA, registros.size())));
    }

    /** Un renglón por cadete con algo anotado en el rango, o con algún intento de GPS falso alguna vez. */
    public List<ResumenCadete> metricas(Instant desde, Instant hasta) {
        Map<String, List<RegistroPedido>> porCadete = new LinkedHashMap<>();
        for (Pedido p : pedidoRepo.conRegistrosEnElLugar(null, desde, hasta)) {
            if (p.getCadeteAsignado() == null) continue;
            RegistroPedido r = registro(p, desde, hasta);
            if (r.tipos().isEmpty()) continue;
            porCadete.computeIfAbsent(p.getCadeteAsignado().getId(), k -> new ArrayList<>()).add(r);
        }
        List<ResumenCadete> resultado = new ArrayList<>();
        for (Cadete c : cadeteRepo.findAll()) {
            List<RegistroPedido> registros = porCadete.getOrDefault(c.getId(), List.of());
            boolean simulada = c.getIntentosUbicacionSimulada() != null && c.getIntentosUbicacionSimulada() > 0;
            if (!registros.isEmpty() || simulada) {
                resultado.add(resumen(c, registros));
            }
        }
        resultado.sort(Comparator.comparingLong((ResumenCadete r) -> r.vecesFueraZona() + r.intentosUbicacionSimulada())
                .reversed());
        return resultado;
    }

    static ResumenCadete resumen(Cadete c, List<RegistroPedido> registros) {
        long fueraZona = registros.stream().flatMap(r -> r.tipos().stream()).filter(t -> t.endsWith("_FUERA_ZONA")).count();
        long imprecisa = registros.stream().filter(r -> r.tipos().contains("UBICACION_IMPRECISA")).count();
        long porAdmin = registros.stream().filter(r -> r.tipos().contains("FINALIZADO_POR_ADMIN")).count();
        return new ResumenCadete(c.getId(), nombre(c), fueraZona, imprecisa, porAdmin,
                c.getIntentosUbicacionSimulada() == null ? 0 : c.getIntentosUbicacionSimulada(),
                c.getUltimoIntentoUbicacionSimuladaEn());
    }

    static RegistroPedido registro(Pedido p) {
        return registro(p, null, null);
    }

    /**
     * Con rango (Métricas), cada marca cuenta en la fecha de su hecho, no en la de creación del pedido
     * (pruebas del 2026-09-28: un pedido de ayer finalizado hoy por el admin no aparecía en "hoy"):
     * retiro → retiradoEn; parada → su entregadoEn; entrega, admin e imprecisa → finalizadoEn (o el
     * retiro si todavía no terminó); GPS falso → la última de esas fechas (el pedido no guarda cuándo).
     */
    static RegistroPedido registro(Pedido p, Instant desde, Instant hasta) {
        Instant ultimoHecho = p.getFinalizadoEn() != null ? p.getFinalizadoEn()
                : p.getRetiradoEn() != null ? p.getRetiradoEn() : p.getCreadoEn();
        List<String> tipos = new ArrayList<>();
        if (Boolean.TRUE.equals(p.getRetiroFueraZona()) && enRango(p.getRetiradoEn(), desde, hasta)) tipos.add("RETIRO_FUERA_ZONA");
        for (PedidoParada parada : p.getParadas()) {
            if (Boolean.TRUE.equals(parada.getFueraZona()) && enRango(parada.getEntregadoEn(), desde, hasta)) {
                tipos.add("PARADA_FUERA_ZONA");
            }
        }
        if (Boolean.TRUE.equals(p.getEntregaFueraZona()) && enRango(p.getFinalizadoEn(), desde, hasta)) tipos.add("ENTREGA_FUERA_ZONA");
        if (Boolean.TRUE.equals(p.getUbicacionSimulada()) && enRango(ultimoHecho, desde, hasta)) tipos.add("UBICACION_SIMULADA");
        if (Boolean.TRUE.equals(p.getUbicacionImprecisa()) && enRango(ultimoHecho, desde, hasta)) tipos.add("UBICACION_IMPRECISA");
        if (p.getFinalizadoPorAdmin() != null && enRango(p.getFinalizadoEn(), desde, hasta)) tipos.add("FINALIZADO_POR_ADMIN");
        Cadete c = p.getCadeteAsignado();
        return new RegistroPedido(p.getId(), p.getNumero(), p.getCreadoEn(), c == null ? null : c.getId(),
                c == null ? null : nombre(c), tipos, p.getRetiroDistanciaM(), p.getEntregaDistanciaM(),
                p.getFinalizadoPorAdmin(), p.getFinalizadoAdminMotivo());
    }

    /** Sin rango (ficha: todo el historial) vale siempre; con rango, la fecha tiene que caer adentro. */
    private static boolean enRango(Instant cuando, Instant desde, Instant hasta) {
        if (desde == null || hasta == null) return true;
        return cuando != null && !cuando.isBefore(desde) && cuando.isBefore(hasta);
    }

    private static String nombre(Cadete c) {
        return ((c.getNombre() == null ? "" : c.getNombre()) + " " + (c.getApellido() == null ? "" : c.getApellido())).trim();
    }
}
