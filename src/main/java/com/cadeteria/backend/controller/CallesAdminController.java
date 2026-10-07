package com.cadeteria.backend.controller;

import com.cadeteria.backend.model.CalleUnion;
import com.cadeteria.backend.service.CallesARevisarService;
import com.cadeteria.backend.service.UnionCallesService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;
import java.util.Map;

/**
 * Calles que la base propia tiene con dos nombres (2026-10-03) y calles a revisar (2026-10-07).
 * Bajo {@code /api/admin/configuracion}, así que exige el permiso "configuracion" (ver SecurityConfig).
 */
@RestController
@RequestMapping("/api/admin/configuracion/calles")
public class CallesAdminController {

    public record LinkRequest(@NotBlank String link) {}

    public record DecisionRequest(@NotBlank String decision) {}

    private final UnionCallesService service;
    private final CallesARevisarService aRevisar;

    public CallesAdminController(UnionCallesService service, CallesARevisarService aRevisar) {
        this.service = service;
        this.aRevisar = aRevisar;
    }

    /** Qué pares se unirían, sin tocar nada. */
    @GetMapping("/duplicadas")
    public List<UnionCallesService.Union> duplicadas() {
        return service.duplicadas(false);
    }

    @PostMapping("/unir-duplicadas")
    public List<UnionCallesService.Union> unirDuplicadas() {
        return service.duplicadas(true);
    }

    @GetMapping("/uniones")
    public List<CalleUnion> uniones() {
        return service.historial();
    }

    /** Las dudas pendientes, primero las de las cuadras más usadas. */
    @GetMapping("/a-revisar")
    public List<CallesARevisarService.DudaVista> aRevisar() {
        return aRevisar.pendientes();
    }

    /** Repasa toda la base ahora (también corre sola todas las noches). */
    @PostMapping("/a-revisar/actualizar")
    public Map<String, Integer> actualizar() {
        return Map.of("nuevas", aRevisar.actualizar());
    }

    /** Resuelve una duda con el link de Google Maps de esa dirección. */
    @PostMapping("/a-revisar/{id}/link")
    public CallesARevisarService.Resultado conLink(@PathVariable String id, @Valid @RequestBody LinkRequest req, Principal principal) {
        return aRevisar.resolverConLink(id, req.link(), principal == null ? null : principal.getName());
    }

    /** Lo que elige la persona: MISMA, DISTINTAS, ESTA_BIEN o NO_EXISTE. */
    @PostMapping("/a-revisar/{id}/marcar")
    public CallesARevisarService.Resultado marcar(@PathVariable String id, @Valid @RequestBody DecisionRequest req, Principal principal) {
        return aRevisar.marcar(id, req.decision(), principal == null ? null : principal.getName());
    }
}
