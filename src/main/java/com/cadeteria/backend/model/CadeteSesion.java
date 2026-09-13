package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Ventana de tiempo en la que el cadete estuvo conectado (LIBRE u OCUPADO, no
 * DESCONECTADO) — se abre cuando pasa de DESCONECTADO a cualquier otro estado y se
 * cierra cuando vuelve a DESCONECTADO. Es la base de "horas online" en las métricas
 * del panel; no existía ningún registro histórico de esto antes.
 */
@Entity
@Table(name = "cadete_sesion")
public class CadeteSesion {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

    @Column(nullable = false)
    private Instant conectadoEn;

    /** null mientras sigue conectado. */
    private Instant desconectadoEn;

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

    public Instant getConectadoEn() {
        return conectadoEn;
    }

    public void setConectadoEn(Instant conectadoEn) {
        this.conectadoEn = conectadoEn;
    }

    public Instant getDesconectadoEn() {
        return desconectadoEn;
    }

    public void setDesconectadoEn(Instant desconectadoEn) {
        this.desconectadoEn = desconectadoEn;
    }
}
