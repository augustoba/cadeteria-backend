package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Historial de movimientos del crédito de un cadete PORCENTAJE (ronda 10, punto 94) —
 * antes `Cadete.creditoDisponible` solo se sumaba/restaba sin dejar ningún rastro de
 * cuándo se acreditó o qué comisión se descontó en cada viaje.
 */
@Entity
@Table(name = "movimiento_credito")
public class MovimientoCredito {

    @Id
    private String id;

    @Column(nullable = false)
    private String cadeteId;

    /** "ACREDITACION" (el admin le carga plata) | "COMISION" (se descuenta al aceptar un viaje). */
    @Column(nullable = false)
    private String tipo;

    /** Positivo en ambos casos — el signo lo da `tipo`, no el monto. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal monto;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal saldoResultante;

    /** Solo para tipo=COMISION. */
    private String pedidoId;
    private Long pedidoNumero;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    /** Solo para tipo=ACREDITACION (quién la cargó). */
    private String creadoPorUsername;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCadeteId() {
        return cadeteId;
    }

    public void setCadeteId(String cadeteId) {
        this.cadeteId = cadeteId;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public BigDecimal getMonto() {
        return monto;
    }

    public void setMonto(BigDecimal monto) {
        this.monto = monto;
    }

    public BigDecimal getSaldoResultante() {
        return saldoResultante;
    }

    public void setSaldoResultante(BigDecimal saldoResultante) {
        this.saldoResultante = saldoResultante;
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

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public String getCreadoPorUsername() {
        return creadoPorUsername;
    }

    public void setCreadoPorUsername(String creadoPorUsername) {
        this.creadoPorUsername = creadoPorUsername;
    }
}
