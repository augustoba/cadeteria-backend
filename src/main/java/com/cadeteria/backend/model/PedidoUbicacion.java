package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Un punto del trayecto real que hizo el cadete mientras el pedido estaba EN_CURSO
 * (spec Métricas: "guardar el trayecto GPS completo para km real y poder reconstruir
 * por dónde pasó ante un reclamo"). Se genera solo, un punto por cada actualización de
 * ubicación del cadete (CadeteService.actualizarUbicacion) mientras tenga un pedido
 * EN_CURSO asignado — no requiere ningún cambio en la app de cadetes, que ya manda su
 * ubicación cada `frecuencia_ubicacion_seg`.
 */
@Entity
@Table(name = "pedido_ubicacion")
public class PedidoUbicacion {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @Column(nullable = false)
    private Double lat;
    @Column(nullable = false)
    private Double lng;

    @Column(nullable = false)
    private Instant capturadoEn = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Pedido getPedido() {
        return pedido;
    }

    public void setPedido(Pedido pedido) {
        this.pedido = pedido;
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

    public Instant getCapturadoEn() {
        return capturadoEn;
    }

    public void setCapturadoEn(Instant capturadoEn) {
        this.capturadoEn = capturadoEn;
    }
}
