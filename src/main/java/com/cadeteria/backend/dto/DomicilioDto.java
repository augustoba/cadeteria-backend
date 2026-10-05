package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.Domicilio;
import jakarta.validation.constraints.Size;

/** Domicilio del cadete tal como viaja: calle, altura y localidad; piso y departamento solo si corresponde. */
public record DomicilioDto(
        @Size(max = 120, message = "La calle puede tener hasta 120 caracteres.") String calle,
        @Size(max = 15, message = "La altura puede tener hasta 15 caracteres.") String altura,
        @Size(max = 15, message = "El piso puede tener hasta 15 caracteres.") String piso,
        @Size(max = 15, message = "El departamento puede tener hasta 15 caracteres.") String depto,
        @Size(max = 80, message = "La localidad puede tener hasta 80 caracteres.") String localidad) {

    public static final String MSJ_INCOMPLETO = "Falta el domicilio: calle, altura y localidad.";

    public static DomicilioDto from(Domicilio d) {
        return d == null ? null : new DomicilioDto(d.getCalle(), d.getAltura(), d.getPiso(), d.getDepto(), d.getLocalidad());
    }

    /** Tiene lo obligatorio: calle, altura y localidad. */
    public boolean completo() {
        return !vacio(calle) && !vacio(altura) && !vacio(localidad);
    }

    /** No trae nada cargado (ni siquiera piso o departamento). */
    public boolean enBlanco() {
        return vacio(calle) && vacio(altura) && vacio(piso) && vacio(depto) && vacio(localidad);
    }

    public Domicilio aEntidad() {
        return new Domicilio(limpio(calle), limpio(altura), limpio(piso), limpio(depto), limpio(localidad));
    }

    private static boolean vacio(String s) {
        return s == null || s.isBlank();
    }

    private static String limpio(String s) {
        return vacio(s) ? null : s.trim().replaceAll("\\s+", " ");
    }
}
