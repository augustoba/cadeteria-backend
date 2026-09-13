package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.LookupResponse;
import com.cadeteria.backend.dto.MetricasDtos.CadeteMetricaResponse;
import com.cadeteria.backend.dto.MetricasDtos.PorHoraResponse;
import com.cadeteria.backend.dto.MetricasDtos.RechazoResponse;
import com.cadeteria.backend.dto.MetricasDtos.ResumenDiaResponse;
import com.cadeteria.backend.dto.MetricasDtos.ZonaMetricaResponse;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.OfertaPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoUbicacion;
import com.cadeteria.backend.model.Zona;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.OfertaPedidoRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import com.cadeteria.backend.repository.PedidoUbicacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Pantalla de Métricas del panel admin: cuántos pedidos hubo, cuántos se cancelaron
 * (y por qué), y por cadete — horas online, aceptados/rechazados/no-aceptados, plata
 * transportada y cobrada, km recorridos (aproximados) y promedios por hora. Nada de
 * esto existía antes; se arma sobre pedido, oferta_pedido y cadete_sesion.
 */
@Service
@Transactional(readOnly = true)
public class MetricasService {

    private static final ZoneId ZONA_ART = ZoneId.of("America/Argentina/Buenos_Aires");

    private final PedidoRepository pedidoRepo;
    private final OfertaPedidoRepository ofertaRepo;
    private final CadeteRepository cadeteRepo;
    private final CadeteSesionService sesionService;
    private final PedidoUbicacionRepository pedidoUbicacionRepo;

    public MetricasService(PedidoRepository pedidoRepo, OfertaPedidoRepository ofertaRepo,
                            CadeteRepository cadeteRepo, CadeteSesionService sesionService,
                            PedidoUbicacionRepository pedidoUbicacionRepo) {
        this.pedidoRepo = pedidoRepo;
        this.ofertaRepo = ofertaRepo;
        this.cadeteRepo = cadeteRepo;
        this.sesionService = sesionService;
        this.pedidoUbicacionRepo = pedidoUbicacionRepo;
    }

    public ResumenDiaResponse resumenDia(Instant desde, Instant hasta) {
        List<Pedido> pedidos = pedidoRepo.findByCreadoEnBetween(desde, hasta);

        long finalizados = 0, cancelados = 0, canceladosCliente = 0, sinAsignar = 0, pendientes = 0, enCurso = 0;
        for (Pedido p : pedidos) {
            switch (p.getEstado().getId()) {
                case "FINALIZADO" -> finalizados++;
                case "CANCELADO" -> {
                    cancelados++;
                    if ("CLIENTE".equals(p.getMotivoCancelacion())) canceladosCliente++;
                }
                case "SIN_ASIGNAR" -> sinAsignar++;
                case "PENDIENTE" -> pendientes++;
                case "EN_CURSO" -> enCurso++;
                default -> { /* PROGRAMADO: no cuenta en ningun bucket puntual */ }
            }
        }
        long canceladosOtro = cancelados - canceladosCliente;
        return new ResumenDiaResponse(pedidos.size(), finalizados, cancelados, canceladosCliente, canceladosOtro,
                sinAsignar, pendientes, enCurso);
    }

    public List<CadeteMetricaResponse> metricasCadetes(Instant desde, Instant hasta) {
        List<CadeteMetricaResponse> out = new ArrayList<>();
        for (Cadete c : cadeteRepo.findAll()) {
            List<Pedido> finalizados = pedidoRepo
                    .findByCadeteAsignadoIdAndEstadoIdAndFinalizadoEnBetweenOrderByFinalizadoEnDesc(c.getId(), "FINALIZADO", desde, hasta);

            long aceptados = ofertaRepo.countByCadeteIdAndResultadoIdAndOfrecidoEnBetween(c.getId(), "ACEPTADO", desde, hasta);
            long rechazados = ofertaRepo.countByCadeteIdAndResultadoIdAndOfrecidoEnBetween(c.getId(), "RECHAZADO", desde, hasta);
            long noAceptados = ofertaRepo.countByCadeteIdAndResultadoIdAndOfrecidoEnBetween(c.getId(), "EXPIRADO", desde, hasta);
            double horas = sesionService.horasOnline(c.getId(), desde, hasta);

            BigDecimal montoTransportado = finalizados.stream().map(Pedido::getMontoDeclarado)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            BigDecimal montoCobrado = finalizados.stream().map(Pedido::getPrecio)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            double km = finalizados.stream().mapToDouble(this::kmDelViaje).sum();
            double promedioViajesPorHora = horas > 0 ? finalizados.size() / horas : 0;
            double promedioPrecioPorHora = horas > 0 ? montoCobrado.doubleValue() / horas : 0;

            List<Integer> calificaciones = finalizados.stream()
                    .map(Pedido::getCalificacionEstrellas).filter(java.util.Objects::nonNull).toList();
            Double promedioCalificacion = calificaciones.isEmpty() ? null
                    : calificaciones.stream().mapToInt(Integer::intValue).average().orElse(0);

            out.add(new CadeteMetricaResponse(
                    c.getId(), c.getNombre(), c.getApellido(), LookupResponse.from(c.getTipoVehiculo()),
                    horas, aceptados, rechazados, noAceptados, finalizados.size(),
                    montoTransportado, montoCobrado, km, promedioViajesPorHora, promedioPrecioPorHora,
                    promedioCalificacion, calificaciones.size()));
        }
        return out;
    }

    /** Motivos de rechazo cargados por los cadetes en el rango — para detectar patrones (spec Métricas). */
    public List<RechazoResponse> motivosDeRechazo(Instant desde, Instant hasta) {
        List<OfertaPedido> rechazos = ofertaRepo.findByResultadoIdAndOfrecidoEnBetweenOrderByOfrecidoEnDesc("RECHAZADO", desde, hasta);
        return rechazos.stream()
                .map(o -> new RechazoResponse(
                        o.getPedido().getNumero(), o.getCadete().getNombre() + " " + o.getCadete().getApellido(),
                        o.getMotivoRechazo(), o.getOfrecidoEn()))
                .toList();
    }

    /** Pedidos creados por hora del día (0-23, hora local) — para el gráfico de Métricas (ronda 5, punto 31). */
    public List<PorHoraResponse> pedidosPorHora(Instant desde, Instant hasta) {
        long[] porHora = new long[24];
        for (Pedido p : pedidoRepo.findByCreadoEnBetween(desde, hasta)) {
            int hora = p.getCreadoEn().atZone(ZONA_ART).getHour();
            porHora[hora]++;
        }
        List<PorHoraResponse> out = new ArrayList<>(24);
        for (int h = 0; h < 24; h++) {
            out.add(new PorHoraResponse(h, porHora[h]));
        }
        return out;
    }

    /** Puntos de origen de pedidos en el rango, para el heatmap de demanda del mapa (mejora 92). */
    public List<double[]> puntosDeDemanda(Instant desde, Instant hasta) {
        List<double[]> puntos = new ArrayList<>();
        for (Pedido p : pedidoRepo.findByCreadoEnBetween(desde, hasta)) {
            if (p.getOrigenLat() != null && p.getOrigenLng() != null) {
                puntos.add(new double[]{p.getOrigenLat(), p.getOrigenLng()});
            }
        }
        return puntos;
    }

    /** Volumen e ingresos por zona en el rango (ronda 5, punto 34). */
    public List<ZonaMetricaResponse> metricasPorZona(Instant desde, Instant hasta) {
        Map<String, Zona> zonas = new LinkedHashMap<>();
        Map<String, Long> cantidad = new LinkedHashMap<>();
        Map<String, Long> finalizados = new LinkedHashMap<>();
        Map<String, BigDecimal> montoCobrado = new LinkedHashMap<>();

        for (Pedido p : pedidoRepo.findByCreadoEnBetween(desde, hasta)) {
            Zona zona = p.getZona();
            zonas.putIfAbsent(zona.getId(), zona);
            cantidad.merge(zona.getId(), 1L, Long::sum);
            if ("FINALIZADO".equals(p.getEstado().getId())) {
                finalizados.merge(zona.getId(), 1L, Long::sum);
                montoCobrado.merge(zona.getId(), p.getPrecio(), BigDecimal::add);
            }
        }

        List<ZonaMetricaResponse> out = new ArrayList<>();
        for (Zona zona : zonas.values()) {
            out.add(new ZonaMetricaResponse(
                    zona.getId(), zona.getNombre(),
                    cantidad.getOrDefault(zona.getId(), 0L),
                    finalizados.getOrDefault(zona.getId(), 0L),
                    montoCobrado.getOrDefault(zona.getId(), BigDecimal.ZERO)));
        }
        out.sort((a, b) -> Long.compare(b.cantidadPedidos(), a.cantidadPedidos()));
        return out;
    }

    /** Km reales sumando el trayecto GPS guardado (si hay al menos 2 puntos); si no, línea recta origen→destino. */
    private double kmDelViaje(Pedido p) {
        List<PedidoUbicacion> puntos = pedidoUbicacionRepo.findByPedidoIdOrderByCapturadoEnAsc(p.getId());
        if (puntos.size() < 2) {
            return distanciaKm(p.getOrigenLat(), p.getOrigenLng(), p.getDestinoLat(), p.getDestinoLng());
        }
        double total = 0;
        for (int i = 1; i < puntos.size(); i++) {
            PedidoUbicacion a = puntos.get(i - 1);
            PedidoUbicacion b = puntos.get(i);
            total += distanciaKm(a.getLat(), a.getLng(), b.getLat(), b.getLng());
        }
        return total;
    }

    /** Distancia en línea recta (haversine) — se usa como aproximación cuando el pedido no tiene trayecto GPS guardado. */
    private static double distanciaKm(double lat1, double lng1, double lat2, double lng2) {
        double r = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLng = Math.toRadians(lng2 - lng1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return r * c;
    }
}
