package com.cadeteria.backend.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Parecido entre lo que se tipeó y el nombre de una calle conocida (2026-10-03), para que la base
 * propia tolere los errores que hasta acá solo perdonaban los buscadores de afuera:
 * <ul>
 *   <li>de dedo: una letra cambiada, de más, de menos, o dos invertidas ("belgarno");</li>
 *   <li>de oído: b/v, s/c/z, y/i/ll, h muda, letra doble ("bolibar", "balcarse", "irigoyen");</li>
 *   <li>palabras de menos o en otro orden ("roca julio", "mate luna").</li>
 * </ul>
 * No decide nada: da un puntaje (0 = igual salvo la ortografía) y quien busca elige qué hacer con
 * uno o con varios candidatos.
 */
public final class CallesParecidas {

    private CallesParecidas() {
    }

    /** Puntaje de "no se parecen". */
    public static final double NO = -1;

    /** Palabras que no distinguen una calle de otra: no cuentan para comparar. */
    private static final Set<String> GENERICAS = Set.of(
            "av", "avda", "avenida", "calle", "gral", "general", "dr", "doctor", "pte", "pres", "presidente",
            "cnel", "coronel", "tte", "teniente", "ing", "ingeniero", "sgto", "sargento",
            "de", "del", "la", "las", "los", "el", "y", "e");

    /**
     * Clave "de oído" de un nombre ya normalizado: sin palabras genéricas y con las letras que
     * suenan igual llevadas a una sola. Vacía si el nombre era solo palabras genéricas.
     */
    public static String clave(String nombreNorm) {
        List<String> palabras = palabras(nombreNorm);
        return String.join(" ", palabras);
    }

    private static List<String> palabras(String nombreNorm) {
        List<String> out = new ArrayList<>();
        if (nombreNorm == null) return out;
        for (String p : nombreNorm.trim().split("\\s+")) {
            if (p.isEmpty() || GENERICAS.contains(p)) continue;
            out.add(deOido(p));
        }
        return out;
    }

    /** "yrigoyen" e "irigoyen", "bolivar" y "bolibar", "balcarce" y "balcarse" dan lo mismo. */
    static String deOido(String palabra) {
        String s = palabra
                .replace("ch", "#")       // se guarda: la h de "ch" no es muda
                .replace("h", "")
                .replace("ll", "i")
                .replace("y", "i")
                .replace("v", "b")
                .replace("qu", "k")
                .replaceAll("c(?=[ei])", "s")
                .replaceAll("g(?=[ei])", "j")
                .replace("c", "k")
                .replace("z", "s")
                .replace("x", "s")
                .replace("#", "ch");
        return s.replaceAll("(.)\\1+", "$1");
    }

    /**
     * Qué tan parecido es lo tipeado al nombre de una calle (los dos normalizados). 0 = iguales de
     * oído; 1 o 2 = con esa cantidad de errores de dedo; {@link #NO} = no se parecen. Cuando lo
     * tipeado son palabras sueltas del nombre ("mate luna") suma medio punto: es peor candidato que
     * el nombre entero con un error.
     */
    public static double puntaje(String tipeadoNorm, String calleNorm) {
        List<String> a = palabras(tipeadoNorm), b = palabras(calleNorm);
        if (a.isEmpty() || b.isEmpty()) return NO;
        String ja = String.join(" ", a), jb = String.join(" ", b);
        if (ja.equals(jb)) return 0;
        int max = erroresPermitidos(ja.length());
        int d = distancia(ja, jb, max);
        if (d <= max) return d;

        // Palabra por palabra, en cualquier orden: todas las tipeadas tienen que estar en el nombre.
        if (a.size() > b.size()) return NO;
        List<String> libres = new ArrayList<>(b);
        int errores = 0;
        for (String palabra : a) {
            int mejor = -1, mejorD = Integer.MAX_VALUE;
            int maxPalabra = erroresPermitidos(palabra.length());
            for (int i = 0; i < libres.size(); i++) {
                int dp = distancia(palabra, libres.get(i), maxPalabra);
                if (dp <= maxPalabra && dp < mejorD) {
                    mejor = i;
                    mejorD = dp;
                }
            }
            if (mejor < 0) return NO;
            libres.remove(mejor);
            errores += mejorD;
        }
        // Con una sola palabra corta ("paz") cualquier calle que la lleve entraría: no alcanza.
        if (a.size() == 1 && a.get(0).length() < 5) return NO;
        return errores + 0.5;
    }

    /** Sin errores hasta 4 letras ("roca" no es "boca"), uno hasta 8, dos de ahí en más. */
    static int erroresPermitidos(int largo) {
        return largo <= 4 ? 0 : largo <= 8 ? 1 : 2;
    }

    /**
     * Distancia de edición contando como un solo error dos letras vecinas invertidas. Corta apenas
     * supera {@code max} (devuelve max + 1): con miles de nombres no hace falta el número exacto.
     */
    static int distancia(String a, String b, int max) {
        if (Math.abs(a.length() - b.length()) > max) return max + 1;
        int[] anteAnterior = new int[b.length() + 1], anterior = new int[b.length() + 1], actual = new int[b.length() + 1];
        for (int j = 0; j <= b.length(); j++) anterior[j] = j;
        for (int i = 1; i <= a.length(); i++) {
            actual[0] = i;
            int minFila = actual[0];
            for (int j = 1; j <= b.length(); j++) {
                int costo = a.charAt(i - 1) == b.charAt(j - 1) ? 0 : 1;
                int v = Math.min(Math.min(actual[j - 1] + 1, anterior[j] + 1), anterior[j - 1] + costo);
                if (i > 1 && j > 1 && a.charAt(i - 1) == b.charAt(j - 2) && a.charAt(i - 2) == b.charAt(j - 1)) {
                    v = Math.min(v, anteAnterior[j - 2] + 1);
                }
                actual[j] = v;
                minFila = Math.min(minFila, v);
            }
            if (minFila > max) return max + 1;
            int[] t = anteAnterior;
            anteAnterior = anterior;
            anterior = actual;
            actual = t;
        }
        return anterior[b.length()];
    }
}
