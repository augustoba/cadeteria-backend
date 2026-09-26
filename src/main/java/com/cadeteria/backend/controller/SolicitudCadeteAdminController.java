package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.SolicitudCadeteDtos.AprobarRequest;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.AprobarResponse;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.GenerarLinkResponse;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.PedirCorreccionRequest;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.ReenviarLinkResponse;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.RechazarRequest;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.SolicitudResponse;
import com.cadeteria.backend.model.SolicitudCadete;
import com.cadeteria.backend.service.SolicitudCadeteService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Alta de cadete por link propio de un solo uso — panel del admin (ronda 7). */
@RestController
@RequestMapping("/api/admin/solicitudes-cadete")
public class SolicitudCadeteAdminController {

    private final SolicitudCadeteService service;

    public SolicitudCadeteAdminController(SolicitudCadeteService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<GenerarLinkResponse> generarLink() {
        SolicitudCadete s = service.generarLink();
        return ResponseEntity.status(201).body(new GenerarLinkResponse(s.getToken(), service.urlDe(s)));
    }

    @GetMapping
    public List<SolicitudResponse> listar(@RequestParam(required = false) String estado) {
        return service.listar(estado).stream().map(s -> SolicitudResponse.from(s, service.cadeteExistenteDe(s))).toList();
    }

    @GetMapping("/{id}")
    public SolicitudResponse get(@PathVariable String id) {
        SolicitudCadete s = service.get(id);
        return SolicitudResponse.from(s, service.cadeteExistenteDe(s));
    }

    @PostMapping("/{id}/aprobar")
    public AprobarResponse aprobar(@PathVariable String id, @Valid @RequestBody AprobarRequest req,
                                   org.springframework.security.core.Authentication auth) {
        var resultado = service.aprobar(id, req.username(), req.modalidadPago(), auth == null ? null : auth.getName());
        return new AprobarResponse(SolicitudResponse.from(service.get(id)), resultado.cadete().getUsername(), resultado.passwordTemporal());
    }

    /** Devuelve la solicitud al postulante con lo que tiene que corregir (le llega por mail). */
    @PostMapping("/{id}/pedir-correccion")
    public SolicitudResponse pedirCorreccion(@PathVariable String id, @RequestBody PedirCorreccionRequest req) {
        return SolicitudResponse.from(service.pedirCorreccion(id, req.observaciones()));
    }

    /** Renueva el vencimiento del link (y reenvía el mail si estaba A_CORREGIR). */
    @PostMapping("/{id}/reenviar-link")
    public ReenviarLinkResponse reenviarLink(@PathVariable String id) {
        var r = service.reenviarLink(id);
        return new ReenviarLinkResponse(r.url(), r.solicitud().getExpiraEn(), r.mailEnviado());
    }

    @PostMapping("/{id}/rechazar")
    public SolicitudResponse rechazar(@PathVariable String id, @RequestBody(required = false) RechazarRequest req) {
        return SolicitudResponse.from(service.rechazar(id, req == null ? null : req.motivo()));
    }
}
