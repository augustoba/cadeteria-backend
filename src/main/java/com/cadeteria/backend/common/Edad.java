package com.cadeteria.backend.common;

import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;

/**
 * Fecha de nacimiento del cadete (2026-10-05): hasta ahora la edad se cubría solo con la casilla
 * "Soy mayor de 18 años". La casilla sigue (es la declaración del cadete); con la fecha el sistema
 * además hace la cuenta y no deja dar de alta a un menor.
 */
public final class Edad {

    public static final int MINIMA = 18;
    public static final String MSJ_FALTA = "Falta la fecha de nacimiento.";
    public static final String MSJ_INVALIDA = "La fecha de nacimiento no es válida.";
    public static final String MSJ_MENOR = "Con esa fecha de nacimiento tiene menos de 18 años: no se puede dar de alta a un menor.";
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");
    private static final int MAXIMA = 100;

    private Edad() {
    }

    /** Años cumplidos hoy (hora de Argentina). */
    public static int de(LocalDate nacimiento) {
        return de(nacimiento, LocalDate.now(ARGENTINA));
    }

    static int de(LocalDate nacimiento, LocalDate hoy) {
        return Period.between(nacimiento, hoy).getYears();
    }

    /** Fecha cargada, creíble (no futura ni de hace más de 100 años) y de alguien con 18 años cumplidos. */
    public static void exigirMayor(LocalDate nacimiento) {
        exigirMayor(nacimiento, LocalDate.now(ARGENTINA));
    }

    static void exigirMayor(LocalDate nacimiento, LocalDate hoy) {
        if (nacimiento == null) throw new BadRequestException(MSJ_FALTA);
        if (nacimiento.isAfter(hoy) || de(nacimiento, hoy) > MAXIMA) throw new BadRequestException(MSJ_INVALIDA);
        if (de(nacimiento, hoy) < MINIMA) throw new BadRequestException(MSJ_MENOR);
    }
}
