package com.cadeteria.backend.config;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ConflictException;
import com.cadeteria.backend.common.ForbiddenException;
import com.cadeteria.backend.common.GoneException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.common.ServiceUnavailableException;
import com.cadeteria.backend.common.TooManyRequestsException;
import com.cadeteria.backend.dto.CadeteDtos.TelefonoRequest;
import jakarta.validation.Valid;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.sql.SQLIntegrityConstraintViolationException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Cada tipo de error sale con su código HTTP, su `codigo` fijo y un mensaje en castellano (2026-09-26). */
class GlobalExceptionHandlerTest {

    private MockMvc mvc;

    @RestController
    static class Prueba {
        @GetMapping("/p/{caso}")
        String lanzar(@PathVariable String caso) {
            switch (caso) {
                case "400" -> throw new BadRequestException("Dato mal.");
                case "403" -> throw new ForbiddenException("No es tuyo.");
                case "404" -> throw new ResourceNotFoundException("No está.");
                case "409" -> throw new ConflictException("Ya cambió.");
                case "410" -> throw new GoneException("Link vencido.");
                case "429" -> throw new TooManyRequestsException("Esperá.");
                case "503" -> throw new ServiceUnavailableException("Sin cupo.");
                case "credenciales" -> throw new BadCredentialsException("Usuario o contraseña incorrectos.");
                case "duplicado" -> throw new DataIntegrityViolationException("x",
                        new SQLIntegrityConstraintViolationException("Duplicate entry '30111222' for key 'cadete.dni'"));
                case "largo" -> throw new DataIntegrityViolationException("x",
                        new java.sql.SQLException("Data truncation: Data too long for column 'nombre' at row 1"));
                case "bloqueo" -> throw new CannotAcquireLockException("Deadlock found");
                default -> throw new IllegalStateException("select * from cadete where password = 'secreto'");
            }
        }

        @GetMapping("/numero")
        int numero(@RequestParam int n) {
            return n;
        }

        @PostMapping("/telefono")
        String telefono(@Valid @RequestBody TelefonoRequest req) {
            return req.telefono();
        }
    }

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new Prueba()).setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    void erroresDeNegocioConSuCodigo() throws Exception {
        String[][] casos = {
                {"400", "DATO_INVALIDO"}, {"403", "SIN_PERMISO"}, {"404", "NO_ENCONTRADO"}, {"409", "CONFLICTO"},
                {"410", "LINK_VENCIDO"}, {"429", "DEMASIADAS_SOLICITUDES"}, {"503", "SERVICIO_NO_DISPONIBLE"}};
        for (String[] c : casos) {
            mvc.perform(get("/p/" + c[0]))
                    .andExpect(status().is(Integer.parseInt(c[0])))
                    .andExpect(jsonPath("$.codigo").value(c[1]))
                    .andExpect(jsonPath("$.status").value(Integer.parseInt(c[0])));
        }
    }

    @Test
    void elLoginMalMuestraElMotivoConUn401() throws Exception {
        mvc.perform(get("/p/credenciales"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("CREDENCIALES_INVALIDAS"))
                .andExpect(jsonPath("$.message").value("Usuario o contraseña incorrectos."));
    }

    @Test
    void unErrorInesperadoNoMuestraElDetalleInternoYDaUnaReferencia() throws Exception {
        mvc.perform(get("/p/otro"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.codigo").value("ERROR_INTERNO"))
                .andExpect(jsonPath("$.message", not(containsString("select"))))
                .andExpect(jsonPath("$.referencia").isNotEmpty());
    }

    @Test
    void duplicadoEs409YTextoLargoEs400() throws Exception {
        mvc.perform(get("/p/duplicado")).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("DUPLICADO"));
        mvc.perform(get("/p/largo")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El dato «nombre» es demasiado largo."));
    }

    @Test
    void dosOperacionesALaVezEs409() throws Exception {
        mvc.perform(get("/p/bloqueo")).andExpect(status().isConflict()).andExpect(jsonPath("$.codigo").value("EDICION_SIMULTANEA"));
    }

    @Test
    void validacionDelBodyDevuelveElCampoYElMotivo() throws Exception {
        mvc.perform(post("/telefono").contentType(MediaType.APPLICATION_JSON).content("{\"telefono\":\"abc\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("VALIDACION"))
                .andExpect(jsonPath("$.fieldErrors.telefono").value(containsString("solo puede tener números")))
                .andExpect(jsonPath("$.message").value(containsString("Revisá los datos")));
    }

    @Test
    void jsonRotoYBodyVacioSon400() throws Exception {
        mvc.perform(post("/telefono").contentType(MediaType.APPLICATION_JSON).content("{telefono:"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
        mvc.perform(post("/telefono").contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("JSON_INVALIDO"));
    }

    @Test
    void parametroMalOFaltanteEs400() throws Exception {
        mvc.perform(get("/numero").param("n", "diez"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("PARAMETRO_INVALIDO"));
        mvc.perform(get("/numero"))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.codigo").value("FALTA_PARAMETRO"));
    }

    @Test
    void metodoEquivocadoEs405YFormatoEquivocadoEs415() throws Exception {
        mvc.perform(put("/telefono").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isMethodNotAllowed()).andExpect(jsonPath("$.codigo").value("METODO_NO_PERMITIDO"));
        mvc.perform(post("/telefono").contentType(MediaType.TEXT_PLAIN).content("hola"))
                .andExpect(status().isUnsupportedMediaType()).andExpect(jsonPath("$.codigo").value("FORMATO_NO_SOPORTADO"));
    }
}
