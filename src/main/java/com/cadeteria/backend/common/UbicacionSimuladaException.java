package com.cadeteria.backend.common;

/**
 * El cadete quiso marcar Retirado/Entregado con una app de GPS falso (carril B, 2026-09-28). Es un 400
 * como cualquier regla violada, pero la transacción NO se deshace: el intento tiene que quedar
 * registrado en el pedido y en el cadete aunque no se deje marcar.
 */
public class UbicacionSimuladaException extends BadRequestException {
    public UbicacionSimuladaException(String message) {
        super(message);
    }
}
