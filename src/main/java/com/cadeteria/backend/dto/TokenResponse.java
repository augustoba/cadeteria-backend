package com.cadeteria.backend.dto;

import java.time.Instant;

public record TokenResponse(String token, String tokenType, String tipo, Instant expiresAt) {
    public static TokenResponse bearer(String token, String tipo, Instant expiresAt) {
        return new TokenResponse(token, "Bearer", tipo, expiresAt);
    }
}
