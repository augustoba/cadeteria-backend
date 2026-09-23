package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Un campo propuesto dentro de un {@link CadeteActualizacion} — se aprueba/rechaza por
 * separado del resto (mejora 2026-09-23, pedido del dueño: "campo por campo", no todo el lote
 * junto). Esta tabla ES el historial: nunca se borra, queda como registro permanente de qué se
 * pidió, cuándo, y cómo se resolvió — no entra en RetencionDatosService (es auditoría de
 * identidad, no una foto de un pedido puntual).
 * <p>
 * {@code campo} es uno de: FOTO_PERFIL, FOTO_VEHICULO, FOTO_TARJETA_VERDE,
 * FOTO_TARJETA_VERDE_DORSO, VEHICULO_MARCA, VEHICULO_MODELO, VEHICULO_COLOR, VEHICULO_PATENTE,
 * VEHICULO_ANIO — valores fijos validados en {@code CadeteActualizacionService}, no una tabla
 * de parametría tipo {@link Lookup} (no son algo que el admin edite desde Configuración).
 */
@Entity
@Table(name = "cadete_actualizacion_campo")
public class CadeteActualizacionCampo {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "actualizacion_id")
    private CadeteActualizacion actualizacion;

    @Column(nullable = false)
    private String campo;

    @Column(length = 1000)
    private String valorAnterior;

    @Column(length = 1000, nullable = false)
    private String valorPropuesto;

    /** PENDIENTE / APROBADO / RECHAZADO */
    @Column(nullable = false)
    private String estado = "PENDIENTE";

    private String motivoRechazo;

    private Instant resueltoEn;

    private String resueltoPorUsername;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CadeteActualizacion getActualizacion() {
        return actualizacion;
    }

    public void setActualizacion(CadeteActualizacion actualizacion) {
        this.actualizacion = actualizacion;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(String valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public String getValorPropuesto() {
        return valorPropuesto;
    }

    public void setValorPropuesto(String valorPropuesto) {
        this.valorPropuesto = valorPropuesto;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public Instant getResueltoEn() {
        return resueltoEn;
    }

    public void setResueltoEn(Instant resueltoEn) {
        this.resueltoEn = resueltoEn;
    }

    public String getResueltoPorUsername() {
        return resueltoPorUsername;
    }

    public void setResueltoPorUsername(String resueltoPorUsername) {
        this.resueltoPorUsername = resueltoPorUsername;
    }
}
