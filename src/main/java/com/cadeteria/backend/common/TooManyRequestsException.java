package com.cadeteria.backend.common;

/** 429: demasiados intentos seguidos (login bloqueado temporalmente, códigos por SMS, etc). */
public class TooManyRequestsException extends RuntimeException {
    public TooManyRequestsException(String message) {
        super(message);
    }
}
