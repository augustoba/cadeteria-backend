package com.cadeteria.backend.common;

/** 503: depende de un servicio externo que ahora no está disponible (sin cupo de API, gateway caído). */
public class ServiceUnavailableException extends RuntimeException {
    public ServiceUnavailableException(String message) {
        super(message);
    }
}
