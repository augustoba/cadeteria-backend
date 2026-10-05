package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Dirección habitual que el operador quitó de las sugerencias de un cliente con la "x"
 * (2026-10-05). No hay entidad cliente: se guarda por teléfono (solo dígitos) + la clave de la
 * dirección (ver {@code DireccionesClienteService}). No borra pedidos ni toca la base de
 * direcciones: solo deja de ofrecerla, hasta que ese cliente vuelva a pedir ahí después de
 * {@link #ocultaEn} (ese pedido nuevo la trae de vuelta, ya con su punto).
 */
@Entity
@Table(name = "direccion_cliente_oculta",
        uniqueConstraints = @UniqueConstraint(columnNames = {"telefono", "clave"}))
public class DireccionClienteOculta {

    @Id
    private String id;

    /** Teléfono del cliente, solo dígitos. */
    @Column(nullable = false, length = 40)
    private String telefono;

    @Column(nullable = false)
    private String clave;

    @Column(name = "oculta_en", nullable = false)
    private Instant ocultaEn = Instant.now();

    /** Usuario del panel que la quitó. */
    @Column(name = "oculta_por")
    private String ocultaPor;

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

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public Instant getOcultaEn() {
        return ocultaEn;
    }

    public void setOcultaEn(Instant ocultaEn) {
        this.ocultaEn = ocultaEn;
    }

    public String getOcultaPor() {
        return ocultaPor;
    }

    public void setOcultaPor(String ocultaPor) {
        this.ocultaPor = ocultaPor;
    }
}
