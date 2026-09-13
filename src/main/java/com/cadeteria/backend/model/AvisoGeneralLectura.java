package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Confirmación de un cadete de que vio un {@link AvisoGeneral} (uno por cadete y aviso). */
@Entity
@Table(name = "aviso_general_lectura")
public class AvisoGeneralLectura {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "aviso_id")
    private AvisoGeneral aviso;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

    @Column(nullable = false)
    private Instant leidoEn = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AvisoGeneral getAviso() {
        return aviso;
    }

    public void setAviso(AvisoGeneral aviso) {
        this.aviso = aviso;
    }

    public Cadete getCadete() {
        return cadete;
    }

    public void setCadete(Cadete cadete) {
        this.cadete = cadete;
    }

    public Instant getLeidoEn() {
        return leidoEn;
    }

    public void setLeidoEn(Instant leidoEn) {
        this.leidoEn = leidoEn;
    }
}
