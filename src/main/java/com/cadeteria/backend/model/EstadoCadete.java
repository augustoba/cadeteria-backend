package com.cadeteria.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Parametría: LIBRE, OCUPADO, DESCONECTADO. Los códigos están atados a la lógica de asignación. */
@Entity
@Table(name = "estado_cadete")
public class EstadoCadete extends Lookup {
}
