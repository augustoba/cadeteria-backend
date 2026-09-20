package com.cadeteria.backend.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Pool de API keys por proveedor externo gratuito con límite diario (Geoapify, GraphHopper,
 * OpenRouteService) — permite cargar VARIAS keys por proveedor desde Configuración (una
 * cuenta gratuita nueva por cada key) y que {@link GeocodingProxyService}/{@link RutaService}
 * roten a la siguiente apenas una se queda sin cupo, en vez de esperar a que el dueño se dé
 * cuenta y cargue otra a mano.
 * <p>
 * Las keys se cargan como texto separado por coma o salto de línea en un único valor de
 * Configuración (ej. "geoapify_keys"). El estado de cada una vive solo en memoria — no hace
 * falta persistirlo porque los límites son diarios: una key marcada agotada se vuelve a probar
 * sola pasadas 24hs, y el conteo de usos de hoy se reinicia solo al cambiar el día.
 * <p>
 * "Cupo restante" para el panel: si el proveedor lo informa en la respuesta (hoy solo
 * OpenRouteService, vía header) se muestra ese dato real. Si no lo informa (Geoapify,
 * GraphHopper), se ESTIMA como {@code límite diario conocido del plan gratuito - consultas ya
 * hechas hoy con esa key} — no es exacto (no sabemos si esa key se usó fuera de este sistema,
 * ni si el dueño tiene un plan distinto al gratuito estándar), pero da una idea del margen.
 */
@Service
public class ApiKeyPoolService {

    private static final Duration DURACION_BLOQUEO = Duration.ofHours(24);

    /** Límite diario publicado del plan gratuito de cada proveedor, para estimar "restante" cuando la API no lo informa. */
    private static final Map<String, Integer> LIMITE_DIARIO_ESTIMADO = Map.of(
            "geoapify", 3000,
            "graphhopper", 500,
            "openrouteservice", 2500
    );

    public enum Estado { OK, AGOTADA }

    /** Snapshot de una key para mostrar en el panel de Configuración — nunca expone la key completa. */
    public record EstadoClave(String proveedor, String claveEnmascarada, Estado estado, Integer restante,
                               boolean restanteEstimado, Instant actualizadoEn) {}

    private static final class ClaveEstado {
        final String valor;
        volatile Estado estado = Estado.OK;
        volatile Integer restanteInformado;
        volatile int usadasHoy;
        volatile LocalDate diaUsadas = LocalDate.now();
        volatile Instant agotadaEn;
        /** true si se marcó agotada por una predicción propia (estimación de uso, o cupo real informado en 0) y
         *  no por un 429 real del proveedor — en ese caso no hace falta esperar las 24hs completas: apenas
         *  cambia el día local (y el conteo de uso se reinicia) ya se puede volver a ofrecer sin riesgo. */
        volatile boolean agotadaPorPrediccion;
        volatile Instant actualizadoEn = Instant.now();
        /** Para no repetir el aviso de "cupo bajo" en cada uso mientras siga bajo — se reinicia solo al cambiar el día. */
        volatile boolean avisoBajoEnviadoHoy;

        ClaveEstado(String valor) {
            this.valor = valor;
        }

        synchronized void rotarDiaSiCorresponde() {
            LocalDate hoy = LocalDate.now();
            if (!hoy.equals(diaUsadas)) {
                diaUsadas = hoy;
                usadasHoy = 0;
                restanteInformado = null;
                avisoBajoEnviadoHoy = false;
                if (agotadaPorPrediccion) {
                    estado = Estado.OK;
                    agotadaEn = null;
                    agotadaPorPrediccion = false;
                }
            }
        }

        /** @return true si esta key recién pasó a AGOTADA en esta llamada (transición real, no una ya conocida). */
        synchronized boolean registrarUso(Integer limiteDiarioEstimado) {
            rotarDiaSiCorresponde();
            usadasHoy++;
            if (limiteDiarioEstimado != null && usadasHoy >= limiteDiarioEstimado) {
                return marcarAgotadaPorPrediccion();
            }
            return false;
        }

        synchronized boolean marcarAgotadaPorPrediccion() {
            if (estado == Estado.OK) {
                estado = Estado.AGOTADA;
                agotadaEn = Instant.now();
                agotadaPorPrediccion = true;
                return true;
            }
            return false;
        }
    }

    private final ConfiguracionService configuracionService;
    private final WebSocketPublisher publisher;
    private final Map<String, List<ClaveEstado>> cache = new ConcurrentHashMap<>();
    private final Map<String, String> ultimaConfigCruda = new ConcurrentHashMap<>();

    public ApiKeyPoolService(ConfiguracionService configuracionService, WebSocketPublisher publisher) {
        this.configuracionService = configuracionService;
        this.publisher = publisher;
    }

    /** Como {@link #siguienteClave(String, String, String)} pero sin valor legado por defecto. */
    public String siguienteClave(String proveedor, String configKey) {
        return siguienteClave(proveedor, configKey, "");
    }

    /**
     * Próxima key utilizable de este proveedor (la primera que no esté agotada, o cuyo
     * bloqueo de 24hs ya venció) — null si no hay ninguna cargada o todas siguen agotadas.
     * @param configKey clave de Configuración donde están cargadas (ej. "geoapify_keys"), y
     *                   por compatibilidad también se acepta ahí una única key sin separadores.
     * @param valorLegado si Configuración está vacía, se usa este valor único (ej. el de
     *                    application.yml/env var de antes de soportar varias keys por proveedor).
     */
    public String siguienteClave(String proveedor, String configKey, String valorLegado) {
        for (ClaveEstado c : clavesDe(proveedor, configKey, valorLegado)) {
            c.rotarDiaSiCorresponde();
            if (c.estado == Estado.OK) return c.valor;
            if (c.agotadaEn != null && Duration.between(c.agotadaEn, Instant.now()).compareTo(DURACION_BLOQUEO) > 0) {
                c.estado = Estado.OK;
                c.agotadaEn = null;
                c.actualizadoEn = Instant.now();
                return c.valor;
            }
        }
        return null;
    }

    /** Marcar como sin cupo por un 429/403 REAL del proveedor — a diferencia de una predicción propia, espera las 24hs completas antes de reintentar, porque no sabemos a qué hora exacta reinicia el cupo. */
    public void marcarAgotada(String proveedor, String configKey, String valor) {
        buscar(proveedor, configKey, valor).ifPresent(c -> {
            boolean eraOk = c.estado == Estado.OK;
            c.estado = Estado.AGOTADA;
            c.agotadaEn = Instant.now();
            c.agotadaPorPrediccion = false;
            c.actualizadoEn = Instant.now();
            if (eraOk) avisarSiPoolAgotado(proveedor);
        });
    }

    /**
     * Contabiliza una consulta más hecha hoy con esta key — llamarlo en cada intento real
     * contra el proveedor (haya salido bien o mal). Sirve para dos cosas: estimar el cupo
     * restante cuando el proveedor no lo informa, y — lo que pediste — pasar a la siguiente
     * cuenta apenas el conteo propio llega al límite diario conocido del plan gratuito, sin
     * esperar a que el proveedor devuelva error. Se resetea solo al cambiar el día.
     */
    public void registrarUso(String proveedor, String configKey, String valor) {
        Integer limite = LIMITE_DIARIO_ESTIMADO.get(proveedor);
        buscar(proveedor, configKey, valor).ifPresent(c -> {
            boolean recienAgotada = c.registrarUso(limite);
            if (recienAgotada) {
                avisarSiPoolAgotado(proveedor);
            } else if (limite != null) {
                avisarSiCupoBajo(proveedor, c, Math.max(0, limite - c.usadasHoy), limite);
            }
        });
    }

    /**
     * Cupo restante informado por el proveedor en la respuesta (ej. header de OpenRouteService)
     * — pisa la estimación propia. Si ya llegó a 0, también pasa a la siguiente cuenta de una,
     * sin esperar a que la próxima consulta le devuelva 429.
     */
    public void actualizarRestanteInformado(String proveedor, String configKey, String valor, Integer restante) {
        buscar(proveedor, configKey, valor).ifPresent(c -> {
            c.restanteInformado = restante;
            c.actualizadoEn = Instant.now();
            if (restante == null) return;
            if (restante <= 0) {
                if (c.marcarAgotadaPorPrediccion()) avisarSiPoolAgotado(proveedor);
            } else {
                Integer limite = LIMITE_DIARIO_ESTIMADO.get(proveedor);
                avisarSiCupoBajo(proveedor, c, restante, limite);
            }
        });
    }

    /** Todas las cuentas cargadas de este proveedor se quedaron sin cupo a la vez — recién ahí vale la alerta. */
    private void avisarSiPoolAgotado(String proveedor) {
        List<ClaveEstado> claves = cache.getOrDefault(proveedor, List.of());
        if (!claves.isEmpty() && claves.stream().allMatch(c -> c.estado == Estado.AGOTADA)) {
            publisher.publicarAlertaApiKeyPoolAgotado(proveedor);
        }
    }

    /** Aviso preventivo (una vez por día por key) cuando el cupo restante cae al 10% del límite diario conocido. */
    private void avisarSiCupoBajo(String proveedor, ClaveEstado c, int restante, Integer limiteDiario) {
        if (limiteDiario == null || c.estado != Estado.OK || c.avisoBajoEnviadoHoy) return;
        int umbral = Math.max(1, limiteDiario / 10);
        if (restante <= umbral) {
            c.avisoBajoEnviadoHoy = true;
            publisher.publicarAlertaApiKeyPoolBajo(proveedor, restante);
        }
    }

    /** Para el semáforo del panel de Configuración: estado de cada key cargada de este proveedor. */
    public List<EstadoClave> estadoDe(String proveedor, String configKey) {
        Integer limiteDiario = LIMITE_DIARIO_ESTIMADO.get(proveedor);
        return clavesDe(proveedor, configKey, "").stream()
                .map(c -> {
                    c.rotarDiaSiCorresponde();
                    if (c.restanteInformado != null) {
                        return new EstadoClave(proveedor, enmascarar(c.valor), c.estado, c.restanteInformado, false, c.actualizadoEn);
                    }
                    Integer estimado = limiteDiario == null ? null : Math.max(0, limiteDiario - c.usadasHoy);
                    return new EstadoClave(proveedor, enmascarar(c.valor), c.estado, estimado, true, c.actualizadoEn);
                })
                .toList();
    }

    private Optional<ClaveEstado> buscar(String proveedor, String configKey, String valor) {
        return clavesDe(proveedor, configKey, "").stream().filter(c -> c.valor.equals(valor)).findFirst();
    }

    /** Relee Configuración solo si el texto crudo cambió desde la última vez, preservando el estado de las keys que ya conocíamos. */
    private List<ClaveEstado> clavesDe(String proveedor, String configKey, String valorLegado) {
        String cruda = configuracionService.getString(configKey, valorLegado);
        if (cruda.equals(ultimaConfigCruda.getOrDefault(proveedor, "\u0000"))) {
            return cache.getOrDefault(proveedor, List.of());
        }
        List<String> nuevasClaves = Arrays.stream(cruda.split("[,\\n]"))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .distinct()
                .toList();
        Map<String, ClaveEstado> anterioresPorValor = cache.getOrDefault(proveedor, List.of()).stream()
                .collect(Collectors.toMap(c -> c.valor, c -> c));
        List<ClaveEstado> actualizadas = nuevasClaves.stream()
                .map(v -> anterioresPorValor.getOrDefault(v, new ClaveEstado(v)))
                .collect(Collectors.toList());
        cache.put(proveedor, actualizadas);
        ultimaConfigCruda.put(proveedor, cruda);
        return actualizadas;
    }

    private String enmascarar(String clave) {
        if (clave.length() <= 6) return "••••";
        return clave.substring(0, 3) + "…" + clave.substring(clave.length() - 3);
    }
}
