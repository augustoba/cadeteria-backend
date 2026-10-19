package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Historial de Configuración (2026-10-19): quién cambió qué clave, de qué valor a cuál y cuándo.
 * Antes `configuracion` era solo clave/valor y no se podía saber desde cuándo regía, por ejemplo,
 * la comisión. Las claves con secretos (API keys, contraseñas) se guardan sin el valor.
 */
@Entity
@Table(name = "configuracion_cambio", indexes = @Index(name = "idx_configuracion_cambio_en", columnList = "cambiadoEn"))
public class ConfiguracionCambio {

    @Id
    private String id;

    @Column(nullable = false, length = 80)
    private String clave;

    /** Null si la clave no existía (regía el valor por defecto del código). */
    @Column(length = 500)
    private String valorAnterior;

    @Column(length = 500)
    private String valorNuevo;

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

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(String valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public String getValorNuevo() {
        return valorNuevo;
    }

    public void setValorNuevo(String valorNuevo) {
        this.valorNuevo = valorNuevo;
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
