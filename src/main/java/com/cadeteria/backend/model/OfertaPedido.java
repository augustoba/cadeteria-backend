package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Registro de cada intento de asignacion de un pedido a un cadete. Es la pieza clave
 * del fix del bug de reasignacion (spec sección 4): al aceptar, el backend valida que
 * exista una fila PENDIENTE para ese pedido+cadete antes de confirmar.
 */
@Entity
@Table(name = "oferta_pedido")
public class OfertaPedido {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "pedido_id")
    private Pedido pedido;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

    @Column(nullable = false)
    private Instant ofrecidoEn = Instant.now();

    @Column(nullable = false)
    private Instant expiraEn;

    @ManyToOne(optional = false)
    @JoinColumn(name = "resultado_id")
    private ResultadoOferta resultado;

    /** Motivo opcional que carga el cadete al tocar "Rechazar" (spec Métricas: detectar patrones de rechazo). */
    @Column(length = 300)
    private String motivoRechazo;

    /** Cuándo el cadete aceptó/rechazó (null si expiró sin respuesta) — mejora 2026-09-17, tiempo de respuesta. */
    private Instant respondidoEn;

    public Instant getRespondidoEn() {
        return respondidoEn;
    }

    public void setRespondidoEn(Instant respondidoEn) {
        this.respondidoEn = respondidoEn;
    }

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

    public Cadete getCadete() {
        return cadete;
    }

    public void setCadete(Cadete cadete) {
        this.cadete = cadete;
    }

    public Instant getOfrecidoEn() {
        return ofrecidoEn;
    }

    public void setOfrecidoEn(Instant ofrecidoEn) {
        this.ofrecidoEn = ofrecidoEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(Instant expiraEn) {
        this.expiraEn = expiraEn;
    }

    public ResultadoOferta getResultado() {
        return resultado;
    }

    public void setResultado(ResultadoOferta resultado) {
        this.resultado = resultado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }
}
