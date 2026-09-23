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

    @Transactional(readOnly = true)
    public List<CadeteActualizacionCampo> listarPendientes() {
        return campoRepo.findByEstadoOrderByActualizacionCreadoEnDesc("PENDIENTE");
    }

    /** El admin aprueba un campo puntual — se aplica al Cadete real al toque. */
    public CadeteActualizacionCampo aprobarCampo(String campoId, String adminUsername) {
        CadeteActualizacionCampo campo = getCampo(campoId);
        exigirPendiente(campo);
        aplicarValor(campo.getActualizacion().getCadete(), campo);
        campo.setEstado("APROBADO");
        campo.setResueltoEn(Instant.now());
        campo.setResueltoPorUsername(adminUsername);
        return campoRepo.save(campo);
    }

    /** El admin rechaza un campo puntual — el Cadete real no se toca. */
    public CadeteActualizacionCampo rechazarCampo(String campoId, String motivo, String adminUsername) {
        CadeteActualizacionCampo campo = getCampo(campoId);
        exigirPendiente(campo);
        campo.setEstado("RECHAZADO");
        campo.setMotivoRechazo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        campo.setResueltoEn(Instant.now());
        campo.setResueltoPorUsername(adminUsername);
        return campoRepo.save(campo);
    }

    private CadeteActualizacionCampo getCampo(String id) {
        return campoRepo.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Actualización de cadete", id));
    }

    private void exigirPendiente(CadeteActualizacionCampo campo) {
        if (!"PENDIENTE".equals(campo.getEstado())) {
            throw new BadRequestException("Este campo ya fue resuelto.");
        }
    }

    private void aplicarValor(Cadete cadete, CadeteActualizacionCampo campo) {
        String valor = campo.getValorPropuesto();
        switch (campo.getCampo()) {
            case "FOTO_PERFIL" -> cadete.setFotoUrl(valor);
            case "FOTO_VEHICULO" -> cadete.setFotoVehiculoUrl(valor);
            case "FOTO_TARJETA_VERDE" -> cadete.setFotoTarjetaVerdeUrl(valor);
            case "FOTO_TARJETA_VERDE_DORSO" -> cadete.setFotoTarjetaVerdeDorsoUrl(valor);
            case "VEHICULO_MARCA" -> cadete.setVehiculoMarca(valor);
            case "VEHICULO_MODELO" -> cadete.setVehiculoModelo(valor);
            case "VEHICULO_COLOR" -> cadete.setVehiculoColor(valor);
            case "VEHICULO_PATENTE" -> cadete.setVehiculoPatente(valor);
            case "VEHICULO_ANIO" -> cadete.setVehiculoAnio(valor == null ? null : Integer.valueOf(valor));
            default -> throw new IllegalStateException("Campo desconocido: " + campo.getCampo());
        }
        cadeteRepo.save(cadete);
    }
}
