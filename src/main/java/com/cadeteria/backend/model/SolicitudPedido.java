package com.cadeteria.backend.model;

import com.cadeteria.backend.util.TelefonoUtils;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Pedido cargado por el cliente mismo desde una página pública (sin login, link fijo
 * "/pedir" que el admin comparte por WhatsApp) — antes esto solo se podía cargar hablando
 * por teléfono/WhatsApp con el admin y que él lo tipeara. El admin revisa, le pone
 * zona/vehículo/precio y confirma directo (ya tiene el precio acordado) o le manda una
 * cotización con un link que el cliente confirma solo, sin llamar.
 */
@Entity
@Table(name = "solicitud_pedido")
public class SolicitudPedido {

    @Id
    private String id;

    @Column(nullable = false)
    private String origenDireccion;
    @Column(nullable = false)
    private Double origenLat;
    @Column(nullable = false)
    private Double origenLng;

    @Column(nullable = false)
    private String destinoDireccion;
    @Column(nullable = false)
    private Double destinoLat;
    @Column(nullable = false)
    private Double destinoLng;

    @Column(nullable = false)
    private boolean llevaDinero;
    @Column(precision = 12, scale = 2)
    private BigDecimal montoDeclarado;

    /** Declarado por el cliente (mejora 2026-09-23) — ver Pedido.llevaValores. */
    @Column(nullable = false)
    private boolean llevaValores;

    @Column(nullable = false)
    private boolean retornaAlOrigen;

    @Column(nullable = false)
    private String clienteNombre;
    @Column(nullable = false)
    private String clienteTelefono;

    @Column(length = 1000)
    private String detalle;

    /** PENDIENTE (recién llegó) -> COTIZADO (admin mandó precio, espera que el cliente confirme) o
     * CONFIRMADA (ya existe el Pedido real) -> RECHAZADA. */
    @Column(nullable = false)
    private String estado = "PENDIENTE";

    /** Token de un solo uso para el link público "/confirmar-pedido/:token" (solo aplica en COTIZADO). */
    @Column(nullable = false, unique = true)
    private String tokenConfirmacion;

    @Column(nullable = false)
    private boolean requiereMoto = false;

    @Column(precision = 12, scale = 2)
    private BigDecimal precio;

    /** Una vez CONFIRMADA, el pedido real creado (para poder linkearlo desde el panel). */
    private String pedidoCreadoId;

    private String motivoRechazo;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getOrigenDireccion() {
        return origenDireccion;
    }

    public void setOrigenDireccion(String origenDireccion) {
        this.origenDireccion = origenDireccion;
    }

    public Double getOrigenLat() {
        return origenLat;
    }

    public void setOrigenLat(Double origenLat) {
        this.origenLat = origenLat;
    }

    public Double getOrigenLng() {
        return origenLng;
    }

    public void setOrigenLng(Double origenLng) {
        this.origenLng = origenLng;
    }

    public String getDestinoDireccion() {
        return destinoDireccion;
    }

    public void setDestinoDireccion(String destinoDireccion) {
        this.destinoDireccion = destinoDireccion;
    }

    public Double getDestinoLat() {
        return destinoLat;
    }

    public void setDestinoLat(Double destinoLat) {
        this.destinoLat = destinoLat;
    }

    public Double getDestinoLng() {
        return destinoLng;
    }

    public void setDestinoLng(Double destinoLng) {
        this.destinoLng = destinoLng;
    }

    public boolean isLlevaDinero() {
        return llevaDinero;
    }

    public void setLlevaDinero(boolean llevaDinero) {
        this.llevaDinero = llevaDinero;
    }

    public BigDecimal getMontoDeclarado() {
        return montoDeclarado;
    }

    public void setMontoDeclarado(BigDecimal montoDeclarado) {
        this.montoDeclarado = montoDeclarado;
    }

    public boolean isLlevaValores() {
        return llevaValores;
    }

    public void setLlevaValores(boolean llevaValores) {
        this.llevaValores = llevaValores;
    }

    public boolean isRetornaAlOrigen() {
        return retornaAlOrigen;
    }

    public void setRetornaAlOrigen(boolean retornaAlOrigen) {
        this.retornaAlOrigen = retornaAlOrigen;
    }

    public String getClienteNombre() {
        return clienteNombre;
    }

    public void setClienteNombre(String clienteNombre) {
        this.clienteNombre = clienteNombre;
    }

    public String getClienteTelefono() {
        return clienteTelefono;
    }

    public void setClienteTelefono(String clienteTelefono) {
        this.clienteTelefono = clienteTelefono;
    }

    @PrePersist
    @PreUpdate
    private void normalizarTelefono() {
        this.clienteTelefono = TelefonoUtils.normalizar(this.clienteTelefono);
    }

    public String getDetalle() {
        return detalle;
    }

    public void setDetalle(String detalle) {
        this.detalle = detalle;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getTokenConfirmacion() {
        return tokenConfirmacion;
    }

    public void setTokenConfirmacion(String tokenConfirmacion) {
        this.tokenConfirmacion = tokenConfirmacion;
    }

    public boolean isRequiereMoto() {
        return requiereMoto;
    }

    public void setRequiereMoto(boolean requiereMoto) {
        this.requiereMoto = requiereMoto;
    }

    public BigDecimal getPrecio() {
        return precio;
    }

    public void setPrecio(BigDecimal precio) {
        this.precio = precio;
    }

    public String getPedidoCreadoId() {
        return pedidoCreadoId;
    }

    public void setPedidoCreadoId(String pedidoCreadoId) {
        this.pedidoCreadoId = pedidoCreadoId;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }
}
