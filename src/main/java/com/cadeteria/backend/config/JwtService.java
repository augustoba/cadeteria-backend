package com.cadeteria.backend.config;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;

/**
 * Genera y valida los JWT (HS256). El claim "tipo" (ADMIN o CADETE) le dice al
 * {@link JwtAuthFilter} en que tabla buscar al usuario — un mismo backend
 * autentica a los dos frentes (panel admin y app de cadetes).
 */
@Service
public class JwtService {

    public static final String TIPO_ADMIN = "ADMIN";
    public static final String TIPO_CADETE = "CADETE";

    private final SecretKey key;
    private final long expirationMinutes;

    public JwtService(AppProperties props) {
        byte[] secret = props.getJwt().getSecret().getBytes(StandardCharsets.UTF_8);
        if (secret.length < 32) {
            throw new IllegalStateException(
                    "app.jwt.secret debe tener al menos 32 caracteres (define JWT_SECRET).");
        }
        this.key = Keys.hmacShaKeyFor(secret);
        this.expirationMinutes = props.getJwt().getExpirationMinutes();
    }

    public TokenData generate(String username, String tipo) {
        return generate(username, tipo, null);
    }

    /**
     * sessionId (claim "sid"): usado solo para CADETE (spec: un solo dispositivo activo
     * por cuenta) — {@link JwtAuthFilter} lo compara contra Cadete.sessionToken en cada
     * request; si no matchean (se logueo desde otro celular despues), el token deja de
     * ser valido aunque la firma y la expiracion sigan siendo correctas.
     */
    public TokenData generate(String username, String tipo, String sessionId) {
        return generate(username, tipo, sessionId, null);
    }

    /**
     * rol (claim "rol"): solo para ADMIN ("DUENO"/"OPERADOR") — el front lo decodifica
     * para mostrar/ocultar pantallas, pero la autorización real de {@code /api/admin/**}
     * en {@link SecurityConfig} y {@link JwtAuthFilter} siempre revalida contra la base,
     * no confía ciegamente en este claim (un cambio de rol a mitad de sesión aplica en la
     * siguiente request, no hace falta esperar a que expire el token viejo).
     */
    public TokenData generate(String username, String tipo, String sessionId, String rol) {
        Instant now = Instant.now();
        Instant exp = now.plusSeconds(expirationMinutes * 60);
        var builder = Jwts.builder()
                .subject(username)
                .claim("tipo", tipo)
                .issuedAt(Date.from(now))
                .expiration(Date.from(exp));
        if (sessionId != null) {
            builder.claim("sid", sessionId);
        }
        if (rol != null) {
            builder.claim("rol", rol);
        }
        String token = builder.signWith(key).compact();
        return new TokenData(token, exp, tipo);
    }

    /** @return los claims si el token es valido; null si no. */
    public Claims validate(String token) {
        try {
            return Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        } catch (Exception e) {
            return null;
        }
    }

    public record TokenData(String token, Instant expiresAt, String tipo) {}
}
