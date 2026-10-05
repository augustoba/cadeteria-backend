package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

/**
 * Dónde vive el cadete (2026-10-05, pendientes 3p). Solo texto: no se ubica en el mapa ni pasa
 * por el buscador de direcciones. Piso y departamento, solo si corresponde.
 */
@Embeddable
public class Domicilio {

    @Column(name = "domicilio_calle", length = 120)
    private String calle;

    /** Texto y no número: "s/n", "1234 bis", "km 5". */
    @Column(name = "domicilio_altura", length = 15)
    private String altura;

    @Column(name = "domicilio_piso", length = 15)
    private String piso;

    @Column(name = "domicilio_depto", length = 15)
    private String depto;

    @Column(name = "domicilio_localidad", length = 80)
    private String localidad;

    public Domicilio() {
    }

    public Domicilio(String calle, String altura, String piso, String depto, String localidad) {
        this.calle = calle;
        this.altura = altura;
        this.piso = piso;
        this.depto = depto;
        this.localidad = localidad;
    }

    public String getCalle() {
        return calle;
    }

    public String getAltura() {
        return altura;
    }

    public String getPiso() {
        return piso;
    }

    public String getDepto() {
        return depto;
    }

    public String getLocalidad() {
        return localidad;
    }
}
