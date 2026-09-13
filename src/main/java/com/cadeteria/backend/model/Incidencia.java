package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Incidencia/ticket general del admin (ronda 4, punto 63) — para reclamos o notas
 * operativas que no están atadas a un pedido puntual (a diferencia de los comentarios
 * de PedidoComentario).
 */
@Entity
@Table(name = "incidencia")
public class Incidencia {

    @Id
    private String id;

    @Column(nullable = false)
    private String titulo;

    @Lob
    private String descripcion;

    /** "ABIERTA" | "CERRADA". */
    @Column(nullable = false)
    private String estado = "ABIERTA";

    /** "BAJA" | "NORMAL" | "GRAVE" (ronda 10, punto 97) — antes todas se veían igual en la lista. */
    @Column(nullable = false)
    private String prioridad = "NORMAL";

    /** Si la incidencia es sobre un cadete puntual (ronda 10, punto 108) — null si es general o solo de un pedido. */
    private String cadeteId;
    private String cadeteNombre;

    @Column(nullable = false)
    private Instant creadaEn = Instant.now();
    private String creadaPorUsername;

    /** Si se creó desde el detalle de un pedido puntual (ronda 7) — null si es una incidencia general. */
    private String pedidoId;
    private Long pedidoNumero;

    private Instant cerradaEn;
    private String cerradaPorUsername;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getPrioridad() {
        return prioridad;
    }

    public void setPrioridad(String prioridad) {
        this.prioridad = prioridad;
    }

    public String getCadeteId() {
        return cadeteId;
    }

    public void setCadeteId(String cadeteId) {
        this.cadeteId = cadeteId;
    }

    public String getCadeteNombre() {
        return cadeteNombre;
    }

    public void setCadeteNombre(String cadeteNombre) {
        this.cadeteNombre = cadeteNombre;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(Instant creadaEn) {
        this.creadaEn = creadaEn;
    }

    public String getCreadaPorUsername() {
        return creadaPorUsername;
    }

    public void setCreadaPorUsername(String creadaPorUsername) {
        this.creadaPorUsername = creadaPorUsername;
    }

    public String getPedidoId() {
        return pedidoId;
    }

    public void setPedidoId(String pedidoId) {
        this.pedidoId = pedidoId;
    }

    public Long getPedidoNumero() {
        return pedidoNumero;
    }

    public void setPedidoNumero(Long pedidoNumero) {
        this.pedidoNumero = pedidoNumero;
    }

    public Instant getCerradaEn() {
        return cerradaEn;
    }

    public void setCerradaEn(Instant cerradaEn) {
        this.cerradaEn = cerradaEn;
    }

    public String getCerradaPorUsername() {
        return cerradaPorUsername;
    }

    public void setCerradaPorUsername(String cerradaPorUsername) {
        this.cerradaPorUsername = cerradaPorUsername;
    }
}
