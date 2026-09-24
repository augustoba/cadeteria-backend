package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Teléfono que ya pasó alguna vez la verificación de "/pedir" (spec-antiabuso §4 Fase 2):
 * la lista blanca que se construye sola. Va en tabla propia y no como flag en
 * {@link Cliente} porque la mayoría de los clientes reales no tiene ficha cargada.
 */
@Entity
@Table(name = "telefono_validado")
public class TelefonoValidado {

    /** Normalizado con TelefonoUtils. */
    @Id
    private String telefono;

    @Column(nullable = false)
    private Instant validadoEn = Instant.now();

    /** WHATSAPP | SMS | ADMIN */
    @Column(nullable = false, length = 20)
    private String via;

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public Instant getValidadoEn() {
        return validadoEn;
    }

    public void setValidadoEn(Instant validadoEn) {
        this.validadoEn = validadoEn;
    }

    public String getVia() {
        return via;
    }

    public void setVia(String via) {
        this.via = via;
    }
}
