package com.cadeteria.backend.common;

/** 403 con un mensaje propio: la persona está identificada pero eso no le corresponde (ej. el chat de otro cadete). */
public class ForbiddenException extends RuntimeException {
    public ForbiddenException(String message) {
        super(message);
    }
}
