package com.cadeteria.backend.controller;

import com.cadeteria.backend.model.ApkDescarga;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.service.ApkService;
import com.cadeteria.backend.service.CelularCadeteService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * Celular del cadete, en su ficha (2026-09-29): el vinculado, los permisos que le faltan a la app y el
 * link de descarga de la APK. Verlo es de cualquier admin; "Habilitar nuevo celular" y generar un link
 * exigen el permiso celular_cadete (SecurityConfig, POST /api/admin/cadetes/{id}/celular/**).
 */
@RestController
public class CelularCadeteAdminController {

    public record CelularResponse(String modelo, Instant vinculadoEn, int intentosOtroCelular,
                                  Instant ultimoIntentoOtroCelularEn, String ultimoIntentoOtroCelularModelo,
                                  /** "NOTIFICACIONES,BATERIA"; vacío = todos; null = la app nunca lo informó. */
                                  String permisosFaltantes, Instant permisosInformadosEn,
                                  /** El último link de descarga de la APK generado para él, y si lo usó. */
                                  Instant ultimoLinkApkEn, Instant ultimoLinkApkDescargadoEn) {
        static CelularResponse de(Cadete c, ApkDescarga link) {
            return new CelularResponse(c.getCelularId() == null ? null : c.getCelularModelo(), c.getCelularVinculadoEn(),
                    c.getIntentosOtroCelular(), c.getUltimoIntentoOtroCelularEn(), c.getUltimoIntentoOtroCelularModelo(),
                    c.getPermisosFaltantes(), c.getPermisosInformadosEn(),
                    link == null ? null : link.getCreadoEn(), link == null ? null : link.getPrimeraDescargaEn());
        }
    }

    public record PermisosRequest(String faltantes) {}

    private final CelularCadeteService service;
    private final ApkService apkService;

    public CelularCadeteAdminController(CelularCadeteService service, ApkService apkService) {
        this.service = service;
        this.apkService = apkService;
    }

    @GetMapping("/api/admin/cadetes/{id}/celular")
    public CelularResponse ver(@PathVariable String id) {
        return respuesta(service.buscar(id));
    }

    @PostMapping("/api/admin/cadetes/{id}/celular/habilitar-nuevo")
    public CelularResponse habilitarNuevo(@PathVariable String id) {
        return respuesta(service.habilitarNuevoCelular(id));
    }

    /** Link de un solo uso para mandarle por WhatsApp (si no le llegó el mail o cambió de celular). */
    @PostMapping("/api/admin/cadetes/{id}/celular/link-apk")
    public ApkService.Link linkApk(@PathVariable String id) {
        service.buscar(id);
        ApkService.Link link = apkService.crearLink(id);
        if (link == null) {
            throw new com.cadeteria.backend.common.BadRequestException(
                    "Todavía no se subió la APK: cargala en Configuración → App de cadetes.");
        }
        return link;
    }

    /** La app informa qué permisos le faltan (2026-09-29). */
    @PostMapping("/api/cadetes/me/permisos")
    public void informarPermisos(@RequestBody PermisosRequest req, Authentication auth) {
        service.informarPermisos(auth.getName(), req.faltantes());
    }

    private CelularResponse respuesta(Cadete c) {
        return CelularResponse.de(c, apkService.ultimoLinkDe(c.getId()).orElse(null));
    }
}
