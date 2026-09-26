package com.cadeteria.backend.common;

import java.util.Locale;

/**
 * Formatos de los datos que carga la gente (2026-09-26). Se usan en las anotaciones {@code @Pattern}
 * de los DTOs y en los servicios; el front tiene los mismos en {@code core/utils/validaciones.ts} —
 * si cambia uno, cambiar el otro.
 */
public final class Validaciones {

    private Validaciones() {}

    /** Prefijo para campos opcionales: vacío o el formato ("^$|" + X). */
    public static final String VACIO_O = "^\\s*$|";

    /** Nombre o apellido de una persona: solo letras (con tilde/ñ), espacios, apóstrofo, guion o punto. */
    public static final String NOMBRE_PERSONA = "^\\s*\\p{L}+(?:[ '.\\-]+\\p{L}+)*\\.?\\s*$";
    public static final String MSJ_NOMBRE = "El nombre solo puede tener letras y espacios (sin números ni símbolos).";
    public static final String MSJ_APELLIDO = "El apellido solo puede tener letras y espacios (sin números ni símbolos).";
    public static final String MSJ_RECEPTOR = "El nombre de quien recibe solo puede tener letras y espacios.";

    /**
     * Nombre de un cliente: puede ser un comercio ("Kiosco 24hs", "Farmacia Nº 3"), así que acepta
     * letras, números y puntuación común — pero no puede ser solo números ni símbolos.
     */
    public static final String NOMBRE_CLIENTE = "^(?=.*\\p{L})[\\p{L}0-9 .,'&()/º°#\\-]{2,100}$";
    public static final String MSJ_NOMBRE_CLIENTE = "El nombre del cliente tiene que tener letras (2 a 100 caracteres, sin símbolos raros).";

    /** DNI: 7 u 8 números; se aceptan puntos o espacios como separadores (30.111.222). */
    public static final String DNI = "^\\s*\\d{1,2}[.\\s]?\\d{3}[.\\s]?\\d{3}\\s*$";
    public static final String MSJ_DNI = "El DNI tiene que tener solo números, 7 u 8 dígitos (ej: 30111222).";

    /** Teléfono: de 7 a 15 dígitos; se aceptan +, espacios, guiones y paréntesis. */
    public static final String TELEFONO = "^(?=(?:\\D*\\d){7,15}\\D*$)\\s*\\+?[0-9 ()\\-]+\\s*$";
    public static final String MSJ_TELEFONO = "El teléfono solo puede tener números (7 a 15 dígitos; se aceptan +, espacios y guiones).";

    public static final String EMAIL = "^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$";
    public static final String MSJ_EMAIL = "El email no es válido (ej: nombre@gmail.com).";

    /** Patente de moto argentina: vieja 123ABC o nueva (Mercosur) A123BCD, con o sin espacios/guiones. */
    public static final String PATENTE_MOTO = "^\\s*(?:\\d{3}[\\s\\-]?[A-Za-z]{3}|[A-Za-z][\\s\\-]?\\d{3}[\\s\\-]?[A-Za-z]{3})\\s*$";
    public static final String MSJ_PATENTE = "La patente tiene que ser del formato viejo (123ABC) o del nuevo (A123BCD).";

    /** Marca/modelo del vehículo: letras, números, espacios y guiones. */
    public static final String MARCA_MODELO = "^[\\p{L}0-9 .\\-]{1,40}$";
    public static final String MSJ_MARCA = "La marca solo puede tener letras, números y espacios (hasta 40).";
    public static final String MSJ_MODELO = "El modelo solo puede tener letras, números y espacios (hasta 40).";
    /** Color: solo letras y espacios. */
    public static final String COLOR = "^[\\p{L} ]{1,30}$";
    public static final String MSJ_COLOR = "El color solo puede tener letras (hasta 30).";

    public static final String CBU = "^\\d{22}$";
    public static final String MSJ_CBU = "El CBU/CVU tiene que tener exactamente 22 números.";
    /** Alias bancario: 6 a 20 caracteres, letras, números, punto o guion. */
    public static final String ALIAS_CBU = "^[A-Za-z0-9.\\-]{6,20}$";
    public static final String MSJ_ALIAS = "El alias tiene que tener de 6 a 20 caracteres: letras, números, punto o guion.";

    public static final int PASSWORD_MIN = 6;
    public static final int PASSWORD_MAX = 72; // límite de BCrypt
    public static final String MSJ_PASSWORD = "La contraseña tiene que tener entre 6 y 72 caracteres.";

    /** Usuario de un admin del panel (los cadetes usan el DNI). */
    public static final String USUARIO_ADMIN = "^[A-Za-z0-9._\\-]{3,30}$";
    public static final String MSJ_USUARIO_ADMIN = "El usuario tiene que tener de 3 a 30 caracteres: letras, números, punto, guion o guion bajo.";

    /** Código de 6 números que llega por WhatsApp/SMS/mail. */
    public static final String CODIGO_6 = "^\\s*\\d{6}\\s*$";
    public static final String MSJ_CODIGO = "El código tiene que ser de 6 números.";

    // --- Normalización (lo que se guarda) ---

    /** "30.111.222" → "30111222". */
    public static String soloDigitos(String s) {
        return s == null ? null : s.replaceAll("\\D", "");
    }

    /** "a 123 bcd" → "A123BCD"; null/vacío → null. */
    public static String normalizarPatente(String s) {
        if (s == null || s.isBlank()) return null;
        return s.replaceAll("[\\s\\-]", "").toUpperCase(Locale.ROOT);
    }

    /** Colapsa espacios repetidos y recorta: "  María   José " → "María José". */
    public static String normalizarNombre(String s) {
        if (s == null) return null;
        String t = s.trim().replaceAll("\\s+", " ");
        return t.isEmpty() ? null : t;
    }

    public static boolean esNombrePersona(String s) {
        return s != null && s.matches(NOMBRE_PERSONA);
    }

    public static boolean esPatenteMoto(String s) {
        return s != null && s.matches(PATENTE_MOTO);
    }
}
