package com.cadeteria.backend.common;

import com.cadeteria.backend.dto.CadeteDtos.CuentaRequest;
import com.cadeteria.backend.dto.SolicitudCadeteDtos.SolicitudFormRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Formatos que valida el backend igual que el front (2026-09-26). */
class ValidacionesTest {

    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    @Test
    void nombresSoloLetras() {
        for (String ok : new String[]{"Juan", "María José", "O'Connor", "De la Fuente", "Ñandú", "Jean-Pierre"}) {
            assertTrue(Validaciones.esNombrePersona(ok), ok);
        }
        for (String mal : new String[]{"Juan2", "123", "Juan!", "", "  ", "J@n", "-Juan"}) {
            assertFalse(Validaciones.esNombrePersona(mal), mal);
        }
    }

    @Test
    void dni7u8NumerosConOSinPuntos() {
        for (String ok : new String[]{"30111222", "1234567", "30.111.222", "1.234.567", "30 111 222"}) {
            assertTrue(ok.matches(Validaciones.DNI), ok);
        }
        for (String mal : new String[]{"301112", "301112223", "30a11222", "abc", "30-111-222"}) {
            assertFalse(mal.matches(Validaciones.DNI), mal);
        }
    }

    @Test
    void patenteDeMotoViejaONueva() {
        for (String ok : new String[]{"123ABC", "123abc", "A123BCD", "a123bcd", "A 123 BCD", "123-ABC"}) {
            assertTrue(Validaciones.esPatenteMoto(ok), ok);
        }
        for (String mal : new String[]{"ABC123", "AB123CD", "12ABC", "A12BCD", "1234ABC", "AA123BCD", ""}) {
            assertFalse(Validaciones.esPatenteMoto(mal), mal);
        }
        assertEquals("A123BCD", Validaciones.normalizarPatente(" a 123-bcd "));
    }

    @Test
    void telefonoDe7a15Digitos() {
        for (String ok : new String[]{"3815551234", "381 555-1234", "+54 9 381 555 1234", "(381) 5551234", "4221234"}) {
            assertTrue(ok.matches(Validaciones.TELEFONO), ok);
        }
        for (String mal : new String[]{"381abc1234", "123456", "1234567890123456", "tel 3815551234"}) {
            assertFalse(mal.matches(Validaciones.TELEFONO), mal);
        }
    }

    @Test
    void cbuYAlias() {
        assertTrue(VALIDATOR.validate(new CuentaRequest("0000003100000000000001", "juan.perez.mp")).isEmpty());
        assertTrue(VALIDATOR.validate(new CuentaRequest("", "")).isEmpty(), "vacíos se aceptan: son opcionales");
        assertEquals(Set.of("cbu", "aliasCbu"), campos(VALIDATOR.validate(new CuentaRequest("123", "a b"))));
    }

    @Test
    void formularioDeCadeteRechazaCadaCampoMal() {
        SolicitudFormRequest mal = new SolicitudFormRequest("Juan2", "P3rez", "30a", "abc", "no-es-mail", "MOTO",
                "Rojo5", "AB123CD", "Honda", "Wave", null, null, null, null, null, null, false);
        assertEquals(Set.of("nombre", "apellido", "dni", "telefono", "email", "vehiculoColor", "vehiculoPatente", "mayorDeEdad"),
                campos(VALIDATOR.validate(mal)));

        SolicitudFormRequest bien = new SolicitudFormRequest("Juan", "Pérez", "30.111.222", "381 555 1234", "juan@mail.com",
                "MOTO", "Rojo", "A123BCD", "Honda", "Wave 110", null, null, null, null, null, null, true);
        assertTrue(VALIDATOR.validate(bien).isEmpty());
    }

    private static Set<String> campos(Set<? extends ConstraintViolation<?>> v) {
        return v.stream().map(c -> c.getPropertyPath().toString()).collect(Collectors.toSet());
    }
}
