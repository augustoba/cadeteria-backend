package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Ficha de cliente por teléfono (ronda 4, puntos 44/58/67) — el teléfono es la clave
 * natural porque hoy `Pedido.clienteTelefono` es el único lugar donde vive el cliente;
 * esta tabla guarda solo lo que se carga a mano (empresa, tarifa especial, si es
 * problemático), el nombre e historial de pedidos se derivan de Pedido en tiempo real.
 */
@Entity
@Table(name = "cliente")
public class Cliente {

    @Id
    private String telefono;

    private String nombreContacto;
    private String empresa;

    /** Tarifa especial/preferencial para este cliente — sugiere el precio al cargar un pedido nuevo. */
    private BigDecimal tarifaEspecial;

    @Column(nullable = false)
    private boolean problematico = false;
    private String notasProblematico;

    @Column(nullable = false)
    private boolean activo = true;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    /** "CONTADO" (paga cada viaje, default) | "CUENTA_CORRIENTE" (factura a fin de mes) — ronda 10, punto 100. */
    @Column(nullable = false)
    private String modalidadFacturacion = "CONTADO";

    /** Hasta cuándo ya se le liquidó — null = nunca se liquidó, cuenta desde el primer pedido. */
    private Instant cuentaCorrienteLiquidadaHasta;

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getNombreContacto() {
        return nombreContacto;
    }

    public void setNombreContacto(String nombreContacto) {
        this.nombreContacto = nombreContacto;
    }

    public String getEmpresa() {
        return empresa;
    }

    public void setEmpresa(String empresa) {
        this.empresa = empresa;
    }

    public BigDecimal getTarifaEspecial() {
        return tarifaEspecial;
    }

    public void setTarifaEspecial(BigDecimal tarifaEspecial) {
        this.tarifaEspecial = tarifaEspecial;
    }

    public boolean isProblematico() {
        return problematico;
    }

    public void setProblematico(boolean problematico) {
        this.problematico = problematico;
    }

    public String getNotasProblematico() {
        return notasProblematico;
    }

    public void setNotasProblematico(String notasProblematico) {
        this.notasProblematico = notasProblematico;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public String getModalidadFacturacion() {
        return modalidadFacturacion;
    }

    public void setModalidadFacturacion(String modalidadFacturacion) {
        this.modalidadFacturacion = modalidadFacturacion;
    }

    public Instant getCuentaCorrienteLiquidadaHasta() {
        return cuentaCorrienteLiquidadaHasta;
    }

    public void setCuentaCorrienteLiquidadaHasta(Instant cuentaCorrienteLiquidadaHasta) {
        this.cuentaCorrienteLiquidadaHasta = cuentaCorrienteLiquidadaHasta;
    }
}
