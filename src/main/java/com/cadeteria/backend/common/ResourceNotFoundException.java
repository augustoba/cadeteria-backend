package com.cadeteria.backend.common;

public class ResourceNotFoundException extends RuntimeException {
    public ResourceNotFoundException(String message) {
        super(message);
    }

    public static ResourceNotFoundException of(String what, Object id) {
        return new ResourceNotFoundException(what + " no encontrado: " + id);
    }
}
