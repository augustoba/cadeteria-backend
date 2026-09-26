package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Un mensaje entrante de un cliente, capturado por el gateway (Baileys) y reportado al
 * backend para que el panel pueda mostrar "el cliente contestó y qué contestó" (ver
 * memoria del proyecto "whatsapp-gateway"). No se asocia a un pedido puntual acá —
 * WhatsApp no manda esa referencia — el panel lo relaciona a ojo por el teléfono.
 */
@Entity
@Table(name = "whatsapp_respuesta")
public class WhatsappRespuesta {

    @Id
    private String id;

    @Column(nullable = false)
    private String telefono;

    private String chipId;

    /** Hasta 4000 caracteres (2026-09-26): con @Lob quedaba TINYTEXT (255), igual que WhatsappMensaje.texto. */
    @Column(nullable = false, length = 4000)
    private String texto;

    @Column(nullable = false)
    private Instant recibidoEn = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getChipId() {
        return chipId;
    }

    public void setChipId(String chipId) {
        this.chipId = chipId;
    }

    public String getTexto() {
        return texto;
    }

    public void setTexto(String texto) {
        this.texto = texto;
    }

    public Instant getRecibidoEn() {
        return recibidoEn;
    }

    public void setRecibidoEn(Instant recibidoEn) {
        this.recibidoEn = recibidoEn;
    }
}
