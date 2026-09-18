package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Código de 6 dígitos para "olvidé mi contraseña" del cadete (app Android) — se manda
 * por mail, el cadete lo tipea en la app junto con la contraseña nueva. Un solo uso,
 * vence a los 15 minutos, y se invalida solo después de {@code MAX_INTENTOS} intentos
 * fallidos (ver PasswordRecoveryService) para no dejarlo fuerza-brutear (son solo 6
 * dígitos, mucho menos espacio que el token de SolicitudCadete).
 */
@Entity
@Table(name = "cadete_reset_password")
public class CadeteResetPassword {

    @Id
    private String id;

    @Column(nullable = false)
    private String cadeteId;

    @Column(nullable = false, length = 6)
    private String codigo;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(nullable = false)
    private Instant expiraEn;

    /** Null = todavía no se usó. */
    private Instant usadoEn;

    @Column(nullable = false)
    private int intentos = 0;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getCadeteId() {
        return cadeteId;
    }

    public void setCadeteId(String cadeteId) {
        this.cadeteId = cadeteId;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
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

    public Instant getUsadoEn() {
        return usadoEn;
    }

    public void setUsadoEn(Instant usadoEn) {
        this.usadoEn = usadoEn;
    }

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }
}
