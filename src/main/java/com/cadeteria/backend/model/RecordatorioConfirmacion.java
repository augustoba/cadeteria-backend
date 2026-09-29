package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * El cadete tocó "Entendido" en el cartel de recordatorios al entrar a la app (2026-09-29). Guarda
 * el título y los renglones tal como estaban en ese momento: si después se cambian, queda qué se le
 * mostró ("nunca me dijeron lo del casco").
 */
@Entity
@Table(name = "recordatorio_confirmacion", indexes = @Index(name = "idx_recordatorio_conf_cadete", columnList = "cadete_id"))
public class RecordatorioConfirmacion {

    @Id
    @Column(length = 36)
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

    @Column(nullable = false)
    private Instant confirmadoEn;

    @Column(nullable = false, length = 60)
    private String titulo;

    /** Los renglones separados por salto de línea (hasta 6 de 150). */
    @Column(nullable = false, length = 1000)
    private String textos;

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

    public Instant getConfirmadoEn() {
        return confirmadoEn;
    }

    public void setConfirmadoEn(Instant confirmadoEn) {
        this.confirmadoEn = confirmadoEn;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getTextos() {
        return textos;
    }

    public void setTextos(String textos) {
        this.textos = textos;
    }
}
