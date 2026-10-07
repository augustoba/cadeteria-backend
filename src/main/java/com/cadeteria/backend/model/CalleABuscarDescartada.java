package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Una cuadra que la lista "A buscar" de la pantalla Calles pedía y alguien descartó porque no
 * existe (2026-10-07, ver {@link com.cadeteria.backend.service.CallesABuscarService}). La lista se
 * calcula sola cada vez; esto es lo único que se guarda, para no volver a pedir lo mismo.
 */
@Entity
@Table(name = "calle_a_buscar_descartada", uniqueConstraints = @UniqueConstraint(columnNames = {"calle_canonica", "localidad", "cuadra"}))
public class CalleABuscarDescartada {

    @Id
    private String id;

    @Column(name = "calle_canonica", nullable = false)
    private String calleCanonica;

    @Column(nullable = false)
    private String localidad;

    @Column(nullable = false)
    private int cuadra;

    @Column(name = "descartada_por")
    private String descartadaPor;

    @Column(name = "descartada_en", nullable = false)
    private Instant descartadaEn = Instant.now();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCalleCanonica() { return calleCanonica; }
    public void setCalleCanonica(String calleCanonica) { this.calleCanonica = calleCanonica; }
    public String getLocalidad() { return localidad; }
    public void setLocalidad(String localidad) { this.localidad = localidad; }
    public int getCuadra() { return cuadra; }
    public void setCuadra(int cuadra) { this.cuadra = cuadra; }
    public String getDescartadaPor() { return descartadaPor; }
    public void setDescartadaPor(String descartadaPor) { this.descartadaPor = descartadaPor; }
    public Instant getDescartadaEn() { return descartadaEn; }
    public void setDescartadaEn(Instant descartadaEn) { this.descartadaEn = descartadaEn; }
}
