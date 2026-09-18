package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/** Usuario del panel de administracion. La contrasena se guarda hasheada con BCrypt. */
@Entity
@Table(name = "admin")
public class Admin {

    @Id
    private String id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private boolean enabled = true;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    /** Bloqueo temporal tras varios intentos fallidos de login (ronda 6, punto 49). */
    @Column(nullable = false)
    private int intentosFallidos = 0;
    private Instant bloqueadoHasta;

    /**
     * Igual que {@link Cadete#getSessionToken()}: se regenera en cada login y
     * {@link com.cadeteria.backend.config.JwtAuthFilter} lo compara contra el claim "sid"
     * del token. Permite el botón de emergencia "cerrar todas las sesiones" (ronda 6,
     * punto 64) — invalidar este valor invalida cualquier token ya emitido para este admin.
     */
    private String sessionToken;

    /**
     * Id de un {@link Rol} (roles con permisos configurables, mejora 2026-09-16 — ver
     * RolService/RolSeeder). Sigue siendo un String plano (no una FK JPA) por simplicidad,
     * mismo patrón que {@code WhatsappMensaje.chipUsado}. Valores legacy "DUENO"/"OPERADOR"
     * de antes de este cambio se siguen resolviendo bien — RolService.permisosEfectivos
     * trata "DUENO" como el rol "admin", y null/cualquier otra cosa como "operador" — no
     * hizo falta migrar datos existentes.
     */
    @Column
    private String rol = "admin";

    /**
     * Contraseña temporal (alta o "reenviar contraseña" desde Usuarios) sin cambiar
     * todavía — mejora 2026-09-17, pedida por el dueño ("debería vencer a los 10
     * minutos"). Mientras sea true, el login solo funciona hasta
     * {@link #passwordTemporalExpira}; pasado eso, hay que pedirle a otro admin con
     * permiso "usuarios" que la reenvíe (genera una nueva, con otros 10 minutos).
     */
    @Column(nullable = false)
    private boolean debeCambiarPassword = false;
    private Instant passwordTemporalExpira;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
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

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
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

    public String getSessionToken() {
        return sessionToken;
    }

    public void setSessionToken(String sessionToken) {
        this.sessionToken = sessionToken;
    }

    public String getRol() {
        return rol;
    }

    public void setRol(String rol) {
        this.rol = rol;
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
}
