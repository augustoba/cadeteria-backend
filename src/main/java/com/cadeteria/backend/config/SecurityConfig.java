package com.cadeteria.backend.config;

import com.cadeteria.backend.common.ApiError;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthFilter jwtAuthFilter;
    private final RateLimitFilter rateLimitFilter;
    private final AppProperties props;

    public SecurityConfig(JwtAuthFilter jwtAuthFilter, RateLimitFilter rateLimitFilter, AppProperties props) {
        this.jwtAuthFilter = jwtAuthFilter;
        this.rateLimitFilter = rateLimitFilter;
        this.props = props;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsSource()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(
                                "/api/auth/**",
                                "/api/publico/**",
                                "/ws/**",
                                "/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**",
                                "/error"
                        ).permitAll()
                        // Rutas de plata/config sensible/seguridad — cada una exige su propio permiso
                        // (roles configurables, mejora 2026-09-16 — antes era un solo bit "DUENO" fijo,
                        // ver RolService/RolSeeder). Tienen que ir ANTES del matcher general de
                        // /api/admin/** para que Spring Security las evalúe primero.
                        .requestMatchers("/api/admin/configuracion/**").hasAuthority("PERM_configuracion")
                        .requestMatchers("/api/admin/metricas/**").hasAuthority("PERM_metricas")
                        .requestMatchers("/api/admin/pagos/**", "/api/admin/cadetes/*/pagos/**").hasAuthority("PERM_pagos")
                        .requestMatchers("/api/admin/seguridad/**").hasAuthority("PERM_seguridad")
                        .requestMatchers("/api/admin/usuarios/**").hasAuthority("PERM_usuarios")
                        .requestMatchers("/api/admin/whatsapp/**").hasAuthority("PERM_whatsapp")
                        // Ver roles (para el selector de "Usuarios") es de cualquier admin; crear/editar/borrar exige el permiso "roles".
                        .requestMatchers(HttpMethod.POST, "/api/admin/roles/**").hasAuthority("PERM_roles")
                        .requestMatchers(HttpMethod.PUT, "/api/admin/roles/**").hasAuthority("PERM_roles")
                        .requestMatchers(HttpMethod.DELETE, "/api/admin/roles/**").hasAuthority("PERM_roles")
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        .requestMatchers("/api/cadetes/me/**").hasRole("CADETE")
                        .requestMatchers("/api/pedidos/me/**").hasRole("CADETE")
                        .anyRequest().authenticated()
                )
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> writeError(res, mapper, HttpStatus.UNAUTHORIZED, "No autenticado"))
                        .accessDeniedHandler((req, res, e) -> writeError(res, mapper, HttpStatus.FORBIDDEN, "Acceso denegado"))
                )
                .addFilterBefore(rateLimitFilter, JwtAuthFilter.class)
                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    private void writeError(jakarta.servlet.http.HttpServletResponse res, ObjectMapper mapper,
                             HttpStatus status, String message) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType("application/json");
        mapper.writeValue(res.getWriter(),
                ApiError.of(status.value(), status.getReasonPhrase(), message, null));
    }

    private CorsConfigurationSource corsSource() {
        CorsConfiguration cfg = new CorsConfiguration();
        cfg.setAllowedOrigins(props.getCors().getAllowedOrigins());
        cfg.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(List.of("*"));
        cfg.setExposedHeaders(List.of("Location"));
        cfg.setAllowCredentials(true);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
