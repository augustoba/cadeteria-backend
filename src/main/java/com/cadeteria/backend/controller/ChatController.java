package com.cadeteria.backend.controller;

import com.cadeteria.backend.common.ForbiddenException;
import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.dto.ChatDtos.MensajeRequest;
import com.cadeteria.backend.dto.ChatDtos.MensajeResponse;
import com.cadeteria.backend.service.CadeteService;
import com.cadeteria.backend.service.ChatService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Chat interno 1 a 1 admin-cadete (spec 5.5). Compartido entre los dos frentes. */
@RestController
@RequestMapping("/api/chat/{cadeteId}")
public class ChatController {

    private final ChatService service;
    private final CadeteService cadeteService;

    public ChatController(ChatService service, CadeteService cadeteService) {
        this.service = service;
        this.cadeteService = cadeteService;
    }

    @GetMapping
    public List<MensajeResponse> historial(@PathVariable String cadeteId, Authentication auth) {
        validarAcceso(cadeteId, auth);
        return service.historial(cadeteId).stream().map(MensajeResponse::from).toList();
    }

    @PostMapping("/mensajes")
    public MensajeResponse enviar(@PathVariable String cadeteId, @Valid @RequestBody MensajeRequest req,
                                   Authentication auth) {
        validarAcceso(cadeteId, auth);
        return MensajeResponse.from(service.enviar(cadeteId, autorDe(auth), req.texto(), req.audioUrl(), req.imagenUrl()));
    }

    @PatchMapping("/leido")
    public void marcarLeido(@PathVariable String cadeteId, Authentication auth) {
        validarAcceso(cadeteId, auth);
        service.marcarLeido(cadeteId, autorDe(auth));
    }

    private boolean esAdmin(Authentication auth) {
        return auth.getAuthorities().contains(new SimpleGrantedAuthority("ROLE_ADMIN"));
    }

    private String autorDe(Authentication auth) {
        return esAdmin(auth) ? "ADMIN" : "CADETE";
    }

    /** Un cadete solo puede ver/escribir su propia conversacion; el admin puede la de cualquiera. */
    private void validarAcceso(String cadeteId, Authentication auth) {
        if (esAdmin(auth)) return;
        String propioId = cadeteService.getByUsername(auth.getName()).getId();
        if (!propioId.equals(cadeteId)) {
            throw new ForbiddenException("No podés acceder al chat de otro cadete.");
        }
    }
}
