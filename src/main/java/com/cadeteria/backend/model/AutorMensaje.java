package com.cadeteria.backend.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Parametría: ADMIN, CADETE. */
@Entity
@Table(name = "autor_mensaje")
public class AutorMensaje extends Lookup {
}
