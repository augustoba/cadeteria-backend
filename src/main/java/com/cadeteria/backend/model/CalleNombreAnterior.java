package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * Nombre que una calle tuvo antes (2026-10-03): "Rivadavia" es hoy "Virgen de la Merced" en San
 * Miguel de Tucumán, "General Roca" es "Néstor Kirchner". No es un {@link DireccionAlias}: el
 * nombre viejo puede seguir siendo otra calle en otra localidad (Rivadavia en Yerba Buena), así
 * que no se reemplaza — el buscador ofrece la calle nueva ADEMÁS de lo que encuentre con el
 * nombre tipeado. Se carga desde el Excel de alias revisado (importador-direcciones/osm).
 */
@Entity
@Table(name = "calle_nombre_anterior",
        uniqueConstraints = @UniqueConstraint(columnNames = {"nombre_norm", "calle_canonica"}),
        indexes = @Index(name = "idx_calle_nombre_anterior_nombre", columnList = "nombre_norm"))
public class CalleNombreAnterior {

    @Id
    private String id;

    /** El nombre viejo, normalizado (DireccionUtils.normalizar). */
    @Column(name = "nombre_norm", nullable = false)
    private String nombreNorm;

    /** La calle como se llama hoy: la canónica de cuadra_coords. */
    @Column(name = "calle_canonica", nullable = false)
    private String calleCanonica;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombreNorm() {
        return nombreNorm;
    }

    public void setNombreNorm(String nombreNorm) {
        this.nombreNorm = nombreNorm;
    }

    public String getCalleCanonica() {
        return calleCanonica;
    }

    public void setCalleCanonica(String calleCanonica) {
        this.calleCanonica = calleCanonica;
    }
}
