package com.cadeteria.backend.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/**
 * Código de 6 dígitos para confirmar que quien carga un pedido en "/pedir" (sin login)
 * es dueño del teléfono que puso (mejora 2026-09-17, pedida por el dueño para frenar
 * pedidos falsos) — mismo patrón que {@link CadeteResetPassword}. Una vez verificado, se
 * emite un {@code token} de un solo uso que {@code SolicitudPedidoService.crear} exige
 * y consume para poder crear la solicitud.
 */
@Entity
@Table(name = "verificacion_telefono")
public class VerificacionTelefono {

    @Id
    private String id;

    @Column(nullable = false)
    private String telefono;

    @Column(nullable = false, length = 6)
    private String codigo;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    @Column(nullable = false)
    private Instant expiraEn;

    @Column(nullable = false)
    private int intentos = 0;

    /** Null hasta que el código se valida bien. */
    private Instant verificadoEn;

    /** Token de un solo uso entregado tras verificar — lo consume SolicitudPedidoService.crear. */
    @Column(unique = true)
    private String token;

    private Instant tokenUsadoEn;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
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

    public int getIntentos() {
        return intentos;
    }

    public void setIntentos(int intentos) {
        this.intentos = intentos;
    }

    public Instant getVerificadoEn() {
        return verificadoEn;
    }

    public void setVerificadoEn(Instant verificadoEn) {
        this.verificadoEn = verificadoEn;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Instant getTokenUsadoEn() {
        return tokenUsadoEn;
    }

    public void setTokenUsadoEn(Instant tokenUsadoEn) {
        this.tokenUsadoEn = tokenUsadoEn;
    }
}
