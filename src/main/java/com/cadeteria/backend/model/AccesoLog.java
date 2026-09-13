package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de accesos al panel de admin (ronda 5, punto 50) — distinto de la auditoría
 * de pedido (Pedido.asignadoPorUsername/canceladoPorUsername): esto es a nivel sesión
 * de admin, no de una acción puntual sobre un pedido.
 */
@Entity
@Table(name = "acceso_log")
public class AccesoLog {

    @Id
    private String id;

    @Column(nullable = false)
    private String username;

    @Column(nullable = false)
    private Instant ingresoEn = Instant.now();

    private String ip;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public Instant getIngresoEn() {
        return ingresoEn;
    }

    public void setIngresoEn(Instant ingresoEn) {
        this.ingresoEn = ingresoEn;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }
}
