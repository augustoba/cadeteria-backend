package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Formulario de alta de cadete por link propio (ronda 7): el admin genera un link con
 * un token de un solo uso, se lo pasa al postulante, este completa sus datos y fotos, y
 * el admin revisa/aprueba desde el panel — recién ahí se crea el Cadete real.
 */
@Entity
@Table(name = "solicitud_cadete")
public class SolicitudCadete {

    @Id
    private String id;

    @Column(nullable = false, unique = true, length = 36)
    private String token;

    /** "PENDIENTE" (link generado, sin completar) -> "EN_REVISION" (enviado) -> "APROBADA" | "RECHAZADA". */
    @Column(nullable = false, length = 20)
    private String estado = "PENDIENTE";

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    /** Pasado este momento el link deja de ser válido aunque nunca se haya usado. */
    @Column(nullable = false)
    private Instant expiraEn;

    private Instant enviadaEn;

    private String nombre;
    private String apellido;
    private String dni;
    private String telefono;
    private String email;

    @ManyToOne
    @JoinColumn(name = "tipo_vehiculo_id")
    private TipoVehiculo tipoVehiculo;
    private String vehiculoColor;
    private String vehiculoPatente;
    private String vehiculoMarca;
    private String vehiculoModelo;

    @Column(length = 500)
    private String fotoUrl;
    @Column(length = 500)
    private String fotoVehiculoUrl;
    @Column(length = 500)
    private String fotoCarnetUrl;
    @Column(length = 500)
    private String fotoCarnetDorsoUrl;
    @Column(length = 500)
    private String fotoTarjetaVerdeUrl;
    @Column(length = 500)
    private String fotoTarjetaVerdeDorsoUrl;

    /** El postulante propone un usuario; el admin lo puede cambiar al aprobar. */
    private String usernamePropuesto;

    private String motivoRechazo;

    /**
     * Estado A_CORREGIR (2026-09-25): qué datos o fotos marcó mal el admin y por qué, como JSON
     * {"fotoCarnetUrl": "está borrosa", ...} — ver SolicitudCadeteService.CAMPOS_REVISABLES.
     */
    @Column(length = 3000)
    private String observaciones;

    /** Cuántas veces se le pidió corregir — null = nunca. */
    private Integer correcciones;

    /** Una vez aprobada, el id del Cadete que se creó — para trazabilidad. */
    private String cadeteCreadoId;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }

    public Instant getExpiraEn() {
        return expiraEn;
    }

    public void setExpiraEn(Instant expiraEn) {
        this.expiraEn = expiraEn;
    }

    public Instant getEnviadaEn() {
        return enviadaEn;
    }

    public void setEnviadaEn(Instant enviadaEn) {
        this.enviadaEn = enviadaEn;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getApellido() {
        return apellido;
    }

    public void setApellido(String apellido) {
        this.apellido = apellido;
    }

    public String getDni() {
        return dni;
    }

    public void setDni(String dni) {
        this.dni = dni;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public TipoVehiculo getTipoVehiculo() {
        return tipoVehiculo;
    }

    public void setTipoVehiculo(TipoVehiculo tipoVehiculo) {
        this.tipoVehiculo = tipoVehiculo;
    }

    public String getVehiculoColor() {
        return vehiculoColor;
    }

    public void setVehiculoColor(String vehiculoColor) {
        this.vehiculoColor = vehiculoColor;
    }

    public String getVehiculoPatente() {
        return vehiculoPatente;
    }

    public void setVehiculoPatente(String vehiculoPatente) {
        this.vehiculoPatente = vehiculoPatente;
    }

    public String getVehiculoMarca() {
        return vehiculoMarca;
    }

    public void setVehiculoMarca(String vehiculoMarca) {
        this.vehiculoMarca = vehiculoMarca;
    }

    public String getVehiculoModelo() {
        return vehiculoModelo;
    }

    public void setVehiculoModelo(String vehiculoModelo) {
        this.vehiculoModelo = vehiculoModelo;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
    }

    public String getFotoVehiculoUrl() {
        return fotoVehiculoUrl;
    }

    public void setFotoVehiculoUrl(String fotoVehiculoUrl) {
        this.fotoVehiculoUrl = fotoVehiculoUrl;
    }

    public String getFotoCarnetUrl() {
        return fotoCarnetUrl;
    }

    public void setFotoCarnetUrl(String fotoCarnetUrl) {
        this.fotoCarnetUrl = fotoCarnetUrl;
    }

    public String getFotoCarnetDorsoUrl() {
        return fotoCarnetDorsoUrl;
    }

    public void setFotoCarnetDorsoUrl(String fotoCarnetDorsoUrl) {
        this.fotoCarnetDorsoUrl = fotoCarnetDorsoUrl;
    }

    public String getFotoTarjetaVerdeUrl() {
        return fotoTarjetaVerdeUrl;
    }

    public void setFotoTarjetaVerdeUrl(String fotoTarjetaVerdeUrl) {
        this.fotoTarjetaVerdeUrl = fotoTarjetaVerdeUrl;
    }

    public String getFotoTarjetaVerdeDorsoUrl() {
        return fotoTarjetaVerdeDorsoUrl;
    }

    public void setFotoTarjetaVerdeDorsoUrl(String fotoTarjetaVerdeDorsoUrl) {
        this.fotoTarjetaVerdeDorsoUrl = fotoTarjetaVerdeDorsoUrl;
    }

    public String getUsernamePropuesto() {
        return usernamePropuesto;
    }

    public void setUsernamePropuesto(String usernamePropuesto) {
        this.usernamePropuesto = usernamePropuesto;
    }

    public String getObservaciones() {
        return observaciones;
    }

    public void setObservaciones(String observaciones) {
        this.observaciones = observaciones;
    }

    public Integer getCorrecciones() {
        return correcciones;
    }

    public void setCorrecciones(Integer correcciones) {
        this.correcciones = correcciones;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public String getCadeteCreadoId() {
        return cadeteCreadoId;
    }

    public void setCadeteCreadoId(String cadeteCreadoId) {
        this.cadeteCreadoId = cadeteCreadoId;
    }
}
