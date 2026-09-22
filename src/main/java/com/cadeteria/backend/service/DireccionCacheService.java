package com.cadeteria.backend.service;

import com.cadeteria.backend.model.CuadraCoords;
import com.cadeteria.backend.model.DireccionAlias;
import com.cadeteria.backend.repository.CuadraCoordsRepository;
import com.cadeteria.backend.repository.DireccionAliasRepository;
import com.cadeteria.backend.util.DireccionUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Cache de "texto de calle + altura -> coordenadas", compartida por todo lo que geocodifica
 * (ver documentacion/spec-geocoding-cache.md §5-6). Guarda por CUADRA (no por número exacto) y
 * por calle CANÓNICA (no por como la tipeó el cliente), así variantes como "av perón 1500" y
 * "perón 1502" pegan en la misma fila. {@link GeocodingProxyService} es quien la consulta antes
 * de llamar a un proveedor y quien la alimenta después de un geocode real.
 * <p>
 * Todavía sin Google: hoy la cache se llena solo con lo que ya devuelven Nominatim/Geoapify
 * (decisión 2026-09-21, para no cargar tarjeta todavía). El día que se sume Google detrás de la
 * misma interfaz, esta clase no cambia — solo cambia quién le pasa el resultado a {@link #guardar}.
 */
@Service
public class DireccionCacheService {

    public record ResultadoCache(String calleCanonica, String localidad, int cuadra, double lat, double lng, boolean approximate) {}

    private final DireccionAliasRepository aliasRepository;
    private final CuadraCoordsRepository coordsRepository;

    public DireccionCacheService(DireccionAliasRepository aliasRepository, CuadraCoordsRepository coordsRepository) {
        this.aliasRepository = aliasRepository;
        this.coordsRepository = coordsRepository;
    }

    /**
     * Null = miss (hay que geocodificar con algún proveedor). No llama a nadie.
     * <p>
     * El texto que tipea el cliente no trae la localidad ("Rivadavia 500" no dice si es San
     * Miguel de Tucumán o Lules), así que si la misma calle+cuadra quedó cacheada para MÁS DE UNA
     * localidad, no hay forma de saber cuál corresponde sin adivinar — se trata como miss para
     * que el geocoder en vivo vuelva a mostrar las opciones (como en una búsqueda sin cache) en
     * vez de devolver en silencio la localidad equivocada.
     */
    @Transactional
    public ResultadoCache buscar(String calleTexto, int numero) {
        String norm = DireccionUtils.normalizar(calleTexto);
        return aliasRepository.findByVarianteNorm(norm)
                .map(alias -> coordsRepository.findByCalleCanonicaAndCuadra(alias.getCalleCanonica(), DireccionUtils.cuadra(numero)))
                .filter(candidatos -> candidatos.size() == 1)
                .map(candidatos -> confirmarYMapear(candidatos.get(0)))
                .orElse(null);
    }

    private ResultadoCache confirmarYMapear(CuadraCoords c) {
        c.setConfirmaciones(c.getConfirmaciones() + 1);
        coordsRepository.save(c);
        return new ResultadoCache(c.getCalleCanonica(), c.getLocalidad(), c.getCuadra(), c.getLat(), c.getLng(), c.isApproximate());
    }

    /**
     * Guarda el resultado de un geocode real para no volver a pagarlo. {@code calleCanonica} debe
     * ser la que devolvió el proveedor (Nominatim: {@code road}; Geoapify: {@code street}), nunca
     * una adivinada localmente (spec §5.4, "alias solo de resultados reales").
     */
    @Transactional
    public void guardar(String calleTextoOriginal, int numero, String calleCanonica, String localidad,
                         double lat, double lng, boolean approximate, String proveedor) {
        if (calleCanonica == null || calleCanonica.isBlank()) return;
        String canonicaNorm = DireccionUtils.normalizar(calleCanonica);

        String varianteNorm = DireccionUtils.normalizar(calleTextoOriginal);
        if (aliasRepository.findByVarianteNorm(varianteNorm).isEmpty()) {
            DireccionAlias alias = new DireccionAlias();
            alias.setId(UUID.randomUUID().toString());
            alias.setVarianteNorm(varianteNorm);
            alias.setLocalidad(localidad == null ? "" : localidad);
            alias.setCalleCanonica(canonicaNorm);
            aliasRepository.save(alias);
        }

        int cuadra = DireccionUtils.cuadra(numero);
        String localidadNorm = localidad == null ? "" : localidad;
        coordsRepository.findByCalleCanonicaAndLocalidadAndCuadra(canonicaNorm, localidadNorm, cuadra).ifPresentOrElse(
                existente -> {
                    existente.setConfirmaciones(existente.getConfirmaciones() + 1);
                    coordsRepository.save(existente);
                },
                () -> {
                    CuadraCoords c = new CuadraCoords();
                    c.setId(UUID.randomUUID().toString());
                    c.setCalleCanonica(canonicaNorm);
                    c.setLocalidad(localidadNorm);
                    c.setCuadra(cuadra);
                    c.setLat(lat);
                    c.setLng(lng);
                    c.setApproximate(approximate);
                    c.setProveedor(proveedor == null ? "" : proveedor);
                    c.setConfirmaciones(1);
                    coordsRepository.save(c);
                }
        );
    }
}
