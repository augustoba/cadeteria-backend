package com.cadeteria.backend.common;

/**
 * 409: el pedido del usuario es correcto pero choca con el estado actual del dato — casi siempre
 * porque cambió mientras lo tenía en pantalla (la oferta venció, otro admin ya lo asignó, el reclamo
 * ya se cerró). La app o el panel tienen que refrescar, no corregir lo que mandaron (2026-09-26).
 */
public class ConflictException extends RuntimeException {
    public ConflictException(String message) {
        super(message);
    }
}
