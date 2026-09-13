package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.Lookup;
import com.cadeteria.backend.model.Zona;

/** Respuesta comun para las tablas de parametria (tipo_vehiculo, estado_pedido, etc.) y para Zona. */
public record LookupResponse(String id, String nombre) {
    public static LookupResponse from(Lookup lookup) {
        return lookup == null ? null : new LookupResponse(lookup.getId(), lookup.getNombre());
    }

    public static LookupResponse from(Zona zona) {
        return zona == null ? null : new LookupResponse(zona.getId(), zona.getNombre());
    }
}
