package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;
import java.time.LocalDate;

/** Registro manual de pago semanal por cadete (spec 5.4) — sin integracion con medios de pago. */
@Entity
@Table(name = "pago_semanal")
public class PagoSemanal {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

    /** Lunes de la semana correspondiente. */
    @Column(nullable = false)
    private LocalDate semanaInicio;

    @Column(nullable = false)
    private boolean pagado;

    @ManyToOne
    @JoinColumn(name = "registrado_por_id")
    private Admin registradoPor;

    @Column(nullable = false)
    private Instant registradoEn = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Cadete getCadete() {
        return cadete;
    }

    public void setCadete(Cadete cadete) {
        this.cadete = cadete;
    }

    public LocalDate getSemanaInicio() {
        return semanaInicio;
    }

    public void setSemanaInicio(LocalDate semanaInicio) {
        this.semanaInicio = semanaInicio;
    }

    public boolean isPagado() {
        return pagado;
    }

    public void setPagado(boolean pagado) {
        this.pagado = pagado;
    }

    public Admin getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(Admin registradoPor) {
        this.registradoPor = registradoPor;
    }

    public Instant getRegistradoEn() {
        return registradoEn;
    }

    public void setRegistradoEn(Instant registradoEn) {
        this.registradoEn = registradoEn;
    }
}
