package com.cadeteria.backend.controller;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.dto.AvisoEnCaminoDtos.AvisoEnCaminoRequest;
import com.cadeteria.backend.dto.AvisoEnCaminoDtos.AvisoEnCaminoResponse;
import com.cadeteria.backend.dto.AvisoEnCaminoDtos.VistaPreviaRequest;
import com.cadeteria.backend.dto.AvisoEnCaminoDtos.VistaPreviaResponse;
import com.cadeteria.backend.service.ConfiguracionService;
import com.cadeteria.backend.service.PedidoService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.time.Instant;

/**
 * Aviso "en camino" que el admin le manda al cliente por WhatsApp (3n, 2026-09-28): se edita desde
 * Configuración (queda bajo /api/admin/configuracion, así que exige el permiso "configuracion"), con
 * vista previa sobre un pedido real y registro de quién lo editó por última vez.
 */
@RestController
@RequestMapping("/api/admin/configuracion/aviso-en-camino")
public class AvisoEnCaminoController {

    private static final String CLAVE = PedidoService.CLAVE_WHATSAPP_EN_CAMINO;

    private final ConfiguracionService configuracion;
    private final PedidoService pedidoService;

    public AvisoEnCaminoController(ConfiguracionService configuracion, PedidoService pedidoService) {
        this.configuracion = configuracion;
        this.pedidoService = pedidoService;
    }

    @GetMapping
    public AvisoEnCaminoResponse get() {
        String guardado = configuracion.getString(CLAVE, "");
        String editado = configuracion.getString(CLAVE + ConfiguracionService.SUFIJO_EDITADO, "");
        String editadoPor = null;
        Instant editadoEn = null;
        int sep = editado.lastIndexOf('|');
        if (sep >= 0) {
            editadoPor = editado.substring(0, sep);
            try {
                editadoEn = Instant.parse(editado.substring(sep + 1));
            } catch (RuntimeException ignorada) {
                // Valor tocado a mano en la base: se muestra sin fecha.
            }
        }
        return new AvisoEnCaminoResponse(guardado.isBlank() ? PedidoService.WHATSAPP_EN_CAMINO_DEFAULT : guardado,
                PedidoService.WHATSAPP_EN_CAMINO_DEFAULT, !guardado.isBlank(), editadoPor, editadoEn);
    }

    /** Guardar vacío (o igual al original) vuelve al texto del código. */
    @PutMapping
    public AvisoEnCaminoResponse set(@Valid @RequestBody AvisoEnCaminoRequest req, Principal principal) {
        String texto = req.texto().trim();
        if (texto.equals(PedidoService.WHATSAPP_EN_CAMINO_DEFAULT)) texto = "";
        if (!texto.isEmpty() && !texto.contains("{link}")) {
            throw new BadRequestException("El aviso tiene que tener {link}: sin el link el cliente no puede seguir el envío.");
        }
        configuracion.setConAutor(CLAVE, texto, principal == null ? null : principal.getName());
        return get();
    }

    @PostMapping("/vista-previa")
    public ResponseEntity<VistaPreviaResponse> vistaPrevia(@Valid @RequestBody VistaPreviaRequest req) {
        VistaPreviaResponse r = pedidoService.vistaPreviaAvisoEnCamino(req.texto());
        return r == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(r);
    }
}
