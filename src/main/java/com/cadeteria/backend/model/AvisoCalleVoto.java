package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * "¿Sigue ahí?" de un aviso de la calle (segunda etapa, 2026-09-29): otro cadete que pasa cerca
 * contesta SIGUE (lo extiende) o YA_NO_ESTA (con dos de cadetes distintos se baja). Un voto por cadete
 * y aviso; si cambia de opinión se actualiza.
 */
@Entity
@Table(name = "aviso_calle_voto",
        uniqueConstraints = @UniqueConstraint(name = "uk_aviso_calle_voto", columnNames = {"aviso_id", "cadete_id"}))
public class AvisoCalleVoto {

    public static final String SIGUE = "SIGUE";
    public static final String YA_NO_ESTA = "YA_NO_ESTA";

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "aviso_id")
    private AvisoCalle aviso;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

    @Column(nullable = false, length = 12)
    private String voto;

    @Column(nullable = false)
    private Instant creadoEn;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public AvisoCalle getAviso() {
        return aviso;
    }

    public void setAviso(AvisoCalle aviso) {
        this.aviso = aviso;
    }

    public Cadete getCadete() {
        return cadete;
    }

    public void setCadete(Cadete cadete) {
        this.cadete = cadete;
    }

    public String getVoto() {
        return voto;
    }

    public void setVoto(String voto) {
        this.voto = voto;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }
}
