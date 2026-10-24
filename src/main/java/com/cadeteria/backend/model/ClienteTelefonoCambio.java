package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Registro de cada cambio de teléfono de un cliente desde su ficha (2026-10-24): quién, de qué número a cuál. */
@Entity
@Table(name = "cliente_telefono_cambio", indexes = {
        @Index(name = "idx_cliente_tel_cambio_nuevo", columnList = "telefonoNuevo"),
        @Index(name = "idx_cliente_tel_cambio_anterior", columnList = "telefonoAnterior")})
public class ClienteTelefonoCambio {

    @Id
    private String id;

    @Column(nullable = false)
    private String telefonoAnterior;

    @Column(nullable = false)
    private String telefonoNuevo;

    /** Cuántos pedidos se mudaron al número nuevo. */
    private int pedidosMovidos;

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

    public String getTelefonoAnterior() {
        return telefonoAnterior;
    }

    public void setTelefonoAnterior(String telefonoAnterior) {
        this.telefonoAnterior = telefonoAnterior;
    }

    public String getTelefonoNuevo() {
        return telefonoNuevo;
    }

    public void setTelefonoNuevo(String telefonoNuevo) {
        this.telefonoNuevo = telefonoNuevo;
    }

    public int getPedidosMovidos() {
        return pedidosMovidos;
    }

    public void setPedidosMovidos(int pedidosMovidos) {
        this.pedidosMovidos = pedidosMovidos;
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
