package com.cadeteria.backend.config;

import com.cadeteria.backend.service.ConfiguracionService;
import com.cadeteria.backend.service.RateLimitService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * Límite general por IP para todo `/api/publico/**` (mejora 2026-09-17): sin login de
 * por medio, cualquiera puede martillar "/pedir", "/cotizar" o el buscador de
 * direcciones — esto no reemplaza el límite específico y más estricto del código de
 * verificación de teléfono (ver VerificacionTelefonoService), es una red de contención
 * más ancha para el resto de los endpoints públicos.
 */
@Component
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimitService rateLimitService;
    private final ConfiguracionService configuracionService;

    public RateLimitFilter(RateLimitService rateLimitService, ConfiguracionService configuracionService) {
        this.rateLimitService = rateLimitService;
        this.configuracionService = configuracionService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!request.getRequestURI().startsWith("/api/publico/")) {
            filterChain.doFilter(request, response);
            return;
        }
        int maxIntentos = configuracionService.getInt("rate_limit_publico_max", 60);
        int ventanaSeg = configuracionService.getInt("rate_limit_publico_ventana_seg", 60);
        String ip = obtenerIp(request);
        if (!rateLimitService.permitir("ip:" + ip, maxIntentos, Duration.ofSeconds(ventanaSeg))) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"error\":\"Demasiadas solicitudes — esperá un momento y probá de nuevo.\"}");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String obtenerIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) return xff.split(",")[0].trim();
        return request.getRemoteAddr();
    }
}
