package com.cadeteria.backend.controller;

import com.cadeteria.backend.service.ApkService;
import com.cadeteria.backend.service.CelularCadeteService;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Path;

/**
 * APK para los cadetes (2026-09-29, ver ApkService): subirla desde Configuración, el link de un solo uso
 * (página con botón "Descargar" y la descarga) y el link que pide la app cuando su versión quedó vieja.
 */
@RestController
public class ApkController {

    private final ApkService service;
    private final CelularCadeteService celularCadeteService;

    public ApkController(ApkService service, CelularCadeteService celularCadeteService) {
        this.service = service;
        this.celularCadeteService = celularCadeteService;
    }

    // --- Panel (Configuración → App de cadetes; /api/admin/configuracion/** exige el permiso configuracion) ---

    @GetMapping("/api/admin/configuracion/apk")
    public ApkService.Info info() {
        return service.info();
    }

    @PostMapping(value = "/api/admin/configuracion/apk", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApkService.Info subir(@RequestParam("archivo") MultipartFile archivo,
                                 @RequestParam(value = "version", required = false) Integer version,
                                 @RequestParam(value = "obligar", defaultValue = "false") boolean obligar) {
        return service.guardar(archivo, version, obligar);
    }

    // --- App del cadete ---

    /** "Tu versión es vieja → Descargar la nueva": link de un solo uso para el cadete logueado. */
    @PostMapping("/api/cadetes/me/apk/link")
    public ApkService.Link linkParaMi(Authentication auth) {
        ApkService.Link link = service.crearLink(celularCadeteService.buscarPorUsuario(auth.getName()).getId());
        return link == null ? new ApkService.Link(null, null) : link;
    }

    // --- Público (el link del mail) ---

    /**
     * Página con un botón "Descargar": los mails (Gmail) y los antivirus abren los links para revisarlos, y
     * si el link bajara la APK directo se gastaría antes de que llegue el cadete.
     */
    @GetMapping(value = "/api/publico/apk/{token}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> pagina(@PathVariable String token) {
        if (!service.linkValido(token)) {
            return ResponseEntity.status(HttpStatus.GONE).contentType(MediaType.TEXT_HTML)
                    .body(html("Link vencido", "<p>" + new ApkService.LinkNoValidoException().getMessage() + "</p>"));
        }
        return ResponseEntity.ok().contentType(MediaType.TEXT_HTML).body(html("Descargar la app de Cadem",
                "<p>Tocá el botón para bajar la app. Cuando termine, abrí el archivo y tocá <b>Instalar</b> "
                        + "(si el celular pregunta, permití instalar apps de este origen).</p>"
                        + "<p><a class=\"boton\" href=\"" + token + "/descargar\">⬇ Descargar la app</a></p>"
                        + "<p class=\"nota\">Este link es personal y sirve una sola vez.</p>"));
    }

    @GetMapping("/api/publico/apk/{token}/descargar")
    public ResponseEntity<?> descargar(@PathVariable String token) {
        try {
            Path apk = service.descargar(token);
            Resource archivo = new FileSystemResource(apk);
            return ResponseEntity.ok()
                    .contentType(MediaType.parseMediaType("application/vnd.android.package-archive"))
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"cadem-cadetes.apk\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .contentLength(archivo.contentLength())
                    .body(archivo);
        } catch (ApkService.LinkNoValidoException e) {
            return ResponseEntity.status(HttpStatus.GONE).contentType(MediaType.TEXT_HTML)
                    .body(html("Link vencido", "<p>" + e.getMessage() + "</p>"));
        } catch (java.io.IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }
    }

    private static String html(String titulo, String cuerpo) {
        return "<!doctype html><html lang=\"es\"><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\"><title>" + titulo + "</title>"
                + "<style>body{font-family:system-ui,sans-serif;background:#2b2b2b;color:#fff;margin:0;padding:32px 20px;"
                + "text-align:center}h1{color:#fb6d01}p{line-height:1.5;max-width:420px;margin:12px auto}"
                + ".boton{display:inline-block;background:#fb6d01;color:#fff;padding:16px 28px;border-radius:14px;"
                + "font-weight:bold;text-decoration:none;font-size:18px}.nota{color:#9e9e9e;font-size:14px}</style>"
                + "</head><body><h1>" + titulo + "</h1>" + cuerpo + "</body></html>";
    }
}
