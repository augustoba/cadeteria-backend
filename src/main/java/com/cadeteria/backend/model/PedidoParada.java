package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Parada adicional de un pedido con varias entregas en la misma vuelta (ronda 3, punto
 * 38) — el origen y el destino "principal" del Pedido siguen siendo la primera recogida
 * y la última entrega; estas son las paradas intermedias, en el orden en que se cargaron.
 */
@Entity
@Table(name = "pedido_parada")
public class PedidoParada {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @Column(nullable = false)
    private int orden;

    @Column(nullable = false, length = 500)
    private String direccion;

    @Column(nullable = false)
    private Double lat;
    @Column(nullable = false)
    private Double lng;

    /** null hasta que el cadete la marca como entregada. */
    private Instant entregadoEn;

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

    public int getOrden() {
        return orden;
    }

    public void setOrden(int orden) {
        this.orden = orden;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
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

    public Instant getEntregadoEn() {
        return entregadoEn;
    }

    public void setEntregadoEn(Instant entregadoEn) {
        this.entregadoEn = entregadoEn;
    }
}
