package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.ActualizacionCadeteRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteActualizacion;
import com.cadeteria.backend.model.CadeteActualizacionCampo;
import com.cadeteria.backend.repository.CadeteActualizacionCampoRepository;
import com.cadeteria.backend.repository.CadeteActualizacionRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CadeteActualizacionService {

    private final CadeteActualizacionRepository loteRepo;
    private final CadeteActualizacionCampoRepository campoRepo;
    private final CadeteRepository cadeteRepo;

    public CadeteActualizacionService(CadeteActualizacionRepository loteRepo, CadeteActualizacionCampoRepository campoRepo,
                                       CadeteRepository cadeteRepo) {
        this.loteRepo = loteRepo;
        this.campoRepo = campoRepo;
        this.cadeteRepo = cadeteRepo;
    }

    private Cadete getCadete(String username) {
        return cadeteRepo.findByUsername(username).orElseThrow(() -> ResourceNotFoundException.of("Cadete", username));
    }

    private record CampoCandidato(String campo, String valorAnterior, String valorPropuesto) {}

    /** El cadete propone cambios — cada campo queda PENDIENTE hasta que el admin lo resuelva. */
    public CadeteActualizacion crear(String username, ActualizacionCadeteRequest req) {
        Cadete cadete = getCadete(username);
        if (campoRepo.existePendientePara(cadete.getId(), "PENDIENTE")) {
            throw new BadRequestException("Ya tenés una actualización esperando revisión — esperá a que se resuelva antes de mandar otra.");
        }

        List<CampoCandidato> candidatos = new ArrayList<>();
        agregarSiCambia(candidatos, "FOTO_PERFIL", req.fotoUrl(), cadete.getFotoUrl());
        agregarSiCambia(candidatos, "FOTO_VEHICULO", req.fotoVehiculoUrl(), cadete.getFotoVehiculoUrl());
        agregarSiCambia(candidatos, "FOTO_TARJETA_VERDE", req.fotoTarjetaVerdeUrl(), cadete.getFotoTarjetaVerdeUrl());
        agregarSiCambia(candidatos, "FOTO_TARJETA_VERDE_DORSO", req.fotoTarjetaVerdeDorsoUrl(), cadete.getFotoTarjetaVerdeDorsoUrl());
        agregarSiCambia(candidatos, "VEHICULO_MARCA", req.vehiculoMarca(), cadete.getVehiculoMarca());
        agregarSiCambia(candidatos, "VEHICULO_MODELO", req.vehiculoModelo(), cadete.getVehiculoModelo());
        agregarSiCambia(candidatos, "VEHICULO_COLOR", req.vehiculoColor(), cadete.getVehiculoColor());
        agregarSiCambia(candidatos, "VEHICULO_PATENTE", req.vehiculoPatente(), cadete.getVehiculoPatente());
        agregarSiCambia(candidatos, "VEHICULO_ANIO",
                req.vehiculoAnio() == null ? null : req.vehiculoAnio().toString(),
                cadete.getVehiculoAnio() == null ? null : cadete.getVehiculoAnio().toString());

        if (candidatos.isEmpty()) {
            throw new BadRequestException("No mandaste ningún cambio.");
        }

        CadeteActualizacion lote = new CadeteActualizacion();
        lote.setId(UUID.randomUUID().toString());
        lote.setCadete(cadete);
        lote = loteRepo.save(lote);

        for (CampoCandidato c : candidatos) {
            CadeteActualizacionCampo campo = new CadeteActualizacionCampo();
            campo.setId(UUID.randomUUID().toString());
            campo.setActualizacion(lote);
            campo.setCampo(c.campo());
            campo.setValorAnterior(c.valorAnterior());
            campo.setValorPropuesto(c.valorPropuesto());
            campo.setEstado("PENDIENTE");
            campoRepo.save(campo);
        }
        return lote;
    }

    private static void agregarSiCambia(List<CampoCandidato> candidatos, String campo, String propuesto, String actual) {
        if (propuesto == null || propuesto.isBlank()) return;
        String propuestoTrim = propuesto.trim();
        if (propuestoTrim.equals(actual)) return;
        candidatos.add(new CampoCandidato(campo, actual, propuestoTrim));
    }

    @Transactional(readOnly = true)
    public List<CadeteActualizacion> misActualizaciones(String username) {
        Cadete cadete = getCadete(username);
        return loteRepo.findByCadeteIdOrderByCreadoEnDesc(cadete.getId());
    }

    @Transactional(readOnly = true)
    public List<CadeteActualizacionCampo> camposDe(String actualizacionId) {
        return campoRepo.findByActualizacionId(actualizacionId);
    }
}
