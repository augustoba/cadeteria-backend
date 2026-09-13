package com.cadeteria.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Parametría: MOTO, BICI hoy — abierta a sumar otros vehículos sin tocar código. */
@Entity
@Table(name = "tipo_vehiculo")
public class TipoVehiculo extends Lookup {
}
