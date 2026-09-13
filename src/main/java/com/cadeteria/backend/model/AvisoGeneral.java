package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Aviso operativo del admin a todos los cadetes conectados en el momento de enviarlo
 * (ej. "cerramos temprano") — antes era 100% transiente (WebSocket/FCM); ahora queda
 * registrado para poder mostrar en el panel cuántos lo confirmaron (spec: "no queda
 * registro de quién lo vio").
 */
@Entity
@Table(name = "aviso_general")
public class AvisoGeneral {

    @Id
    private String id;

    @Column(nullable = false, length = 1000)
    private String mensaje;

    @Column(nullable = false)
    private Instant enviadoEn = Instant.now();

    /** Cuántos cadetes estaban conectados (destinatarios) cuando se mandó — para calcular % de lectura. */
    @Column(nullable = false)
    private int totalDestinatarios;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getMensaje() {
        return mensaje;
    }

    public void setMensaje(String mensaje) {
        this.mensaje = mensaje;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public void setEnviadoEn(Instant enviadoEn) {
        this.enviadoEn = enviadoEn;
    }

    public int getTotalDestinatarios() {
        return totalDestinatarios;
    }

    public void setTotalDestinatarios(int totalDestinatarios) {
        this.totalDestinatarios = totalDestinatarios;
    }
}
