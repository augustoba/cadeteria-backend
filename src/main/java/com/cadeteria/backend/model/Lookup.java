package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;

/**
 * Base para las tablas de parametría (estado_pedido, tipo_vehiculo, etc.):
 * un código estable (id) + una etiqueta editable (nombre), en vez de enums de Java.
 * Así se puede sumar un valor nuevo insertando una fila, sin recompilar el backend.
 * OJO: si el código nuevo dispara lógica de negocio (ej. un estado que abre un flujo
 * distinto), además de la fila hace falta el código Java correspondiente — esto
 * resuelve la extensibilidad de datos/etiquetas, no la de la lógica de negocio.
 */
@MappedSuperclass
public abstract class Lookup {

    @Id
    @Column(length = 40)
    private String id;

    @Column(nullable = false, length = 100)
    private String nombre;

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
}
