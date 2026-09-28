package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * "Avisos de la calle" (carril C, 2026-09-28, pedido del dueño): un cadete avisa algo que vio en la
 * calle (control, calle cortada, accidente, piquete) y les llega a los cadetes cercanos y al Mapa del
 * panel. Vence solo; vencido queda como historial.
 */
@Entity
@Table(name = "aviso_calle", indexes = @Index(name = "idx_aviso_calle_vence", columnList = "venceEn"))
public class AvisoCalle {

    @Id
    @Column(length = 36)
    private String id;

    /** CONTROL | CALLE_CORTADA | ACCIDENTE | PIQUETE (ver {@link com.cadeteria.backend.dto.AvisoCalleDtos#TIPOS}). */
    @Column(nullable = false, length = 20)
    private String tipo;

    @Column(nullable = false)
    private Double lat;
    @Column(nullable = false)
    private Double lng;

    /** "Mate de Luna 2400" — null si el reverse no trajo calle (se muestra "cerca de tu ubicación"). */
    @Column(length = 200)
    private String calle;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

    @Column(nullable = false)
    private Instant creadoEn;

    @Column(nullable = false)
    private Instant venceEn;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public String getCalle() {
        return calle;
    }

    public void setCalle(String calle) {
        this.calle = calle;
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

    public Instant getVenceEn() {
        return venceEn;
    }

    public void setVenceEn(Instant venceEn) {
        this.venceEn = venceEn;
    }
}
