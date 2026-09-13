package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Parametros globales editables desde el panel admin (diseno-tecnico.md sección 3/8):
 * tiempo limite de aceptacion, frecuencia de ubicacion, etc. Clave-valor simple para
 * poder sumar parametros nuevos sin migraciones.
 */
@Entity
@Table(name = "configuracion")
public class Configuracion {

    @Id
    @Column(length = 80)
    private String clave;

    @Column(nullable = false, length = 500)
    private String valor;

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }
}
