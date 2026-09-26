package com.cadeteria.backend.common;

/** 410: el link existió pero ya no sirve (venció o ya se usó) — no tiene sentido reintentar con el mismo. */
public class GoneException extends RuntimeException {
    public GoneException(String message) {
        super(message);
    }
}
