package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Link de descarga de la APK de un solo uso (2026-09-29): va en el mail de alta del cadete, se genera
 * desde su ficha o cuando la app le avisa que su versión es vieja. Vence a las 24 h; desde la primera
 * descarga quedan 15 minutos para reintentar (cortes de señal) y después no anda más.
 */
@Entity
@Table(name = "apk_descarga")
public class ApkDescarga {

    @Id
    @Column(length = 64)
    private String token;

    /** Para quién se generó (null = sin cadete, ej. generado a mano). */
    @Column(length = 36)
    private String cadeteId;

    @Column(nullable = false)
    private Instant creadoEn;

    @Column(nullable = false)
    private Instant venceEn;

    private Instant primeraDescargaEn;

    private int descargas;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getCadeteId() {
        return cadeteId;
    }

    public void setCadeteId(String cadeteId) {
        this.cadeteId = cadeteId;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Instant getVenceEn() {
        return venceEn;
    }

    public void setVenceEn(Instant venceEn) {
        this.venceEn = venceEn;
    }

    public Instant getPrimeraDescargaEn() {
        return primeraDescargaEn;
    }

    public void setPrimeraDescargaEn(Instant primeraDescargaEn) {
        this.primeraDescargaEn = primeraDescargaEn;
    }

    public int getDescargas() {
        return descargas;
    }

    public void setDescargas(int descargas) {
        this.descargas = descargas;
    }
}
