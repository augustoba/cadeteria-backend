package com.cadeteria.backend.model;

import com.cadeteria.backend.util.TelefonoUtils;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalTime;

@Entity
@Table(name = "cadete")
public class Cadete {

    @Id
    private String id;

    @Column(nullable = false)
    private String nombre;

    @Column(nullable = false)
    private String apellido;

    @Column(nullable = false, unique = true)
    private String dni;

    @Column(nullable = false)
    private String telefono;

    /** Para mandarle el mail de alta con usuario/contraseña temporal (ronda 7) — null en cadetes viejos. */
    private String email;

    @Column(length = 500)
    private String fotoUrl;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tipo_vehiculo_id")
    private TipoVehiculo tipoVehiculo;

    /** Datos del vehiculo mostrados en la pagina publica de seguimiento (ver diseno-tecnico.md sección 8). */
    private String vehiculoColor;
    private String vehiculoPatente;
    private String vehiculoMarca;
    private String vehiculoModelo;
    private Integer vehiculoAnio;

    /** Foto de la moto/bici. Se sube directo a Cloudinary desde el front (igual que en el ecommerce) — aca solo se guarda la URL resultante. */
    @Column(length = 500)
    private String fotoVehiculoUrl;

    /** Documentacion del cadete/vehiculo, subida igual que fotoVehiculoUrl. */
    @Column(length = 500)
    private String fotoCarnetUrl;

    @Column(length = 500)
    private String fotoTarjetaVerdeUrl;

    /** Para que el cliente le transfiera (spec: pago por transferencia) — el cadete los carga solo desde la app. */
    private String cbu;
    private String aliasCbu;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    /** Token FCM del dispositivo actual, se actualiza en cada login/refresh de la app. */
    @Column(length = 500)
    private String fcmToken;

    /** false = no puede loguearse ni recibir asignaciones (spec 5.4, pago semanal). */
    @Column(nullable = false)
    private boolean activo = true;

    @ManyToOne(optional = false)
    @JoinColumn(name = "estado_id")
    private EstadoCadete estado;

    private Double lat;
    private Double lng;
    private Instant ubicacionActualizadaEn;

    /** Recalculada por reverse geocoding en cada update de ubicacion (spec 5.1). */
    @ManyToOne
    @JoinColumn(name = "zona_actual_id")
    private Zona zonaActual;

    /** null = sin tope (spec 5.2). */
    @Column(precision = 12, scale = 2)
    private BigDecimal montoMaximoTransportado;

    /** null = sin tope (spec 5.3). */
    private Integer maxViajesSimultaneos;

    /** null = sin tope — máximo de viajes FINALIZADOS por día/semana (ronda 6, punto 33). */
    private Integer maxViajesDiarios;
    private Integer maxViajesSemanales;

    /**
     * Turno fijo del cadete (ambos null = sin turno fijo, disponible siempre — comportamiento
     * previo). Si están cargados, la sugerencia automática de asignación (PedidoService.buscarCandidato)
     * solo lo tiene en cuenta dentro de ese rango horario. Soporta turnos que cruzan medianoche
     * (ej. 22:00 a 06:00): ver PedidoService.dentroDeTurno.
     */
    private LocalTime turnoInicio;
    private LocalTime turnoFin;

    /**
     * Cuando quedo libre por ultima vez: define el orden FIFO de la cola de asignacion
     * (diseno-tecnico.md sección 5) — el que quedo libre antes, primero en la cola.
     */
    @Column(nullable = false)
    private Instant ordenColaEspera = Instant.now();

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /**
     * Se regenera en cada login exitoso y viaja como claim "sid" del JWT (spec: un solo
     * dispositivo activo por cuenta) — un token viejo con otro "sid" deja de ser valido
     * en {@link com.cadeteria.backend.config.JwtAuthFilter}, sin necesidad de invalidar
     * el JWT en si (es stateless).
     */
    @Column(length = 36)
    private String sessionToken;

    /** Bloqueo temporal tras varios intentos fallidos de login (ronda 6, punto 49). */
    @Column(nullable = false)
    private int intentosFallidos = 0;
    private Instant bloqueadoHasta;

    /**
     * Modelo de cobro del cadete (ronda 7): "SEMANAL" (paga una cuota fija por semana,
     * se lo habilita a mano) o "PORCENTAJE" (paga con crédito precargado, se le descuenta
     * un % de cada viaje al aceptarlo).
     */
    @Column(nullable = false, length = 20)
    private String modalidadPago = "SEMANAL";

    /**
     * Solo aplica con modalidadPago=SEMANAL: si puede loguearse/recibir ofertas esta
     * semana. Se pone en false para todos los cadetes SEMANAL el lunes a la madrugada
     * (PedidoService/CadeteService, job programado) y el admin lo vuelve a habilitar a
     * mano (con pago completo o parcial + vencimiento) — independiente de `activo`, que
     * es el alta/baja general de la cuenta.
     */
    @Column(nullable = false)
    private boolean habilitadoPago = true;

    /** Cuánto pagó de la cuota semanal configurada — null hasta que el admin lo carga la primera vez esta semana. */
    @Column(precision = 12, scale = 2)
    private BigDecimal pagoSemanalMontoPagado;

    /**
     * Precio de la cuota semanal DE ESTE cadete (mejora: pantalla "Pagos" unificada) — antes
     * era un único monto global en Configuración para todos; ahora cada cadete tiene el suyo,
     * se recuerda de una semana a la otra y el admin lo puede cambiar cuando quiera. null =
     * todavía no se cargó ninguno, se usa el monto global de Configuración como referencia.
     */
    @Column(precision = 12, scale = 2)
    private BigDecimal montoSemanalActual;

    /** Si el admin lo habilitó con pago parcial: fecha límite para completar el resto, si no se deshabilita solo. */
    private Instant pagoSemanalVenceEn;

    /** Solo aplica con modalidadPago=PORCENTAJE: saldo a favor, se descuenta la comisión de cada viaje al aceptarlo. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal creditoDisponible = BigDecimal.ZERO;

    /** Para no repetir el aviso de crédito bajo en cada viaje aceptado. */
    @Column(nullable = false)
    private boolean alertaCreditoBajoEnviada = false;

    /** Notas libres del admin sobre este cadete (ej. "llega tarde seguido") — ronda 10, punto 103. */
    @jakarta.persistence.Lob
    private String notasInternas;

    /**
     * Contraseña temporal (alta o "reenviar contraseña") sin cambiar todavía — mejora
     * 2026-09-17, pedida por el dueño ("debería vencer a los 10 minutos"). Mientras sea
     * true, el login solo funciona hasta {@link #passwordTemporalExpira}; pasado eso, el
     * admin tiene que reenviarle una nueva desde el panel.
     */
    @Column(nullable = false)
    private boolean debeCambiarPassword = false;
    private Instant passwordTemporalExpira;

    /** Última versión de APK con la que se logueó — visibilidad para el admin (mejora 2026-09-17), no bloquea nada. */
    private Integer ultimaVersionApp;
    private Instant ultimaVersionAppEn;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    @PrePersist
    @PreUpdate
    private void normalizarTelefono() {
        this.telefono = TelefonoUtils.normalizar(this.telefono);
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getFotoUrl() {
        return fotoUrl;
    }

    public void setFotoUrl(String fotoUrl) {
        this.fotoUrl = fotoUrl;
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

    public String getFotoVehiculoUrl() {
        return fotoVehiculoUrl;
    }

    public void setFotoVehiculoUrl(String fotoVehiculoUrl) {
        this.fotoVehiculoUrl = fotoVehiculoUrl;
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

    public Integer getVehiculoAnio() {
        return vehiculoAnio;
    }

    public void setVehiculoAnio(Integer vehiculoAnio) {
        this.vehiculoAnio = vehiculoAnio;
    }

    public String getFotoCarnetUrl() {
        return fotoCarnetUrl;
    }

    public void setFotoCarnetUrl(String fotoCarnetUrl) {
        this.fotoCarnetUrl = fotoCarnetUrl;
    }

    public String getFotoTarjetaVerdeUrl() {
        return fotoTarjetaVerdeUrl;
    }

    public void setFotoTarjetaVerdeUrl(String fotoTarjetaVerdeUrl) {
        this.fotoTarjetaVerdeUrl = fotoTarjetaVerdeUrl;
    }

    public String getCbu() {
        return cbu;
    }

    public void setCbu(String cbu) {
        this.cbu = cbu;
    }

    public String getAliasCbu() {
        return aliasCbu;
    }

    public void setAliasCbu(String aliasCbu) {
        this.aliasCbu = aliasCbu;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public void setPasswordHash(String passwordHash) {
        this.passwordHash = passwordHash;
    }

    public String getFcmToken() {
        return fcmToken;
    }

    public void setFcmToken(String fcmToken) {
        this.fcmToken = fcmToken;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public EstadoCadete getEstado() {
        return estado;
    }

    public void setEstado(EstadoCadete estado) {
        this.estado = estado;
    }

    public Double getLat() {
        return lat;
    }

    public void setLat(Double lat) {
        this.lat = lat;
    }

    public Double getLng() {
        return lng;
    }

    public void setLng(Double lng) {
        this.lng = lng;
    }

    public Instant getUbicacionActualizadaEn() {
        return ubicacionActualizadaEn;
    }

    public void setUbicacionActualizadaEn(Instant ubicacionActualizadaEn) {
        this.ubicacionActualizadaEn = ubicacionActualizadaEn;
    }

    public Zona getZonaActual() {
        return zonaActual;
    }

    public void setZonaActual(Zona zonaActual) {
        this.zonaActual = zonaActual;
    }

    public BigDecimal getMontoMaximoTransportado() {
        return montoMaximoTransportado;
    }

    public void setMontoMaximoTransportado(BigDecimal montoMaximoTransportado) {
        this.montoMaximoTransportado = montoMaximoTransportado;
    }

    public Integer getMaxViajesSimultaneos() {
        return maxViajesSimultaneos;
    }

    public void setMaxViajesSimultaneos(Integer maxViajesSimultaneos) {
        this.maxViajesSimultaneos = maxViajesSimultaneos;
    }

    public Integer getMaxViajesDiarios() {
        return maxViajesDiarios;
    }

    public void setMaxViajesDiarios(Integer maxViajesDiarios) {
        this.maxViajesDiarios = maxViajesDiarios;
    }

    public Integer getMaxViajesSemanales() {
        return maxViajesSemanales;
    }

    public void setMaxViajesSemanales(Integer maxViajesSemanales) {
        this.maxViajesSemanales = maxViajesSemanales;
    }

    public LocalTime getTurnoInicio() {
        return turnoInicio;
    }

    public void setTurnoInicio(LocalTime turnoInicio) {
        this.turnoInicio = turnoInicio;
    }

    public LocalTime getTurnoFin() {
        return turnoFin;
    }

    public void setTurnoFin(LocalTime turnoFin) {
        this.turnoFin = turnoFin;
    }

    public Instant getOrdenColaEspera() {
        return ordenColaEspera;
    }

    public void setOrdenColaEspera(Instant ordenColaEspera) {
        this.ordenColaEspera = ordenColaEspera;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public void setIntentosFallidos(int intentosFallidos) {
        this.intentosFallidos = intentosFallidos;
    }

    public Instant getBloqueadoHasta() {
        return bloqueadoHasta;
    }

    public void setBloqueadoHasta(Instant bloqueadoHasta) {
        this.bloqueadoHasta = bloqueadoHasta;
    }

    public String getModalidadPago() {
        return modalidadPago;
    }

    public void setModalidadPago(String modalidadPago) {
        this.modalidadPago = modalidadPago;
    }

    public boolean isHabilitadoPago() {
        return habilitadoPago;
    }

    public void setHabilitadoPago(boolean habilitadoPago) {
        this.habilitadoPago = habilitadoPago;
    }

    public BigDecimal getPagoSemanalMontoPagado() {
        return pagoSemanalMontoPagado;
    }

    public void setPagoSemanalMontoPagado(BigDecimal pagoSemanalMontoPagado) {
        this.pagoSemanalMontoPagado = pagoSemanalMontoPagado;
    }

    public BigDecimal getMontoSemanalActual() {
        return montoSemanalActual;
    }

    public void setMontoSemanalActual(BigDecimal montoSemanalActual) {
        this.montoSemanalActual = montoSemanalActual;
    }

    public Instant getPagoSemanalVenceEn() {
        return pagoSemanalVenceEn;
    }

    public void setPagoSemanalVenceEn(Instant pagoSemanalVenceEn) {
        this.pagoSemanalVenceEn = pagoSemanalVenceEn;
    }

    public BigDecimal getCreditoDisponible() {
        return creditoDisponible;
    }

    public void setCreditoDisponible(BigDecimal creditoDisponible) {
        this.creditoDisponible = creditoDisponible;
    }

    public boolean isAlertaCreditoBajoEnviada() {
        return alertaCreditoBajoEnviada;
    }

    public void setAlertaCreditoBajoEnviada(boolean alertaCreditoBajoEnviada) {
        this.alertaCreditoBajoEnviada = alertaCreditoBajoEnviada;
    }

    public String getNotasInternas() {
        return notasInternas;
    }

    public void setNotasInternas(String notasInternas) {
        this.notasInternas = notasInternas;
    }

    public boolean isDebeCambiarPassword() {
        return debeCambiarPassword;
    }

    public void setDebeCambiarPassword(boolean debeCambiarPassword) {
        this.debeCambiarPassword = debeCambiarPassword;
    }

    public Instant getPasswordTemporalExpira() {
        return passwordTemporalExpira;
    }

    public void setPasswordTemporalExpira(Instant passwordTemporalExpira) {
        this.passwordTemporalExpira = passwordTemporalExpira;
    }

    public Integer getUltimaVersionApp() {
        return ultimaVersionApp;
    }

    public void setUltimaVersionApp(Integer ultimaVersionApp) {
        this.ultimaVersionApp = ultimaVersionApp;
    }

    public Instant getUltimaVersionAppEn() {
        return ultimaVersionAppEn;
    }

    public void setUltimaVersionAppEn(Instant ultimaVersionAppEn) {
        this.ultimaVersionAppEn = ultimaVersionAppEn;
    }
}
