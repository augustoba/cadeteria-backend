package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;

/**
 * Cada pago de la cuota semanal que registra el admin (2026-10-19). Antes solo se guardaba el monto
 * en el cadete y "Registrar otro pago" lo reemplazaba en vez de sumarlo, sin rastro de quién ni
 * cuándo. Lo pagado en la semana es la suma de los no anulados; uno equivocado se anula (solo los de
 * la semana actual) y queda tachado con el motivo.
 */
@Entity
@Table(name = "pago_semanal_movimiento",
        indexes = @Index(name = "idx_pago_semanal_mov_cadete_semana", columnList = "cadeteId,semanaInicio"))
public class PagoSemanalMovimiento {

    @Id
    private String id;

    @Column(nullable = false)
    private String cadeteId;

    /** Lunes de la semana a la que corresponde. */
    @Column(nullable = false)
    private LocalDate semanaInicio;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    /** Null en el que se arma con lo pagado antes de que existiera este historial. */
    private String creadoPorUsername;

    private Instant anuladoEn;
    private String anuladoPorUsername;
    private String motivoAnulacion;

    public boolean isAnulado() {
        return anuladoEn != null;
    }

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

    public LocalDate getSemanaInicio() {
        return semanaInicio;
    }

    public void setSemanaInicio(LocalDate semanaInicio) {
        this.semanaInicio = semanaInicio;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public String getCreadoPorUsername() {
        return creadoPorUsername;
    }

    public void setCreadoPorUsername(String creadoPorUsername) {
        this.creadoPorUsername = creadoPorUsername;
    }

    public Instant getAnuladoEn() {
        return anuladoEn;
    }

    public void setAnuladoEn(Instant anuladoEn) {
        this.anuladoEn = anuladoEn;
    }

    public String getAnuladoPorUsername() {
        return anuladoPorUsername;
    }

    public void setAnuladoPorUsername(String anuladoPorUsername) {
        this.anuladoPorUsername = anuladoPorUsername;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    public void setMotivoAnulacion(String motivoAnulacion) {
        this.motivoAnulacion = motivoAnulacion;
    }
}
