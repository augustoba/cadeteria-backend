package com.cadeteria.backend.config;

import com.cadeteria.backend.model.Admin;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.repository.AdminRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.service.RolService;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Lee `Authorization: Bearer <jwt>` y autentica contra la tabla admin o cadete
 * segun el claim "tipo" del token (ver JwtService). Authorities: ROLE_ADMIN o
 * ROLE_CADETE.
 */
@Component
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final AdminRepository admins;
    private final CadeteRepository cadetes;
    private final RolService rolService;

    public JwtAuthFilter(JwtService jwtService, AdminRepository admins, CadeteRepository cadetes, RolService rolService) {
        this.jwtService = jwtService;
        this.admins = admins;
        this.cadetes = cadetes;
        this.rolService = rolService;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                     @NonNull HttpServletResponse response,
                                     @NonNull FilterChain chain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            Claims claims = jwtService.validate(header.substring(7));
            if (claims != null) {
                String username = claims.getSubject();
                String tipo = claims.get("tipo", String.class);
                String sid = claims.get("sid", String.class);
                Admin adminValido = JwtService.TIPO_ADMIN.equals(tipo)
                        ? admins.findByUsername(username).filter(Admin::isEnabled)
                                .filter(a -> sid != null && sid.equals(a.getSessionToken()))
                                .orElse(null)
                        : null;
                boolean valido = adminValido != null
                        || (JwtService.TIPO_CADETE.equals(tipo)
                                && cadetes.findByUsername(username).filter(Cadete::isActivo)
                                        .filter(c -> sid != null && sid.equals(c.getSessionToken()))
                                        .isPresent());
                if (valido) {
                    // Los permisos de ADMIN se revalidan contra la base en cada request (no contra
                    // el claim del token) para que un cambio de rol/permiso a mitad de sesión
                    // aplique al toque, no recién cuando expire el JWT viejo.
                    List<SimpleGrantedAuthority> authorities = adminValido != null
                            ? authoritiesParaAdmin(adminValido)
                            : List.of(new SimpleGrantedAuthority("ROLE_" + tipo));
                    var auth = new UsernamePasswordAuthenticationToken(username, null, authorities);
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                }
            }
        }
        chain.doFilter(request, response);
    }

    /** ROLE_ADMIN siempre (distingue de ROLE_CADETE) + un PERM_x por cada permiso que le da su rol (ver RolService). */
    private List<SimpleGrantedAuthority> authoritiesParaAdmin(Admin admin) {
        List<SimpleGrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("ROLE_ADMIN"));
        rolService.permisosEfectivos(admin.getRol())
                .forEach(permiso -> authorities.add(new SimpleGrantedAuthority("PERM_" + permiso)));
        return authorities;
    }
}
