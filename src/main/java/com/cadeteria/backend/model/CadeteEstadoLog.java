package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Historial de altas/bajas de un cadete (ronda 10, punto 96) — antes solo había un
 * `confirm()` sin dejar rastro de cuándo ni por qué se dio de baja o se reactivó.
 */
@Entity
@Table(name = "cadete_estado_log")
public class CadeteEstadoLog {

    @Id
    private String id;

    @Column(nullable = false)
    private String cadeteId;

    @Column(nullable = false)
    private boolean activo;

    private String motivo;

    @Column(nullable = false)
    private Instant cambiadoEn = Instant.now();

    private String cambiadoPorUsername;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCadeteId() {
        return cadeteId;
    }

    public void setCadeteId(String cadeteId) {
        this.cadeteId = cadeteId;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public String getMotivo() {
        return motivo;
    }

    public void setMotivo(String motivo) {
        this.motivo = motivo;
    }

    public Instant getCambiadoEn() {
        return cambiadoEn;
    }

    public void setCambiadoEn(Instant cambiadoEn) {
        this.cambiadoEn = cambiadoEn;
    }

    public String getCambiadoPorUsername() {
        return cambiadoPorUsername;
    }

    public void setCambiadoPorUsername(String cambiadoPorUsername) {
        this.cambiadoPorUsername = cambiadoPorUsername;
    }
}
