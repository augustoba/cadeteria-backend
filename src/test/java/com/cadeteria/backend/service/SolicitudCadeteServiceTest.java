package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.SolicitudFormRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteEstadoLog;
import com.cadeteria.backend.model.SolicitudCadete;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.SolicitudCadeteRepository;
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** Corrección de la solicitud (2026-09-25) y reingreso de alguien que ya fue cadete. */
class SolicitudCadeteServiceTest {

    private SolicitudCadeteRepository repo;
    private TipoVehiculoRepository tipoVehiculoRepo;
    private CadeteRepository cadeteRepo;
    private CadeteService cadeteService;
    private EmailService emailService;
    private SolicitudCadeteService service;

    @BeforeEach
    void setUp() {
        repo = mock(SolicitudCadeteRepository.class);
        tipoVehiculoRepo = mock(TipoVehiculoRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        cadeteService = mock(CadeteService.class);
        emailService = mock(EmailService.class);
        AppProperties props = new AppProperties();
        service = new SolicitudCadeteService(repo, tipoVehiculoRepo, cadeteRepo, cadeteService, emailService, props);
        when(repo.save(any())).thenAnswer(i -> i.getArgument(0));
        TipoVehiculo moto = new TipoVehiculo();
        moto.setId("MOTO");
        when(tipoVehiculoRepo.findById("MOTO")).thenReturn(Optional.of(moto));
        when(cadeteRepo.findByDni(anyString())).thenReturn(Optional.empty());
        when(cadeteRepo.findByUsername(anyString())).thenReturn(Optional.empty());
    }

    @Test
    void pedirCorreccionGuardaLoMarcadoMandaMailYRenuevaElLink() {
        SolicitudCadete s = enRevision();
        when(repo.findById("s1")).thenReturn(Optional.of(s));

        service.pedirCorreccion("s1", Map.of("fotoCarnetUrl", "Está borrosa", "telefono", "Falta la característica", "inventado", "x"));

        assertEquals("A_CORREGIR", s.getEstado());
        assertEquals(1, s.getCorrecciones());
        assertTrue(s.getExpiraEn().isAfter(Instant.now().plus(6, ChronoUnit.DAYS)));
        var obs = SolicitudCadeteService.observacionesDe(s);
        // en el orden del formulario, y la clave inventada no entra
        assertEquals(List.of("telefono", "fotoCarnetUrl"), obs.stream().map(o -> o.campo()).toList());
        verify(emailService).enviar(eq("juan@mail.com"), anyString(), contains("Foto del DNI (frente): Está borrosa"));
    }

    @Test
    void pedirCorreccionSinNadaMarcadoNoSeAcepta() {
        when(repo.findById("s1")).thenReturn(Optional.of(enRevision()));
        assertThrows(BadRequestException.class, () -> service.pedirCorreccion("s1", Map.of("dni", "  ")));
    }

    @Test
    void laCorreccionPrecargaTodoMenosLasFotosMarcadas() {
        SolicitudCadete s = enRevision();
        when(repo.findById("s1")).thenReturn(Optional.of(s));
        service.pedirCorreccion("s1", Map.of("fotoCarnetUrl", "Está borrosa"));

        var c = service.correccionDe(s);

        assertEquals("Juan", c.nombre());
        assertEquals("https://img/foto.jpg", c.fotoUrl());
        assertNull(c.fotoCarnetUrl());
    }

    @Test
    void alCorregirHayQueSubirOtraFotoDeLaMarcada() {
        SolicitudCadete s = enRevision();
        when(repo.findById("s1")).thenReturn(Optional.of(s));
        when(repo.findByToken("tok")).thenReturn(Optional.of(s));
        service.pedirCorreccion("s1", Map.of("fotoCarnetUrl", "Está borrosa"));

        // la misma foto de antes: no
        assertThrows(BadRequestException.class, () -> service.enviarFormulario("tok", form("https://img/dni.jpg")));

        service.enviarFormulario("tok", form("https://img/dni-nueva.jpg"));
        assertEquals("EN_REVISION", s.getEstado());
        assertNull(s.getObservaciones());
        assertEquals("https://img/dni-nueva.jpg", s.getFotoCarnetUrl());
    }

    @Test
    void unDniYaRegistradoNoBloqueaElFormularioYElPanelLoVeConElMotivoDeLaBaja() {
        SolicitudCadete s = enRevision();
        Cadete viejo = cadete(false);
        when(cadeteRepo.findByDni("30111222")).thenReturn(Optional.of(viejo));
        CadeteEstadoLog baja = new CadeteEstadoLog();
        baja.setActivo(false);
        baja.setMotivo("Dejó de venir sin avisar");
        baja.setCambiadoEn(Instant.parse("2026-05-01T12:00:00Z"));
        when(cadeteService.historialEstado("c1")).thenReturn(List.of(baja));

        var existente = service.cadeteExistenteDe(s);

        assertNotNull(existente);
        assertEquals("Dejó de venir sin avisar", existente.motivoUltimaBaja());
    }

    @Test
    void aprobarAAlguienDadoDeBajaLoReactivaEnVezDeCrearOtro() {
        SolicitudCadete s = enRevision();
        when(repo.findById("s1")).thenReturn(Optional.of(s));
        Cadete viejo = cadete(false);
        when(cadeteRepo.findByDni("30111222")).thenReturn(Optional.of(viejo));
        when(cadeteService.reenviarPasswordTemporal("c1")).thenReturn("Temp12345");

        var r = service.aprobar("s1", "30111222", "SEMANAL", "admin");

        verify(cadeteService, never()).create(any(), any());
        verify(cadeteService).setActivo(eq("c1"), eq(true), anyString(), eq("admin"));
        assertEquals("Temp12345", r.passwordTemporal());
        assertEquals("1133334444", viejo.getTelefono());
        assertEquals("APROBADA", s.getEstado());
    }

    @Test
    void aprobarConUnCadeteActivoConEseDniNoSePuede() {
        SolicitudCadete s = enRevision();
        when(repo.findById("s1")).thenReturn(Optional.of(s));
        when(cadeteRepo.findByDni("30111222")).thenReturn(Optional.of(cadete(true)));

        assertThrows(ConflictException.class, () -> service.aprobar("s1", "30111222", "SEMANAL", "admin"));
    }

    private SolicitudCadete enRevision() {
        SolicitudCadete s = new SolicitudCadete();
        s.setId("s1");
        s.setToken("tok");
        s.setEstado("EN_REVISION");
        s.setCreadoEn(Instant.now());
        s.setExpiraEn(Instant.now().plus(1, ChronoUnit.DAYS));
        s.setNombre("Juan");
        s.setApellido("Pérez");
        s.setDni("30111222");
        s.setTelefono("1133334444");
        s.setEmail("juan@mail.com");
        TipoVehiculo moto = new TipoVehiculo();
        moto.setId("MOTO");
        s.setTipoVehiculo(moto);
        s.setFotoUrl("https://img/foto.jpg");
        s.setFotoCarnetUrl("https://img/dni.jpg");
        s.setFotoCarnetDorsoUrl("https://img/dni-dorso.jpg");
        return s;
    }

    private SolicitudFormRequest form(String fotoCarnet) {
        return form(fotoCarnet, "30.111.222", "a 123 bcd", true);
    }

    private SolicitudFormRequest form(String fotoCarnet, String dni, String patente, boolean mayorDeEdad) {
        return new SolicitudFormRequest("Juan", "Pérez", dni, "381 555 1234", "juan@mail.com", "MOTO",
                "Rojo", patente, "Honda", "Wave", "https://img/foto.jpg", "https://img/moto.jpg", fotoCarnet,
                "https://img/dni-dorso.jpg", "https://img/tv.jpg", "https://img/tv-dorso.jpg", mayorDeEdad);
    }

    // --- Validaciones del formulario (2026-09-26): antes solo las controlaba el front ---

    @Test
    void unMenorDeEdadNoSePuedeAnotar() {
        when(repo.findByToken("tok")).thenReturn(Optional.of(pendiente()));
        var e = assertThrows(BadRequestException.class,
                () -> service.enviarFormulario("tok", form("https://img/dni.jpg", "30111222", "A123BCD", false)));
        assertTrue(e.getMessage().contains("mayor de 18"));
    }

    @Test
    void alAnotarseQuedaGuardadoCuandoDeclaroSerMayorYLaPatenteNormalizada() {
        SolicitudCadete s = pendiente();
        when(repo.findByToken("tok")).thenReturn(Optional.of(s));
        when(repo.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.enviarFormulario("tok", form("https://img/dni.jpg"));

        assertNotNull(s.getMayorEdadDeclaradaEn());
        assertEquals("A123BCD", s.getVehiculoPatente());
        assertEquals("30111222", s.getDni());
    }

    @Test
    void elDniTieneQueSerDe7u8Numeros() {
        when(repo.findByToken("tok")).thenReturn(Optional.of(pendiente()));
        assertThrows(BadRequestException.class,
                () -> service.enviarFormulario("tok", form("https://img/dni.jpg", "301112", "A123BCD", true)));
    }

    @Test
    void unaMotoSinPatenteNoSeAcepta() {
        when(repo.findByToken("tok")).thenReturn(Optional.of(pendiente()));
        var e = assertThrows(BadRequestException.class,
                () -> service.enviarFormulario("tok", form("https://img/dni.jpg", "30111222", " ", true)));
        assertTrue(e.getMessage().contains("patente"));
    }

    private SolicitudCadete pendiente() {
        SolicitudCadete s = new SolicitudCadete();
        s.setId("s2");
        s.setToken("tok");
        s.setEstado("PENDIENTE");
        s.setExpiraEn(Instant.now().plus(1, ChronoUnit.DAYS));
        return s;
    }

    private Cadete cadete(boolean activo) {
        Cadete c = new Cadete();
        c.setId("c1");
        c.setNombre("Juan");
        c.setApellido("Pérez");
        c.setUsername("30111222");
        c.setTelefono("viejo");
        c.setActivo(activo);
        return c;
    }
}
