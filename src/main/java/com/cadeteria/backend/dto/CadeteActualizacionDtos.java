package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.CadeteActualizacion;
import com.cadeteria.backend.model.CadeteActualizacionCampo;

import java.time.Instant;
import java.util.List;

public final class CadeteActualizacionDtos {

    private CadeteActualizacionDtos() {}

    /** Todos los campos son opcionales — el cadete manda solo los que quiere cambiar. */
    public record ActualizacionCadeteRequest(
            String fotoUrl, String fotoVehiculoUrl,
            String fotoTarjetaVerdeUrl, String fotoTarjetaVerdeDorsoUrl,
            String vehiculoMarca, String vehiculoModelo, String vehiculoColor,
            String vehiculoPatente, Integer vehiculoAnio
    ) {}

    public record RechazarCampoRequest(String motivo) {}

    public record CampoResponse(
            String id, String campo, String valorAnterior, String valorPropuesto,
            String estado, String motivoRechazo, Instant resueltoEn, String resueltoPorUsername
    ) {
        public static CampoResponse from(CadeteActualizacionCampo c) {
            return new CampoResponse(c.getId(), c.getCampo(), c.getValorAnterior(), c.getValorPropuesto(),
                    c.getEstado(), c.getMotivoRechazo(), c.getResueltoEn(), c.getResueltoPorUsername());
        }
    }

    /** Un lote con sus campos — el "estado" del lote se deriva acá, no se persiste (spec 5.2). */
    public record ActualizacionResponse(
            String id, Instant creadoEn, String estado, List<CampoResponse> campos
    ) {
        public static ActualizacionResponse from(CadeteActualizacion a, List<CadeteActualizacionCampo> campos) {
            String estado = campos.stream().anyMatch(c -> "PENDIENTE".equals(c.getEstado())) ? "PENDIENTE"
                    : campos.stream().anyMatch(c -> "RECHAZADO".equals(c.getEstado())) ? "CON_RECHAZOS"
                    : "APROBADO";
            return new ActualizacionResponse(a.getId(), a.getCreadoEn(), estado,
                    campos.stream().map(CampoResponse::from).toList());
        }
    }

    /** Para la pantalla admin: el campo pendiente + de qué lote/cadete es. */
    public record CampoPendienteAdminResponse(
            String actualizacionId, CampoResponse campo,
            String cadeteId, String cadeteNombre, String cadeteApellido, String cadeteFotoUrl
    ) {}
}
