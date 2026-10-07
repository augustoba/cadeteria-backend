package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Registro de cada vez que dos nombres de calle se unieron en uno (2026-10-03, ver
 * {@link com.cadeteria.backend.service.UnionCallesService}): qué nombre se fue, cuál quedó y con
 * qué cuadra se comprobó que eran la misma calle. Es para poder revisar las uniones; no se usa
 * para buscar.
 */
@Entity
@Table(name = "calle_union")
public class CalleUnion {

    @Id
    private String id;

    @Column(name = "se_fue", nullable = false)
    private String seFue;

    @Column(nullable = false)
    private String queda;

    /** La localidad y la cuadra donde los dos nombres estaban en el mismo lugar. */
    @Column(nullable = false)
    private String localidad;

    @Column(nullable = false)
    private int cuadra;

    @Column(name = "distancia_m", nullable = false)
    private int distanciaM;

    /** Cuadras que pasaron de un nombre al otro sin chocar con ninguna. */
    @Column(name = "filas_movidas", nullable = false)
    private int filasMovidas;

    /** Cuadras que estaban con los dos nombres: quedó la de la fuente más confiable. */
    @Column(name = "filas_fusionadas", nullable = false)
    private int filasFusionadas;

    /** "al aprender" o "revisión de duplicadas". */
    @Column(nullable = false)
    private String origen;

    @Column(nullable = false)
    private Instant cuando = Instant.now();

    /**
     * Cómo estaba cada fila tocada antes de unir (JSON), para poder deshacerla (2026-10-07). Null en
     * las uniones anteriores a esa fecha y en las hechas con un .sql: esas no se pueden deshacer.
     */
    /* Sin columnDefinition: con los nombres entre comillas (globally_quoted_identifiers) el tipo sale como `LONGTEXT` y el ALTER falla. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(length = 2_000_000)
    private String respaldo;

    @Column(name = "deshecha_en")
    private Instant deshechaEn;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getSeFue() { return seFue; }
    public void setSeFue(String seFue) { this.seFue = seFue; }
    public String getQueda() { return queda; }
    public void setQueda(String queda) { this.queda = queda; }
    public String getLocalidad() { return localidad; }
    public void setLocalidad(String localidad) { this.localidad = localidad; }
    public int getCuadra() { return cuadra; }
    public void setCuadra(int cuadra) { this.cuadra = cuadra; }
    public int getDistanciaM() { return distanciaM; }
    public void setDistanciaM(int distanciaM) { this.distanciaM = distanciaM; }
    public int getFilasMovidas() { return filasMovidas; }
    public void setFilasMovidas(int filasMovidas) { this.filasMovidas = filasMovidas; }
    public int getFilasFusionadas() { return filasFusionadas; }
    public void setFilasFusionadas(int filasFusionadas) { this.filasFusionadas = filasFusionadas; }
    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }
    public String getRespaldo() { return respaldo; }
    public void setRespaldo(String respaldo) { this.respaldo = respaldo; }
    public Instant getDeshechaEn() { return deshechaEn; }
    public void setDeshechaEn(Instant deshechaEn) { this.deshechaEn = deshechaEn; }
    public boolean isSePuedeDeshacer() { return respaldo != null && deshechaEn == null; }
    public Instant getCuando() { return cuando; }
    public void setCuando(Instant cuando) { this.cuando = cuando; }
}
