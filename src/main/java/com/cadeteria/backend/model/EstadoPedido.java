package com.cadeteria.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Parametría: SIN_ASIGNAR, PENDIENTE, EN_CURSO, FINALIZADO, CANCELADO. Códigos atados al flujo de asignación. */
@Entity
@Table(name = "estado_pedido")
public class EstadoPedido extends Lookup {
}
