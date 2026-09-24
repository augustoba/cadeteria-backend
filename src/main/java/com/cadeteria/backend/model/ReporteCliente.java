package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Reporte del cadete sobre un cliente, desde la pantalla del viaje (spec-antiabuso §4
 * Fase 3). Es un evento inmutable que se acumula por teléfono — no tiene ciclo de vida
 * como {@link Incidencia}, por eso va en tabla propia. No bloquea ni marca nada solo: el
 * admin lo ve en el aviso del cliente y decide. Sin FK duras, mismo criterio que
 * Incidencia (ids + nombre desnormalizado).
 */
@Entity
@Table(name = "reporte_cliente", indexes = @Index(name = "idx_reporte_cliente_telefono", columnList = "telefono"))
public class ReporteCliente {

    @Id
    private String id;

    /** Normalizado con TelefonoUtils. */
    @Column(nullable = false)
    private String telefono;

    /** DEMORO | NO_DECLARO_VALORES | PEDIDO_FALSO | OTRO */
    @Column(nullable = false, length = 30)
    private String tipo;

    private String pedidoId;
    private Long pedidoNumero;
    private String cadeteId;
    private String cadeteNombre;

    @Column(length = 500)
    private String nota;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

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

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
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

    public String getNota() {
        return nota;
    }

    public void setNota(String nota) {
        this.nota = nota;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }
}
