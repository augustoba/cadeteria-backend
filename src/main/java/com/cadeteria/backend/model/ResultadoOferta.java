package com.cadeteria.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/**
 * Parametría: PENDIENTE, ACEPTADO, RECHAZADO, EXPIRADO, QUITADO_ADMIN, SIN_CREDITO (2026-10-19: quiso
 * aceptar sin crédito para la comisión; no lo penaliza). Códigos atados al flujo de asignación.
 */
@Entity
@Table(name = "resultado_oferta")
public class ResultadoOferta extends Lookup {
}
