package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Un chip SIM descartable del gateway propio de WhatsApp (ver memoria del proyecto
 * "whatsapp-gateway"). El id es el chipId que usa el gateway (ej. "chip1"), no un UUID —
 * así el panel y el gateway hablan del mismo chip sin tener que sincronizar nada más.
 */
@Entity
@Table(name = "whatsapp_chip")
public class WhatsappChip {

    @Id
    private String id;

    private String numero;

    /** "VINCULANDO" | "CONECTADO" | "DESCONECTADO" | "BANEADO". */
    @Column(nullable = false)
    private String estado = "VINCULANDO";

    /** Código de pairing de 8 dígitos mientras se está vinculando; se limpia al conectar. */
    private String pairingCodigo;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    private Instant ultimoCambioEstado;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNumero() {
        return numero;
    }

    public void setNumero(String numero) {
        this.numero = numero;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getPairingCodigo() {
        return pairingCodigo;
    }

    public void setPairingCodigo(String pairingCodigo) {
        this.pairingCodigo = pairingCodigo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Instant getUltimoCambioEstado() {
        return ultimoCambioEstado;
    }

    public void setUltimoCambioEstado(Instant ultimoCambioEstado) {
        this.ultimoCambioEstado = ultimoCambioEstado;
    }
}
