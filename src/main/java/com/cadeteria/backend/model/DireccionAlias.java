package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Variante de texto con la que un cliente escribió una calle ("av peron", "avenida perón",
 * "peron") apuntando a su nombre oficial (ver documentacion/spec-geocoding-cache.md §5.2-5.3).
 * Evita volver a geocodificar una forma de escritura ya vista una vez.
 * <p>
 * {@code varianteNorm} es el texto ya pasado por {@link com.cadeteria.backend.util.DireccionUtils#normalizar};
 * a propósito NO incluye la localidad en la clave: una forma de escribir una calle ("av rivadavia")
 * no depende de en qué pueblo esté esa calle, así que la misma variante sirve para cualquier
 * localidad. La localidad SÍ importa para las coordenadas en sí (dos pueblos pueden compartir
 * nombre de calle con ubicaciones distintas) — esa parte la resuelve
 * {@link com.cadeteria.backend.model.CuadraCoords}, que si tiene la calle+cuadra cacheada para
 * más de una localidad, no la resuelve en silencio (ver {@link com.cadeteria.backend.service.DireccionCacheService}).
 */
@Entity
@Table(name = "direccion_alias", uniqueConstraints = @UniqueConstraint(columnNames = "variante_norm"))
public class DireccionAlias {

    @Id
    private String id;

    @Column(name = "variante_norm", nullable = false)
    private String varianteNorm;

    /** Localidad devuelta por el geocoder la primera vez — informativa, no forma parte de la clave (ver comentario de clase). */
    @Column(nullable = false)
    private String localidad;

    @Column(name = "calle_canonica", nullable = false)
    private String calleCanonica;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getVarianteNorm() {
        return varianteNorm;
    }

    public void setVarianteNorm(String varianteNorm) {
        this.varianteNorm = varianteNorm;
    }

    public String getLocalidad() {
        return localidad;
    }

    public void setLocalidad(String localidad) {
        this.localidad = localidad;
    }

    public String getCalleCanonica() {
        return calleCanonica;
    }

    public void setCalleCanonica(String calleCanonica) {
        this.calleCanonica = calleCanonica;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }
}
