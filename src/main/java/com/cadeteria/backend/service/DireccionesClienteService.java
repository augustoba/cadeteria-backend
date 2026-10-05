package com.cadeteria.backend.service;

import com.cadeteria.backend.dto.PedidoDtos.DireccionFrecuenteResponse;
import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.model.DireccionClienteOculta;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.repository.DireccionClienteOcultaRepository;
import com.cadeteria.backend.repository.PedidoRepository;
import com.cadeteria.backend.util.DireccionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Direcciones habituales de un cliente (2026-09-25): las de sus últimos pedidos, las más usadas
 * primero (hasta 5). La mayoría de los clientes pide siempre desde o hacia los mismos lugares:
 * así se cargan con un clic, sin buscar ni gastar geocoding.
 * <p>
 * 2026-10-05 (pendientes 3p): la lista salía tal cual de los pedidos viejos, agrupada por el
 * texto. Si una dirección se había cargado con el punto mal y después se corrigió (link de
 * Google Maps, pin a mano, GPS del cadete), seguía ofreciendo la vieja con el punto malo — y
 * primera, porque tenía más usos. Ahora:
 * <ul>
 *   <li>cada dirección se cruza con la base propia: si esa calle y altura tiene un punto
 *       <b>validado</b> y el del pedido quedó a más de {@link #DESVIO_MAX_M}, se ofrece el
 *       validado;</li>
 *   <li>las que son la misma calle y altura (aunque el texto difiera: con o sin localidad, "Av.")
 *       y caen en el mismo lugar se juntan en una, sumando los usos;</li>
 *   <li>el operador puede quitar una con la "x" ({@link #ocultar}).</li>
 * </ul>
 */
@Service
public class DireccionesClienteService {

    private static final int MAXIMO = 5;
    /**
     * La base guarda un punto por cuadra, no por puerta: dentro de la misma cuadra el punto del
     * pedido (que puede ser un pin exacto) vale más. Más lejos que esto ya es otra cuadra: el
     * del pedido está mal.
     */
    static final double DESVIO_MAX_M = 150;
    /** Dos usos de la misma calle y altura a más de esto son lugares distintos (otra localidad): no se juntan. */
    private static final double MISMO_LUGAR_M = 300;
    /** Lo que una persona o un cadete confirmó, y lo de Google mientras no venza (la base ya no devuelve lo vencido). */
    private static final Set<String> VALIDADOS = Set.of(DireccionCacheService.PROVEEDOR_CADETE_GPS,
            DireccionCacheService.PROVEEDOR_MANUAL, DireccionCacheService.PROVEEDOR_GOOGLE_LINK,
            GeocodingProxyService.PROVEEDOR_GOOGLE);
    private static final Pattern CALLE_Y_NUMERO = Pattern.compile("^(.+?)[\\s,]*(\\d{1,6})\\s*$");

    private final PedidoRepository pedidoRepository;
    private final DireccionCacheService direccionCache;
    private final DireccionClienteOcultaRepository ocultaRepository;

    public DireccionesClienteService(PedidoRepository pedidoRepository, DireccionCacheService direccionCache,
                                     DireccionClienteOcultaRepository ocultaRepository) {
        this.pedidoRepository = pedidoRepository;
        this.direccionCache = direccionCache;
        this.ocultaRepository = ocultaRepository;
    }

    @Transactional(readOnly = true)
    public List<DireccionFrecuenteResponse> frecuentes(String telefono) {
        String digitos = soloDigitos(telefono);
        if (digitos.length() < 6) return List.of();
        Map<String, Instant> ocultas = new HashMap<>();
        for (DireccionClienteOculta o : ocultaRepository.findByTelefono(digitos)) ocultas.put(o.getClave(), o.getOcultaEn());

        Map<String, Lugar> lugares = new HashMap<>();
        List<Acumulada> grupos = new ArrayList<>();
        // del más nuevo al más viejo: la primera vez que aparece una dirección es la última que se usó
        for (Pedido p : pedidoRepository.ultimosPorTelefonoNormalizado(digitos)) {
            sumar(grupos, lugares, ocultas, p.getOrigenDireccion(), p.getOrigenLat(), p.getOrigenLng(), p.getOrigenPiso(),
                    p.getOrigenDepto(), p.getOrigenObservaciones(), p.getCreadoEn(), true);
            sumar(grupos, lugares, ocultas, p.getDestinoDireccion(), p.getDestinoLat(), p.getDestinoLng(), p.getDestinoPiso(),
                    p.getDestinoDepto(), p.getDestinoObservaciones(), p.getCreadoEn(), false);
        }
        return grupos.stream()
                .sorted(Comparator.comparingInt((Acumulada a) -> -(a.vecesOrigen + a.vecesDestino))
                        .thenComparing(a -> a.ultimaVez, Comparator.reverseOrder()))
                .limit(MAXIMO)
                .map(a -> new DireccionFrecuenteResponse(a.direccion, a.lat, a.lng, a.piso, a.depto, a.observaciones,
                        a.vecesOrigen, a.vecesDestino, a.ultimaVez, a.clave))
                .toList();
    }

    /** La "x" del panel: deja de ofrecerle esa dirección a ese cliente. Repetirla solo actualiza la fecha. */
    @Transactional
    public void ocultar(String telefono, String clave, String usuario) {
        String digitos = soloDigitos(telefono);
        if (digitos.length() < 6 || clave == null || clave.isBlank()) return;
        DireccionClienteOculta o = ocultaRepository.findByTelefonoAndClave(digitos, clave.trim()).orElseGet(() -> {
            DireccionClienteOculta n = new DireccionClienteOculta();
            n.setId(UUID.randomUUID().toString());
            n.setTelefono(digitos);
            n.setClave(clave.trim());
            return n;
        });
        o.setOcultaEn(Instant.now());
        o.setOcultaPor(usuario);
        ocultaRepository.save(o);
    }

    private void sumar(List<Acumulada> grupos, Map<String, Lugar> lugares, Map<String, Instant> ocultas, String direccion,
                       Double lat, Double lng, String piso, String depto, String obs, Instant cuando, boolean esOrigen) {
        if (direccion == null || direccion.isBlank() || lat == null || lng == null) return;
        Lugar lugar = lugares.computeIfAbsent(DireccionUtils.normalizar(direccion), k -> lugarDe(direccion));
        Instant oculta = ocultas.get(lugar.clave);
        if (oculta != null && !cuando.isAfter(oculta)) return;
        double[] punto = lugar.corregir(lat, lng);
        Acumulada a = grupos.stream()
                .filter(g -> g.clave.equals(lugar.clave) && metros(g.lat, g.lng, punto[0], punto[1]) <= MISMO_LUGAR_M)
                .findFirst().orElse(null);
        if (a == null) {
            a = new Acumulada();
            a.clave = lugar.clave;
            a.direccion = direccion.trim();
            a.lat = punto[0];
            a.lng = punto[1];
            a.piso = piso;
            a.depto = depto;
            a.observaciones = obs;
            a.ultimaVez = cuando;
            grupos.add(a);
        }
        if (esOrigen) a.vecesOrigen++;
        else a.vecesDestino++;
    }

    /** Calle y altura del texto ("Corrientes 480, San Miguel de Tucumán") y lo que la base propia sabe de ese lugar. */
    private Lugar lugarDe(String direccion) {
        Matcher m = CALLE_Y_NUMERO.matcher(direccion.split(",")[0].trim());
        if (!m.matches()) return new Lugar(DireccionUtils.normalizar(direccion), null);
        String calle = m.group(1).trim();
        int numero = Integer.parseInt(m.group(2));
        CuadraCoords fila = direccionCache.filaDe(calle, numero);
        boolean validada = fila != null && VALIDADOS.contains(fila.getProveedor());
        return new Lugar(direccionCache.canonicalizar(calle) + " " + numero, validada ? fila : null);
    }

    private static double metros(double lat1, double lng1, double lat2, double lng2) {
        return GeocodingService.distanciaKm(lat1, lng1, lat2, lng2) * 1000;
    }

    private static String soloDigitos(String telefono) {
        return telefono == null ? "" : telefono.replaceAll("[^0-9]", "");
    }

    /** Clave con la que se agrupa y se oculta, y el punto validado de la base propia (null si no hay). */
    private record Lugar(String clave, CuadraCoords validado) {
        double[] corregir(double lat, double lng) {
            if (validado == null || metros(lat, lng, validado.getLat(), validado.getLng()) <= DESVIO_MAX_M) {
                return new double[]{lat, lng};
            }
            return new double[]{validado.getLat(), validado.getLng()};
        }
    }

    private static final class Acumulada {
        String clave, direccion, piso, depto, observaciones;
        double lat, lng;
        Instant ultimaVez;
        int vecesOrigen, vecesDestino;
    }
}
