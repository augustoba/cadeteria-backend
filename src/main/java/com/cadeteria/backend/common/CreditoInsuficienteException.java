package com.cadeteria.backend.common;

/**
 * El cadete PORCENTAJE quiso aceptar un viaje y no le alcanza el crédito para la comisión
 * (2026-10-19). Es un 409 como cualquier conflicto, pero la transacción no se deshace: la oferta
 * queda rechazada y el viaje ya pasó a otro cadete.
 */
public class CreditoInsuficienteException extends ConflictException {
    public CreditoInsuficienteException(String message) {
        super(message);
    }
}
