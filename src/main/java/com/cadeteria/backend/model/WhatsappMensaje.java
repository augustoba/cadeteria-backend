package com.cadeteria.backend.model;

import com.cadeteria.backend.util.TelefonoUtils;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
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

    /**
     * Hasta 4000 caracteres (2026-09-26). Antes era @Lob, que Hibernate crea en MySQL como TINYTEXT
     * (255): cualquier WhatsApp más largo fallaba al guardarse, y en cada arranque intentaba volver
     * la columna a TINYTEXT.
     */
    @Column(nullable = false, length = 4000)
    private String texto;

    /** "PENDIENTE" | "ENVIADO" | "FALLIDO". */
    @Column(nullable = false)
    private String estado = "PENDIENTE";

    /** Qué chip lo mandó, informado por el gateway en el ack (ej. "chip1"). */
    private String chipUsado;

    private String error;

    /** Id del mensaje en WhatsApp (lo devuelve Baileys al mandar) — permite traducir los
     * recibos de entrega/lectura que llegan después, identificados por este id. */
    private String waMessageId;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    private Instant enviadoEn;

    /** Cuándo el celular del cliente recibió el mensaje (doble tilde gris/azul de WhatsApp). */
    private Instant entregadoEn;

    /** Cuándo el cliente lo leyó (doble tilde azul). */
    private Instant leidoEn;

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

    @PrePersist
    @PreUpdate
    private void normalizarTelefono() {
        this.telefono = TelefonoUtils.normalizar(this.telefono);
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

    public String getWaMessageId() {
        return waMessageId;
    }

    public void setWaMessageId(String waMessageId) {
        this.waMessageId = waMessageId;
    }

    public Instant getEntregadoEn() {
        return entregadoEn;
    }

    public void setEntregadoEn(Instant entregadoEn) {
        this.entregadoEn = entregadoEn;
    }

    public Instant getLeidoEn() {
        return leidoEn;
    }

    public void setLeidoEn(Instant leidoEn) {
        this.leidoEn = leidoEn;
    }
}
