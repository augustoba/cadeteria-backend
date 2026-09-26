package com.cadeteria.backend.config;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Permisos del usuario que hace el pedido actual (los PERM_x que arma {@link JwtAuthFilter}), para
 * las reglas que dependen de QUIÉN pide y no solo de la URL (2026-09-26, superadmin): un admin no
 * puede tocar la configuración técnica, borrar API keys ni darse el permiso "sistema".
 * Sin usuario autenticado (seeders, jobs) se considera que no tiene ningún permiso.
 */
public final class PermisosActuales {

    private PermisosActuales() {
    }

    public static boolean tiene(String permiso) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) return false;
        String buscado = "PERM_" + permiso;
        return auth.getAuthorities().stream().anyMatch(a -> buscado.equals(a.getAuthority()));
    }

    public static boolean esSuperadmin() {
        return tiene(RolSeeder.PERMISO_SISTEMA);
    }
}
