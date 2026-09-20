package com.cadeteria.backend.dto;

import com.cadeteria.backend.dto.CadeteDtos.CadeteResponse;
import com.cadeteria.backend.model.Cadete;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * El listado del panel no muestra fotos ni datos de cobro, así que no tienen por qué viajar
 * en cada carga. `fromListado` los vacía; el detalle (`from`) los sigue mandando porque la
 * ficha sí los muestra.
 */
class CadeteResponseTest {

    private Cadete cadeteConTodo() {
        Cadete c = new Cadete();
        c.setId("c1");
        c.setNombre("Juan");
        c.setApellido("Perez");
        c.setDni("30111222");
        c.setFotoUrl("https://res.cloudinary.com/demo/image/upload/v1/foto.jpg");
        c.setFotoVehiculoUrl("https://res.cloudinary.com/demo/image/upload/v1/vehiculo.jpg");
        c.setFotoCarnetUrl("https://res.cloudinary.com/demo/image/upload/v1/carnet.jpg");
        c.setFotoTarjetaVerdeUrl("https://res.cloudinary.com/demo/image/upload/v1/tarjeta.jpg");
        c.setCbu("0170099220000067797370");
        c.setAliasCbu("juan.moto.cadete");
        return c;
    }

    @Test
    void fromListadoVaciaFotosYDatosDeCobro() {
        CadeteResponse r = CadeteResponse.fromListado(cadeteConTodo(), null, 0);
        assertNull(r.fotoUrl());
        assertNull(r.fotoVehiculoUrl());
        assertNull(r.fotoCarnetUrl());
        assertNull(r.fotoTarjetaVerdeUrl());
        assertNull(r.cbu());
        assertNull(r.aliasCbu());
    }

    @Test
    void fromListadoConservaLoQueElListadoSiUsa() {
        CadeteResponse r = CadeteResponse.fromListado(cadeteConTodo(), null, 0);
        assertNotNull(r.nombre());
        assertNotNull(r.apellido());
        assertNotNull(r.dni());
        assertNotNull(r.id());
    }

    @Test
    void fromSigueMandandoTodo() {
        CadeteResponse r = CadeteResponse.from(cadeteConTodo());
        assertNotNull(r.fotoUrl());
        assertNotNull(r.cbu());
    }
}
