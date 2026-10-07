package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Una calle o cuadra de la base propia que el sistema no puede dar por buena solo (2026-10-07, ver
 * {@link com.cadeteria.backend.service.CallesARevisarService}). Se resuelve desde el panel pegando
 * el link de Google Maps de esa dirección o eligiendo a mano. Las resueltas se guardan para no
 * volver a preguntar lo mismo.
 */
@Entity
@Table(name = "calle_duda", indexes = @Index(name = "idx_calle_duda_estado", columnList = "estado"))
public class CalleDuda {

    @Id
    private String id;

    /** "NOMBRE" (dos nombres que se escriben casi igual) o "UBICACION" (la cuadra no cierra con sus vecinas). */
    @Column(nullable = false)
    private String tipo;

    @Column(name = "calle_canonica", nullable = false)
    private String calleCanonica;

    @Column(nullable = false)
    private String localidad;

    @Column(nullable = false)
    private int cuadra;

    /** En las de nombre, la calle con la que se confunde; vacío en las de ubicación. */
    @Column(name = "otra_calle", nullable = false)
    private String otraCalle = "";

    @Column(nullable = false, length = 500)
    private String motivo;

    @Column(nullable = false)
    private String estado;

    /** Qué decidió el sistema o la persona, en palabras. */
    @Column(length = 500)
    private String resultado;

    /** El punto del último link de Google Maps que se pegó, aunque no haya alcanzado para decidir. */
    @Column(name = "link_lat")
    private Double linkLat;

    @Column(name = "link_lng")
    private Double linkLng;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn = Instant.now();

    @Column(name = "resuelta_en")
    private Instant resueltaEn;

    @Column(name = "resuelta_por")
    private String resueltaPor;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }
    public String getCalleCanonica() { return calleCanonica; }
    public void setCalleCanonica(String calleCanonica) { this.calleCanonica = calleCanonica; }
    public String getLocalidad() { return localidad; }
    public void setLocalidad(String localidad) { this.localidad = localidad; }
    public int getCuadra() { return cuadra; }
    public void setCuadra(int cuadra) { this.cuadra = cuadra; }
    public String getOtraCalle() { return otraCalle; }
    public void setOtraCalle(String otraCalle) { this.otraCalle = otraCalle; }
    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }
    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
    public String getResultado() { return resultado; }
    public void setResultado(String resultado) { this.resultado = resultado; }
    public Double getLinkLat() { return linkLat; }
    public void setLinkLat(Double linkLat) { this.linkLat = linkLat; }
    public Double getLinkLng() { return linkLng; }
    public void setLinkLng(Double linkLng) { this.linkLng = linkLng; }
    public Instant getCreadaEn() { return creadaEn; }
    public void setCreadaEn(Instant creadaEn) { this.creadaEn = creadaEn; }
    public Instant getResueltaEn() { return resueltaEn; }
    public void setResueltaEn(Instant resueltaEn) { this.resueltaEn = resueltaEn; }
    public String getResueltaPor() { return resueltaPor; }
    public void setResueltaPor(String resueltaPor) { this.resueltaPor = resueltaPor; }
}
