package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Cola persistida de mensajes de WhatsApp para el gateway propio (Baileys + chips
 * descartables, ver memoria del proyecto "whatsapp-gateway"). Se guarda ANTES de
 * publicar el comando por STOMP porque el gateway corre en una PC local que puede estar
 * apagada o sin internet en el momento exacto en que se genera el pedido — al reconectar,
 * se reenvían todos los que quedaron en PENDIENTE (mismo criterio que
 * PendingActionsStore del lado de la app de cadetes para su modo offline).
 */
@Entity
@Table(name = "whatsapp_mensaje")
public class WhatsappMensaje {

    @Id
    private String id;

    /** Null si es un envío de prueba manual, sin pedido asociado. */
    private String pedidoId;

    @Column(nullable = false)
    private String telefono;

    @Lob
    @Column(nullable = false)
    private String texto;

    /** "PENDIENTE" | "ENVIADO" | "FALLIDO". */
    @Column(nullable = false)
    private String estado = "PENDIENTE";

    /** Qué chip lo mandó, informado por el gateway en el ack (ej. "chip1"). */
    private String chipUsado;

    private String error;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    private Instant enviadoEn;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(String pedidoId) {
        this.pedidoId = pedidoId;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getChipUsado() {
        return chipUsado;
    }

    public void setChipUsado(String chipUsado) {
        this.chipUsado = chipUsado;
    }

    public String getError() {
        return error;
    }

    public void setError(String error) {
        this.error = error;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Instant getEnviadoEn() {
        return enviadoEn;
    }

    public void setEnviadoEn(Instant enviadoEn) {
        this.enviadoEn = enviadoEn;
    }
}
