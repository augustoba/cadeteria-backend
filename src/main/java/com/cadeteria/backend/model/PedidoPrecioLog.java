package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/** Historial de cambios de precio de un pedido (mejora 75) — antes editar el precio no dejaba rastro. */
@Entity
@Table(name = "pedido_precio_log")
public class PedidoPrecioLog {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioAnterior;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal precioNuevo;

    @Column(nullable = false)
    private String cambiadoPorUsername;

    @Column(nullable = false)
    private Instant cambiadoEn = Instant.now();

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

    public BigDecimal getPrecioAnterior() {
        return precioAnterior;
    }

    public void setPrecioAnterior(BigDecimal precioAnterior) {
        this.precioAnterior = precioAnterior;
    }

    public BigDecimal getPrecioNuevo() {
        return precioNuevo;
    }

    public void setPrecioNuevo(BigDecimal precioNuevo) {
        this.precioNuevo = precioNuevo;
    }

    public String getCambiadoPorUsername() {
        return cambiadoPorUsername;
    }

    public void setCambiadoPorUsername(String cambiadoPorUsername) {
        this.cambiadoPorUsername = cambiadoPorUsername;
    }

    public Instant getCambiadoEn() {
        return cambiadoEn;
    }

    public void setCambiadoEn(Instant cambiadoEn) {
        this.cambiadoEn = cambiadoEn;
    }
}
