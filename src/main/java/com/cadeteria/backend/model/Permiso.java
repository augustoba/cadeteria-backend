package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Catálogo fijo de permisos del panel (sistema de roles configurables, mejora pedida por
 * el dueño 2026-09-16) — igual patrón que {@link EstadoPedido}/{@link TipoVehiculo}: una
 * tabla lookup sembrada una sola vez por {@code RolSeeder}, no la crea el admin. Cada
 * permiso protege una sección/acción concreta del panel (ver SecurityConfig); un
 * {@link Rol} es simplemente un conjunto de estos.
 */
@Entity
@Table(name = "permiso")
public class Permiso {

    @Id
    private String id;

    @Column(nullable = false)
    private String nombre;

    /** Para agrupar visualmente en la pantalla de Roles (ej. "Panel", "Datos sensibles"). */
    private String categoria;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getCategoria() {
        return categoria;
    }

    public void setCategoria(String categoria) {
        this.categoria = categoria;
    }
}
