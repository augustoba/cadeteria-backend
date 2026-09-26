package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.CadeteDtos.CadeteRequest;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.CadeteExistenteResponse;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.CorreccionResponse;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.ObservacionResponse;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.SolicitudFormRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.SolicitudCadete;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.SolicitudCadeteRepository;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * Alta de cadete por formulario propio con link de un solo uso (ronda 7): el admin
 * genera el link, se lo pasa al postulante fuera del sistema (WhatsApp, etc.), este lo
 * completa una sola vez, y el admin revisa/aprueba o rechaza desde el panel. Al aprobar
 * se crea el Cadete real (reusando CadeteService) con una contraseña temporal que se le
 * manda por mail.
 * <p>
 * Corrección (2026-09-25): en vez de rechazar todo, el admin marca qué dato o foto está mal y
 * por qué ({@link #pedirCorreccion}); la solicitud pasa a A_CORREGIR, al postulante le llega un
 * mail con la lista y el MISMO link, que ahora abre el formulario precargado. Las fotos marcadas
 * tiene que subirlas de nuevo. "Reenviar link" ({@link #reenviarLink}) renueva el vencimiento.
 */
@Service
@Transactional
public class SolicitudCadeteService {

    /** Lo que el admin puede marcar para corregir, con el nombre que ve el postulante (en este orden). */
    public static final Map<String, String> CAMPOS_REVISABLES = new LinkedHashMap<>();
    static {
        CAMPOS_REVISABLES.put("nombre", "Nombre y apellido");
        CAMPOS_REVISABLES.put("dni", "DNI");
        CAMPOS_REVISABLES.put("telefono", "Teléfono");
        CAMPOS_REVISABLES.put("email", "Email");
        CAMPOS_REVISABLES.put("vehiculo", "Datos del vehículo");
        CAMPOS_REVISABLES.put("fotoUrl", "Tu foto");
        CAMPOS_REVISABLES.put("fotoCarnetUrl", "Foto del DNI (frente)");
        CAMPOS_REVISABLES.put("fotoCarnetDorsoUrl", "Foto del DNI (dorso)");
        CAMPOS_REVISABLES.put("fotoVehiculoUrl", "Foto del vehículo");
        CAMPOS_REVISABLES.put("fotoTarjetaVerdeUrl", "Tarjeta verde (frente)");
        CAMPOS_REVISABLES.put("fotoTarjetaVerdeDorsoUrl", "Tarjeta verde (dorso)");
    }

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(ZoneId.of("America/Argentina/Buenos_Aires"));
    private static final int DIAS_LINK = 7;

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
        s.setExpiraEn(Instant.now().plus(DIAS_LINK, ChronoUnit.DAYS));
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
        switch (s.getEstado()) {
            case "PENDIENTE", "A_CORREGIR" -> { }
            case "EN_REVISION" -> throw new BadRequestException(
                    "Ya recibimos tu formulario y lo estamos revisando. Si hay que corregir algo, te llega un mail.");
            case "APROBADA" -> throw new BadRequestException(
                    "Ya estás dado de alta: revisá tu mail, ahí están tu usuario y tu contraseña.");
            case "RECHAZADA" -> throw new BadRequestException("Esta solicitud fue rechazada.");
            default -> throw new BadRequestException("Este link ya fue usado.");
        }
        if (s.getExpiraEn().isBefore(Instant.now())) {
            throw new BadRequestException("Este link venció. Pedí uno nuevo.");
        }
        return s;
    }

    /** Si hay que corregir, lo que ya había cargado (para precargar el formulario); si no, null. */
    @Transactional(readOnly = true)
    public CorreccionResponse correccionDe(SolicitudCadete s) {
        if (!"A_CORREGIR".equals(s.getEstado())) return null;
        Map<String, String> obs = leerObservaciones(s);
        return new CorreccionResponse(
                s.getNombre(), s.getApellido(), s.getDni(), s.getTelefono(), s.getEmail(),
                s.getTipoVehiculo() == null ? null : s.getTipoVehiculo().getId(),
                s.getVehiculoColor(), s.getVehiculoPatente(), s.getVehiculoMarca(), s.getVehiculoModelo(),
                obs.containsKey("fotoUrl") ? null : s.getFotoUrl(),
                obs.containsKey("fotoVehiculoUrl") ? null : s.getFotoVehiculoUrl(),
                obs.containsKey("fotoCarnetUrl") ? null : s.getFotoCarnetUrl(),
                obs.containsKey("fotoCarnetDorsoUrl") ? null : s.getFotoCarnetDorsoUrl(),
                obs.containsKey("fotoTarjetaVerdeUrl") ? null : s.getFotoTarjetaVerdeUrl(),
                obs.containsKey("fotoTarjetaVerdeDorsoUrl") ? null : s.getFotoTarjetaVerdeDorsoUrl(),
                observacionesDe(s));
    }

    /** El postulante envía el formulario — de un solo uso, queda EN_REVISION para el admin. */
    public SolicitudCadete enviarFormulario(String token, SolicitudFormRequest req) {
        SolicitudCadete s = validarToken(token);
        if ("A_CORREGIR".equals(s.getEstado())) {
            // Cada foto marcada tiene que venir NUEVA (otra URL); si no, se reenviaría la misma mal.
            Map<String, String> obs = leerObservaciones(s);
            Map<String, String[]> fotos = Map.of(
                    "fotoUrl", new String[]{s.getFotoUrl(), req.fotoUrl()},
                    "fotoVehiculoUrl", new String[]{s.getFotoVehiculoUrl(), req.fotoVehiculoUrl()},
                    "fotoCarnetUrl", new String[]{s.getFotoCarnetUrl(), req.fotoCarnetUrl()},
                    "fotoCarnetDorsoUrl", new String[]{s.getFotoCarnetDorsoUrl(), req.fotoCarnetDorsoUrl()},
                    "fotoTarjetaVerdeUrl", new String[]{s.getFotoTarjetaVerdeUrl(), req.fotoTarjetaVerdeUrl()},
                    "fotoTarjetaVerdeDorsoUrl", new String[]{s.getFotoTarjetaVerdeDorsoUrl(), req.fotoTarjetaVerdeDorsoUrl()});
            for (var e : fotos.entrySet()) {
                String nueva = e.getValue()[1];
                if (obs.containsKey(e.getKey()) && (nueva == null || nueva.isBlank() || nueva.equals(e.getValue()[0]))) {
                    throw new BadRequestException("Tenés que subir de nuevo: " + CAMPOS_REVISABLES.get(e.getKey()) + ".");
                }
            }
        }
        // El usuario del cadete es su DNI: se acepta con puntos o espacios y se guarda solo con números.
        String dni = req.dni().replaceAll("[.\\s]", "");
        if (!dni.matches("^[0-9]{6,8}$")) {
            throw new BadRequestException("El DNI tiene que ser solo números, hasta 8 dígitos (ej: 30111222).");
        }
        // Un DNI ya registrado NO se bloquea (2026-09-25): puede ser alguien que quiere volver.
        // El admin lo ve al revisar (cadeteExistenteDe) junto con el motivo de su baja.
        TipoVehiculo tipo = tipoVehiculoRepo.findById(req.tipoVehiculoId())
                .orElseThrow(() -> ResourceNotFoundException.of("Tipo de vehiculo", req.tipoVehiculoId()));

        s.setNombre(req.nombre().trim());
        s.setApellido(req.apellido().trim());
        s.setDni(dni);
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
        s.setUsernamePropuesto(dni);
        s.setObservaciones(null);
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
    public AprobacionResultado aprobar(String id, String username, String modalidadPago, String adminUsername) {
        SolicitudCadete s = get(id);
        if (!"EN_REVISION".equals(s.getEstado())) {
            throw new BadRequestException("Esta solicitud no está pendiente de revisión.");
        }
        Optional<Cadete> existente = buscarCadeteExistente(s);
        if (existente.isPresent()) {
            return reincorporar(s, existente.get(), modalidadPago, adminUsername);
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

    /** El cadete que ya tiene (o tuvo) ese DNI, por DNI o por usuario (el usuario es el DNI). */
    private Optional<Cadete> buscarCadeteExistente(SolicitudCadete s) {
        if (s.getDni() == null || s.getDni().isBlank()) return Optional.empty();
        return cadeteRepo.findByDni(s.getDni()).or(() -> cadeteRepo.findByUsername(s.getDni()));
    }

    /** Para el panel: si el DNI de la solicitud ya estuvo registrado, quién es y por qué se fue. */
    @Transactional(readOnly = true)
    public CadeteExistenteResponse cadeteExistenteDe(SolicitudCadete s) {
        return buscarCadeteExistente(s).map(c -> {
            var ultimaBaja = cadeteService.historialEstado(c.getId()).stream().filter(h -> !h.isActivo()).findFirst();
            return new CadeteExistenteResponse(c.getId(), c.getNombre(), c.getApellido(), c.getUsername(), c.isActivo(),
                    ultimaBaja.map(h -> h.getMotivo()).orElse(null),
                    ultimaBaja.map(h -> h.getCambiadoEn()).orElse(null));
        }).orElse(null);
    }

    /**
     * Alguien dado de baja que vuelve: no se crea otro cadete (el usuario es el DNI y ya existe),
     * se reactiva el mismo — con su historial — actualizando datos personales, vehículo y fotos
     * de la solicitud nueva. Límites, turno y demás configuración quedan como estaban.
     */
    private AprobacionResultado reincorporar(SolicitudCadete s, Cadete c, String modalidadPago, String adminUsername) {
        if (c.isActivo()) {
            throw new BadRequestException("Ya hay un cadete ACTIVO con ese DNI (" + c.getNombre() + " " + c.getApellido()
                    + "): no hace falta darlo de alta. Rechazá esta solicitud.");
        }
        c.setNombre(s.getNombre());
        c.setApellido(s.getApellido());
        c.setTelefono(s.getTelefono());
        c.setEmail(s.getEmail());
        c.setTipoVehiculo(s.getTipoVehiculo());
        c.setVehiculoColor(s.getVehiculoColor());
        c.setVehiculoPatente(s.getVehiculoPatente());
        c.setVehiculoMarca(s.getVehiculoMarca());
        c.setVehiculoModelo(s.getVehiculoModelo());
        if (s.getFotoUrl() != null) c.setFotoUrl(s.getFotoUrl());
        if (s.getFotoVehiculoUrl() != null) c.setFotoVehiculoUrl(s.getFotoVehiculoUrl());
        if (s.getFotoCarnetUrl() != null) c.setFotoCarnetUrl(s.getFotoCarnetUrl());
        if (s.getFotoCarnetDorsoUrl() != null) c.setFotoCarnetDorsoUrl(s.getFotoCarnetDorsoUrl());
        if (s.getFotoTarjetaVerdeUrl() != null) c.setFotoTarjetaVerdeUrl(s.getFotoTarjetaVerdeUrl());
        if (s.getFotoTarjetaVerdeDorsoUrl() != null) c.setFotoTarjetaVerdeDorsoUrl(s.getFotoTarjetaVerdeDorsoUrl());
        if (modalidadPago != null && !modalidadPago.isBlank()) c.setModalidadPago(modalidadPago);
        cadeteRepo.save(c);
        cadeteService.setActivo(c.getId(), true, "Reincorporado por una solicitud de alta nueva", adminUsername);
        String passwordTemporal = cadeteService.reenviarPasswordTemporal(c.getId());

        s.setEstado("APROBADA");
        s.setCadeteCreadoId(c.getId());
        s.setUsernamePropuesto(c.getUsername());
        repo.save(s);

        emailService.enviar(s.getEmail(), "Volviste a estar de alta en la cadetería",
                "Hola " + s.getNombre() + "!\n\n" +
                        "Te volvimos a dar de alta como cadete. Estos son tus datos para entrar a la app:\n\n" +
                        "Usuario: " + c.getUsername() + "\n" +
                        "Contraseña temporal: " + passwordTemporal + "\n\n" +
                        "Tenés 10 minutos para entrar con esta contraseña — si se vence, pedile a la cadetería que te la reenvíe.");
        return new AprobacionResultado(c, passwordTemporal);
    }

    /** Rechazo definitivo (no se puede corregir). Si hay mail, se le avisa con el motivo. */
    public SolicitudCadete rechazar(String id, String motivo) {
        SolicitudCadete s = get(id);
        if (!"EN_REVISION".equals(s.getEstado()) && !"A_CORREGIR".equals(s.getEstado())) {
            throw new BadRequestException("Esta solicitud no está pendiente de revisión.");
        }
        s.setEstado("RECHAZADA");
        s.setMotivoRechazo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        s = repo.save(s);
        emailService.enviar(s.getEmail(), "Tu solicitud para ser cadete",
                "Hola " + s.getNombre() + ".\n\n" +
                        "Revisamos tu solicitud y por ahora no podemos darte de alta como cadete." +
                        (s.getMotivoRechazo() == null ? "" : "\n\nMotivo: " + s.getMotivoRechazo()) +
                        "\n\nGracias por tu interés.");
        return s;
    }

    /**
     * El admin marca qué datos o fotos están mal y por qué: la solicitud vuelve al postulante
     * (A_CORREGIR) con el mismo link, renovado {@value #DIAS_LINK} días, y le llega un mail con
     * la lista. Claves válidas: {@link #CAMPOS_REVISABLES}.
     */
    public SolicitudCadete pedirCorreccion(String id, Map<String, String> observaciones) {
        SolicitudCadete s = get(id);
        if (!"EN_REVISION".equals(s.getEstado())) {
            throw new BadRequestException("Esta solicitud no está pendiente de revisión.");
        }
        Map<String, String> obs = new LinkedHashMap<>();
        if (observaciones != null) {
            for (String campo : CAMPOS_REVISABLES.keySet()) {
                String motivo = observaciones.get(campo);
                if (motivo != null && !motivo.isBlank()) obs.put(campo, motivo.trim());
            }
        }
        if (obs.isEmpty()) {
            throw new BadRequestException("Marcá al menos un dato o una foto a corregir, con el motivo.");
        }
        try {
            s.setObservaciones(JSON.writeValueAsString(obs));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        s.setEstado("A_CORREGIR");
        s.setCorrecciones(s.getCorrecciones() == null ? 1 : s.getCorrecciones() + 1);
        s.setExpiraEn(Instant.now().plus(DIAS_LINK, ChronoUnit.DAYS));
        s = repo.save(s);
        enviarMailCorreccion(s);
        return s;
    }

    public record LinkReenviado(SolicitudCadete solicitud, String url, boolean mailEnviado) {}

    /**
     * "Reenviar link": el postulante lo cerró, lo perdió o se le venció. Renueva el vencimiento;
     * si ya se le había pedido corregir (hay email), le vuelve a mandar el mail.
     */
    public LinkReenviado reenviarLink(String id) {
        SolicitudCadete s = get(id);
        if (!"PENDIENTE".equals(s.getEstado()) && !"A_CORREGIR".equals(s.getEstado())) {
            throw new BadRequestException("Solo se reenvía un link sin completar o con correcciones pendientes.");
        }
        s.setExpiraEn(Instant.now().plus(DIAS_LINK, ChronoUnit.DAYS));
        s = repo.save(s);
        boolean mail = "A_CORREGIR".equals(s.getEstado()) && emailService.isHabilitado()
                && s.getEmail() != null && !s.getEmail().isBlank();
        if (mail) enviarMailCorreccion(s);
        return new LinkReenviado(s, urlDe(s), mail);
    }

    private void enviarMailCorreccion(SolicitudCadete s) {
        StringBuilder lista = new StringBuilder();
        for (ObservacionResponse o : observacionesDe(s)) {
            lista.append("• ").append(o.etiqueta()).append(": ").append(o.motivo()).append("\n");
        }
        emailService.enviar(s.getEmail(), "Tenés que corregir tu solicitud para ser cadete",
                "Hola " + s.getNombre() + "!\n\n" +
                        "Revisamos tu solicitud y hay que corregir esto:\n\n" + lista +
                        "\nEntrá a este link: tus datos ya están cargados, solo cambiá lo que está marcado " +
                        "(las fotos marcadas tenés que subirlas de nuevo):\n" + urlDe(s) + "\n\n" +
                        "El link vence el " + FECHA.format(s.getExpiraEn()) + ". Si se te vence o lo perdés, " +
                        "pedile a la cadetería que te lo reenvíe.");
    }

    /** Las observaciones guardadas, en el orden de {@link #CAMPOS_REVISABLES}. Vacía si no hay. */
    public static List<ObservacionResponse> observacionesDe(SolicitudCadete s) {
        List<ObservacionResponse> out = new ArrayList<>();
        leerObservaciones(s).forEach((campo, motivo) ->
                out.add(new ObservacionResponse(campo, CAMPOS_REVISABLES.getOrDefault(campo, campo), motivo)));
        return out;
    }

    private static Map<String, String> leerObservaciones(SolicitudCadete s) {
        if (s.getObservaciones() == null || s.getObservaciones().isBlank()) return new LinkedHashMap<>();
        try {
            return JSON.readValue(s.getObservaciones(), new TypeReference<LinkedHashMap<String, String>>() {});
        } catch (Exception e) {
            return new LinkedHashMap<>();
        }
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
