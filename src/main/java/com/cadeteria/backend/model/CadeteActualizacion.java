package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Un "lote" de cambios que el propio cadete propone desde la app (mejora 2026-09-23) — agrupa
 * uno o más {@link CadeteActualizacionCampo} mandados juntos. No tiene estado propio: el admin
 * aprueba/rechaza cada campo por separado (ver CadeteActualizacionCampo), así que el "estado"
 * del lote se deriva de sus campos al armar la respuesta (nunca se persiste acá).
 */
@Entity
@Table(name = "cadete_actualizacion")
public class CadeteActualizacion {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Cadete getCadete() {
        return cadete;
    }

    public void setCadete(Cadete cadete) {
        this.cadete = cadete;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }
}
