package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.PedidoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Simulador del factor de línea recta (Configuración → Tarifas, 2026-09-24): toma los pedidos
 * finalizados de los últimos días, calcula cuánto habría cobrado la fórmula con distancia =
 * línea recta × factor, y lo compara con lo que realmente se cobró. Sirve para elegir el factor
 * "según lo que se cobraba antes", en vez de adivinarlo.
 * <p>
 * El factor sugerido sale de despejar la fórmula al revés en cada pedido cobrado por encima del
 * mínimo: km implícitos = distancia_minima_km + (precio − recargo − mínimo) / precio_por_km, y
 * factor = km implícitos / línea recta. Se toma la mediana (no el promedio) para que un par de
 * pedidos cobrados a ojo no la muevan. Los cobrados al mínimo no dicen nada del factor y quedan
 * fuera de la sugerencia (pero sí entran en la comparación).
 */
@Service
public class TarifaSimulacionService {

    private static final double FACTOR_MIN = 1.0;
    private static final double FACTOR_MAX = 2.5;
    private static final int MAX_FILAS = 15;

    private final PedidoRepository pedidoRepo;
    private final CotizacionService cotizacionService;
    private final ConfiguracionService configuracionService;

    public TarifaSimulacionService(PedidoRepository pedidoRepo, CotizacionService cotizacionService,
                                   ConfiguracionService configuracionService) {
        this.pedidoRepo = pedidoRepo;
        this.cotizacionService = cotizacionService;
        this.configuracionService = configuracionService;
    }

    public record Fila(Long numero, Instant creadoEn, String origen, String destino, double lineaRectaKm,
                       BigDecimal cobrado, BigDecimal conFormula) {}

    public record Resultado(
            double factor, int dias,
            int pedidosAnalizados,
            /** La fórmula cobra más / menos / casi igual (±5%) que lo que se cobró. */
            int formulaMasCara, int formulaMasBarata, int parecidos,
            BigDecimal totalCobrado, BigDecimal totalConFormula,
            /** Promedio de (fórmula − cobrado) por pedido, en $ y en %. */
            BigDecimal diferenciaPromedio, double diferenciaPromedioPct,
            /** null si no hay pedidos cobrados por encima del mínimo. */
            Double factorSugerido, int pedidosParaSugerencia,
            /** Los que más se alejan, para ver casos concretos. */
            List<Fila> mayoresDiferencias
    ) {}

    @Transactional(readOnly = true)
    public Resultado simular(Double factorPedido, int diasPedido) {
        int dias = Math.max(1, Math.min(diasPedido, 365));
        double factor = factorPedido != null && factorPedido >= FACTOR_MIN
                ? factorPedido
                : configuracionService.getBigDecimal("factor_linea_recta", new BigDecimal("1.4")).doubleValue();

        BigDecimal precioPorKm = configuracionService.getBigDecimal("precio_por_km", BigDecimal.ZERO);
        BigDecimal precioBase = configuracionService.getBigDecimal("precio_base_viaje", BigDecimal.ZERO);
        double minimaKm = configuracionService.getBigDecimal("distancia_minima_km", BigDecimal.valueOf(2)).doubleValue();

        Instant hasta = Instant.now();
        List<Pedido> pedidos = pedidoRepo.findByCreadoEnBetween(hasta.minus(dias, ChronoUnit.DAYS), hasta).stream()
                .filter(p -> p.getEstado() != null && "FINALIZADO".equals(p.getEstado().getId()))
                .filter(p -> p.getPrecio() != null && p.getPrecio().signum() > 0)
                .filter(p -> p.getOrigenLat() != null && p.getDestinoLat() != null)
                .toList();

        int masCara = 0, masBarata = 0, parecidos = 0;
        BigDecimal totalCobrado = BigDecimal.ZERO, totalFormula = BigDecimal.ZERO;
        double sumaPct = 0;
        List<Fila> filas = new ArrayList<>();
        List<Double> factoresImplicitos = new ArrayList<>();

        for (Pedido p : pedidos) {
            double recta = GeocodingService.distanciaKm(p.getOrigenLat(), p.getOrigenLng(), p.getDestinoLat(), p.getDestinoLng());
            BigDecimal conFormula = cotizacionService.precioParaKm(recta * factor, p.getMontoDeclarado());
            BigDecimal cobrado = p.getPrecio();
            totalCobrado = totalCobrado.add(cobrado);
            totalFormula = totalFormula.add(conFormula);

            double pct = conFormula.subtract(cobrado).doubleValue() / cobrado.doubleValue() * 100;
            sumaPct += pct;
            if (Math.abs(pct) <= 5) parecidos++;
            else if (pct > 0) masCara++;
            else masBarata++;
            filas.add(new Fila(p.getNumero(), p.getCreadoEn(), p.getOrigenDireccion(), p.getDestinoDireccion(),
                    redondear(recta), cobrado, conFormula));

            // Despeje de la fórmula: solo informa si se cobró por encima del mínimo y hay distancia.
            BigDecimal sinRecargo = cobrado.subtract(cotizacionService.recargoPorDinero(p.getMontoDeclarado()));
            if (precioPorKm.signum() > 0 && recta > 0.3 && sinRecargo.compareTo(precioBase) > 0) {
                double kmImplicitos = minimaKm + sinRecargo.subtract(precioBase).doubleValue() / precioPorKm.doubleValue();
                double f = kmImplicitos / recta;
                if (f >= FACTOR_MIN && f <= FACTOR_MAX) factoresImplicitos.add(f);
            }
        }

        int n = pedidos.size();
        BigDecimal difPromedio = n == 0 ? BigDecimal.ZERO
                : totalFormula.subtract(totalCobrado).divide(BigDecimal.valueOf(n), 0, RoundingMode.HALF_UP);
        filas.sort(Comparator.comparingDouble((Fila f) -> Math.abs(f.conFormula().subtract(f.cobrado()).doubleValue())).reversed());

        return new Resultado(factor, dias, n, masCara, masBarata, parecidos, totalCobrado, totalFormula,
                difPromedio, n == 0 ? 0 : redondear(sumaPct / n),
                mediana(factoresImplicitos), factoresImplicitos.size(),
                filas.subList(0, Math.min(MAX_FILAS, filas.size())));
    }

    /** Mediana redondeada a 0,05 (un factor con más decimales no aporta). */
    static Double mediana(List<Double> valores) {
        if (valores.isEmpty()) return null;
        List<Double> orden = new ArrayList<>(valores);
        orden.sort(Double::compare);
        int m = orden.size() / 2;
        double med = orden.size() % 2 == 1 ? orden.get(m) : (orden.get(m - 1) + orden.get(m)) / 2;
        return Math.round(med * 20) / 20.0;
    }

    private static double redondear(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
