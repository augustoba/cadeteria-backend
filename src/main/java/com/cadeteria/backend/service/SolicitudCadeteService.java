package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.CadeteDtos.CadeteRequest;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.SolicitudFormRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.SolicitudCadete;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.SolicitudCadeteRepository;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

/**
 * Alta de cadete por formulario propio con link de un solo uso (ronda 7): el admin
 * genera el link, se lo pasa al postulante fuera del sistema (WhatsApp, etc.), este lo
 * completa una sola vez, y el admin revisa/aprueba o rechaza desde el panel. Al aprobar
 * se crea el Cadete real (reusando CadeteService) con una contraseña temporal que se le
 * manda por mail.
 */
@Service
@Transactional
public class SolicitudCadeteService {

    private static final String ALFABETO_PASSWORD = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private final SecureRandom random = new SecureRandom();

    private final SolicitudCadeteRepository repo;
    private final TipoVehiculoRepository tipoVehiculoRepo;
    private final CadeteRepository cadeteRepo;
    private final CadeteService cadeteService;
    private final EmailService emailService;
    private final AppProperties props;

    public SolicitudCadeteService(SolicitudCadeteRepository repo, TipoVehiculoRepository tipoVehiculoRepo,
                                   CadeteRepository cadeteRepo, CadeteService cadeteService,
                                   EmailService emailService, AppProperties props) {
        this.repo = repo;
        this.tipoVehiculoRepo = tipoVehiculoRepo;
        this.cadeteRepo = cadeteRepo;
        this.cadeteService = cadeteService;
        this.emailService = emailService;
        this.props = props;
    }

    /** El admin genera un link nuevo, válido 7 días, para pasarle al postulante. */
    public SolicitudCadete generarLink() {
        SolicitudCadete s = new SolicitudCadete();
        s.setId(UUID.randomUUID().toString());
        s.setToken(UUID.randomUUID().toString());
        s.setEstado("PENDIENTE");
        s.setCreadoEn(Instant.now());
        s.setExpiraEn(Instant.now().plus(7, ChronoUnit.DAYS));
        return repo.save(s);
    }

    public String urlDe(SolicitudCadete s) {
        return props.getFrontBaseUrl() + "/registro-cadete/" + s.getToken();
    }

    /** Valida el token antes de mostrar el formulario público — no expone nada más. */
    @Transactional(readOnly = true)
    public SolicitudCadete validarToken(String token) {
        SolicitudCadete s = repo.findByToken(token)
                .orElseThrow(() -> new BadRequestException("Este link no es válido."));
        if (!"PENDIENTE".equals(s.getEstado())) {
            throw new BadRequestException("Este link ya fue usado.");
        }
        if (s.getExpiraEn().isBefore(Instant.now())) {
            throw new BadRequestException("Este link venció. Pedí uno nuevo.");
        }
        return s;
    }

    /** El postulante envía el formulario — de un solo uso, queda EN_REVISION para el admin. */
    public SolicitudCadete enviarFormulario(String token, SolicitudFormRequest req) {
        SolicitudCadete s = validarToken(token);
        if (cadeteRepo.findByUsername(req.usernamePropuesto().trim()).isPresent()) {
            throw new BadRequestException("Ese usuario ya está en uso, elegí otro.");
        }
        TipoVehiculo tipo = tipoVehiculoRepo.findById(req.tipoVehiculoId())
                .orElseThrow(() -> ResourceNotFoundException.of("Tipo de vehiculo", req.tipoVehiculoId()));

        s.setNombre(req.nombre().trim());
        s.setApellido(req.apellido().trim());
        s.setDni(req.dni().trim());
        s.setTelefono(req.telefono().trim());
        s.setEmail(req.email().trim());
        s.setTipoVehiculo(tipo);
        s.setVehiculoColor(blankToNull(req.vehiculoColor()));
        s.setVehiculoPatente(blankToNull(req.vehiculoPatente()));
        s.setVehiculoMarca(blankToNull(req.vehiculoMarca()));
        s.setVehiculoModelo(blankToNull(req.vehiculoModelo()));
        s.setFotoUrl(blankToNull(req.fotoUrl()));
        s.setFotoVehiculoUrl(blankToNull(req.fotoVehiculoUrl()));
        s.setFotoCarnetUrl(blankToNull(req.fotoCarnetUrl()));
        s.setFotoCarnetDorsoUrl(blankToNull(req.fotoCarnetDorsoUrl()));
        s.setFotoTarjetaVerdeUrl(blankToNull(req.fotoTarjetaVerdeUrl()));
        s.setFotoTarjetaVerdeDorsoUrl(blankToNull(req.fotoTarjetaVerdeDorsoUrl()));
        s.setUsernamePropuesto(req.usernamePropuesto().trim());
        s.setEstado("EN_REVISION");
        s.setEnviadaEn(Instant.now());
        return repo.save(s);
    }

    @Transactional(readOnly = true)
    public List<SolicitudCadete> listar(String estado) {
        if (estado == null || estado.isBlank()) return repo.findAllByOrderByCreadoEnDesc();
        return repo.findByEstadoOrderByCreadoEnDesc(estado.toUpperCase());
    }

    @Transactional(readOnly = true)
    public SolicitudCadete get(String id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Solicitud de cadete", id));
    }

    /** Resultado de aprobar: el cadete creado y la contraseña temporal — el admin la ve por si el mail no llegó. */
    public record AprobacionResultado(Cadete cadete, String passwordTemporal) {}

    /**
     * Crea el Cadete real con contraseña temporal, y le manda el mail con sus datos de acceso.
     * modalidadPago la elige el admin acá al aprobar (antes quedaba fija en "SEMANAL" sin poder elegirla).
     */
    public AprobacionResultado aprobar(String id, String username, String modalidadPago) {
        SolicitudCadete s = get(id);
        if (!"EN_REVISION".equals(s.getEstado())) {
            throw new BadRequestException("Esta solicitud no está pendiente de revisión.");
        }
        String passwordTemporal = generarPasswordTemporal();
        CadeteRequest cadeteReq = new CadeteRequest(
                s.getNombre(), s.getApellido(), s.getDni(), s.getTelefono(), s.getEmail(), s.getFotoUrl(),
                s.getTipoVehiculo().getId(), s.getVehiculoColor(), s.getVehiculoPatente(),
                s.getVehiculoMarca(), s.getVehiculoModelo(), null, s.getFotoVehiculoUrl(), s.getFotoCarnetUrl(),
                s.getFotoCarnetDorsoUrl(), s.getFotoTarjetaVerdeUrl(), s.getFotoTarjetaVerdeDorsoUrl(),
                username.trim(), passwordTemporal,
                null, null, null, null, null, null,
                modalidadPago == null || modalidadPago.isBlank() ? "SEMANAL" : modalidadPago, null);
        Cadete cadete = cadeteService.create(cadeteReq);
        cadeteService.marcarPasswordTemporal(cadete.getId(), Instant.now().plus(10, ChronoUnit.MINUTES));

        s.setEstado("APROBADA");
        s.setCadeteCreadoId(cadete.getId());
        repo.save(s);

        emailService.enviar(s.getEmail(), "Ya estás de alta en la cadetería",
                "Hola " + s.getNombre() + "!\n\n" +
                        "Te dimos de alta como cadete. Estos son tus datos para entrar a la app:\n\n" +
                        "Usuario: " + username.trim() + "\n" +
                        "Contraseña temporal: " + passwordTemporal + "\n\n" +
                        "Tenés 10 minutos para entrar con esta contraseña — si se vence, pedile a la cadetería que te la reenvíe.");
        return new AprobacionResultado(cadete, passwordTemporal);
    }

    public SolicitudCadete rechazar(String id, String motivo) {
        SolicitudCadete s = get(id);
        if (!"EN_REVISION".equals(s.getEstado())) {
            throw new BadRequestException("Esta solicitud no está pendiente de revisión.");
        }
        s.setEstado("RECHAZADA");
        s.setMotivoRechazo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        return repo.save(s);
    }

    private String generarPasswordTemporal() {
        StringBuilder sb = new StringBuilder(10);
        for (int i = 0; i < 10; i++) {
            sb.append(ALFABETO_PASSWORD.charAt(random.nextInt(ALFABETO_PASSWORD.length())));
        }
        return sb.toString();
    }

    private static String blankToNull(String v) {
        return (v == null || v.isBlank()) ? null : v.trim();
    }
}
