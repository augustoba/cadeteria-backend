package com.cadeteria.backend.service;

import com.cadeteria.backend.service.ErroresRecientes.ErrorRegistrado;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Cómo está el servidor ahora (2026-10-05, pantalla "Sistema" del superadmin): memoria, procesador,
 * disco, cuánto ocupa cada tabla, pedidos por día, qué pedidos a la API son los más pesados y los
 * últimos errores. Todo sale de lo que el backend ya tiene a mano (el sistema operativo, MySQL y
 * los contadores de Spring): no agrega ningún servicio al servidor.
 * <p>
 * Los contadores de la API y los errores son <b>desde el último arranque</b>; lo demás es la foto
 * de este momento. Para tener historia hace falta guardar estas fotos cada tanto (pendiente).
 */
@Service
public class EstadoSistemaService {

    private static final int DIAS_ACTIVIDAD = 14;
    private static final int RUTAS_A_MOSTRAR = 12;

    public record Recursos(Instant arrancoEn, long memoriaJavaUsada, long memoriaJavaMaxima,
                           /** Del contenedor del backend (su límite), no de todo el servidor. */
                           long memoriaTotal, long memoriaLibre,
                           /** 0 a 1; -1 si el sistema no lo informa. */
                           double usoProcesador, double usoProcesadorBackend, int procesadores,
                           long discoTotal, long discoLibre) {}

    public record Tabla(String nombre, long filasAprox, long bytes) {}

    public record Base(long bytesTotales, List<Tabla> tablas) {}

    public record Dia(LocalDate dia, long cantidad) {}

    public record Ruta(String metodo, String ruta, long pedidos, double promedioMs, double maximoMs, long errores) {}

    public record Api(long pedidos, long errores, List<Ruta> masPesadas) {}

    public record Errores(long desdeElArranque, List<Dia> porDia, List<ErrorRegistrado> ultimos) {}

    public record EstadoSistema(Instant generadoEn, Recursos recursos, Base base, List<Dia> pedidosPorDia,
                                long cadetesActivos, Api api, Errores errores) {}

    private final JdbcTemplate jdbc;
    private final MeterRegistry metricas;
    private final ErroresRecientes erroresRecientes;

    public EstadoSistemaService(JdbcTemplate jdbc, MeterRegistry metricas, ErroresRecientes erroresRecientes) {
        this.jdbc = jdbc;
        this.metricas = metricas;
        this.erroresRecientes = erroresRecientes;
    }

    public EstadoSistema estado() {
        return new EstadoSistema(Instant.now(), recursos(), base(), pedidosPorDia(), cadetesActivos(), api(), errores());
    }

    private Recursos recursos() {
        Runtime java = Runtime.getRuntime();
        long total = -1, libre = -1;
        double uso = -1, usoBackend = -1;
        if (ManagementFactory.getOperatingSystemMXBean() instanceof com.sun.management.OperatingSystemMXBean so) {
            total = so.getTotalMemorySize();
            libre = so.getFreeMemorySize();
            uso = so.getCpuLoad();
            usoBackend = so.getProcessCpuLoad();
        }
        File raiz = new File("/");
        return new Recursos(Instant.ofEpochMilli(ManagementFactory.getRuntimeMXBean().getStartTime()),
                java.totalMemory() - java.freeMemory(), java.maxMemory(), total, libre, uso, usoBackend,
                java.availableProcessors(), raiz.getTotalSpace(), raiz.getUsableSpace());
    }

    /** Cuánto ocupa cada tabla (datos + índices). Las filas son la estimación de MySQL, no un conteo exacto. */
    private Base base() {
        List<Tabla> tablas = jdbc.query(
                "SELECT table_name, COALESCE(table_rows, 0), COALESCE(data_length, 0) + COALESCE(index_length, 0) "
                        + "FROM information_schema.tables WHERE table_schema = DATABASE() AND table_type = 'BASE TABLE' "
                        + "ORDER BY 3 DESC",
                (rs, i) -> new Tabla(rs.getString(1), rs.getLong(2), rs.getLong(3)));
        return new Base(tablas.stream().mapToLong(Tabla::bytes).sum(), tablas);
    }

    /** Pedidos creados por día (hora de Argentina; en la base las fechas están en UTC), últimos 14 días con alguno. */
    private List<Dia> pedidosPorDia() {
        return jdbc.query(
                "SELECT DATE(DATE_SUB(creado_en, INTERVAL 3 HOUR)) AS dia, COUNT(*) FROM pedido "
                        + "WHERE creado_en >= DATE_SUB(UTC_TIMESTAMP(), INTERVAL ? DAY) GROUP BY dia ORDER BY dia",
                (rs, i) -> new Dia(rs.getDate(1).toLocalDate(), rs.getLong(2)), DIAS_ACTIVIDAD);
    }

    private long cadetesActivos() {
        Long n = jdbc.queryForObject("SELECT COUNT(*) FROM cadete WHERE activo = 1", Long.class);
        return n == null ? 0 : n;
    }

    /** Pedidos a la API desde el arranque, agrupados por método y ruta; primero los que más tiempo sumaron. */
    private Api api() {
        Map<String, double[]> porRuta = new HashMap<>(); // pedidos, tiempo total ms, máximo ms, errores
        for (Timer t : metricas.find("http.server.requests").timers()) {
            String ruta = t.getId().getTag("uri");
            if (ruta == null || !ruta.startsWith("/api")) continue;
            String estado = t.getId().getTag("status");
            double[] a = porRuta.computeIfAbsent(t.getId().getTag("method") + " " + ruta, k -> new double[4]);
            a[0] += t.count();
            a[1] += t.totalTime(TimeUnit.MILLISECONDS);
            a[2] = Math.max(a[2], t.max(TimeUnit.MILLISECONDS));
            if (estado != null && estado.startsWith("5")) a[3] += t.count();
        }
        List<Ruta> rutas = new ArrayList<>();
        long pedidos = 0, errores = 0;
        for (Map.Entry<String, double[]> e : porRuta.entrySet()) {
            double[] a = e.getValue();
            String[] partes = e.getKey().split(" ", 2);
            rutas.add(new Ruta(partes[0], partes[1], (long) a[0], a[0] == 0 ? 0 : a[1] / a[0], a[2], (long) a[3]));
            pedidos += (long) a[0];
            errores += (long) a[3];
        }
        rutas.sort(Comparator.comparingDouble((Ruta r) -> -r.promedioMs() * r.pedidos()));
        return new Api(pedidos, errores, rutas.stream().limit(RUTAS_A_MOSTRAR).toList());
    }

    private Errores errores() {
        List<Dia> porDia = erroresRecientes.porDia().entrySet().stream().map(e -> new Dia(e.getKey(), e.getValue())).toList();
        return new Errores(erroresRecientes.total(), porDia, erroresRecientes.ultimos());
    }
}
