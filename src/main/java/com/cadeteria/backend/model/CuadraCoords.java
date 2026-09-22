package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

/**
 * Coordenadas ESTIMATIVAS (nivel cuadra, no número exacto) de una calle canónica ya
 * geocodificada, para no volver a pagar el mismo geocode dos veces (ver
 * documentacion/spec-geocoding-cache.md §5.1 y §5.3). {@code cuadra} es el bloque de
 * centena de la altura (1502 -> 1500), calculado por {@link com.cadeteria.backend.util.DireccionUtils#cuadra}.
 * Sin TTL: una calle no se mueve, la entrada sirve para siempre.
 * <p>
 * {@code localidad} SÍ es parte de la clave acá (a diferencia de {@link DireccionAlias}): el
 * nombre de calle que devuelve el geocoder no distingue ciudad ("Rivadavia" existe en San Miguel
 * de Tucumán y en Lules), así que sin localidad en la clave dos pueblos con la misma calle
 * pisarían la misma fila (ver {@link com.cadeteria.backend.service.DireccionCacheService}).
 */
@Entity
@Table(name = "cuadra_coords", uniqueConstraints = @UniqueConstraint(columnNames = {"calle_canonica", "localidad", "cuadra"}))
public class CuadraCoords {

    @Id
    private String id;

    @Column(name = "calle_canonica", nullable = false)
    private String calleCanonica;

    @Column(nullable = false)
    private String localidad;

    @Column(nullable = false)
    private int cuadra;

    @Column(nullable = false)
    private double lat;

    @Column(nullable = false)
    private double lng;

    @Column(nullable = false)
    private boolean approximate;

    @Column(nullable = false)
    private String proveedor;

    /** Cuántas veces se usó/confirmó esta fila desde que se creó — sirve para medir hit rate (§6.3). */
    @Column(nullable = false)
    private int confirmaciones = 1;

    @Column(name = "creada_en", nullable = false)
    private Instant creadaEn = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCalleCanonica() {
        return calleCanonica;
    }

    public void setCalleCanonica(String calleCanonica) {
        this.calleCanonica = calleCanonica;
    }

    public String getLocalidad() {
        return localidad;
    }

    public void setLocalidad(String localidad) {
        this.localidad = localidad;
    }

    public int getCuadra() {
        return cuadra;
    }

    public void setCuadra(int cuadra) {
        this.cuadra = cuadra;
    }

    public double getLat() {
        return lat;
    }

    public void setLat(double lat) {
        this.lat = lat;
    }

    public double getLng() {
        return lng;
    }

    public void setLng(double lng) {
        this.lng = lng;
    }

    public boolean isApproximate() {
        return approximate;
    }

    public void setApproximate(boolean approximate) {
        this.approximate = approximate;
    }

    public String getProveedor() {
        return proveedor;
    }

    public void setProveedor(String proveedor) {
        this.proveedor = proveedor;
    }

    public int getConfirmaciones() {
        return confirmaciones;
    }

    public void setConfirmaciones(int confirmaciones) {
        this.confirmaciones = confirmaciones;
    }

    public Instant getCreadaEn() {
        return creadaEn;
    }

    public void setCreadaEn(Instant creadaEn) {
        this.creadaEn = creadaEn;
    }
}
