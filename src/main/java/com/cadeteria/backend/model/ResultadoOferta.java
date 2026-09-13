package com.cadeteria.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Parametría: PENDIENTE, ACEPTADO, RECHAZADO, EXPIRADO. Códigos atados al flujo de asignación. */
@Entity
@Table(name = "resultado_oferta")
public class ResultadoOferta extends Lookup {
}
