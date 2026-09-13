package com.cadeteria.backend.common;

/** Regla de negocio violada (ej: aceptar un pedido cuya oferta ya expiro). */
public class BadRequestException extends RuntimeException {
    public BadRequestException(String message) {
        super(message);
    }
}
