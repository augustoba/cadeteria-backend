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
}
