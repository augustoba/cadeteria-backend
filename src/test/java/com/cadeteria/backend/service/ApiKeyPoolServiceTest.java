package com.cadeteria.backend.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * El pool ya rota sola entre varias cuentas cargadas por proveedor; lo que falta es avisar al
 * admin (mismo canal que el chip de WhatsApp baneado) cuando eso deja de alcanzar, sin agregar
 * ninguna consulta extra al proveedor — todo se dispara desde los mismos métodos que ya se
 * llaman en cada uso real (registrarUso/marcarAgotada/actualizarRestanteInformado).
 */
class ApiKeyPoolServiceTest {

    private ConfiguracionService configuracionService;
    private WebSocketPublisher publisher;
    private ApiKeyPoolService pool;

    @BeforeEach
    void setUp() {
        configuracionService = mock(ConfiguracionService.class);
        publisher = mock(WebSocketPublisher.class);
        pool = new ApiKeyPoolService(configuracionService, publisher);
    }

    @Test
    void avisaCuandoSeAgotaLaUnicaKeyDeUnProveedor() {
        when(configuracionService.getString("geoapify_keys", "")).thenReturn("KEY1");
        String key = pool.siguienteClave("geoapify", "geoapify_keys");

        pool.marcarAgotada("geoapify", "geoapify_keys", key);

        verify(publisher).publicarAlertaApiKeyPoolAgotado("geoapify");
    }

    @Test
    void noAvisaMientrasQuedaOtraKeyConCupo() {
        when(configuracionService.getString("geoapify_keys", "")).thenReturn("KEY1,KEY2");
        pool.siguienteClave("geoapify", "geoapify_keys");

        pool.marcarAgotada("geoapify", "geoapify_keys", "KEY1");

        verify(publisher, never()).publicarAlertaApiKeyPoolAgotado(any());
    }

    @Test
    void avisaAlAgotarseLaSegundaKeyCuandoLaPrimeraYaEstabaAgotada() {
        when(configuracionService.getString("geoapify_keys", "")).thenReturn("KEY1,KEY2");
        pool.siguienteClave("geoapify", "geoapify_keys");
        pool.marcarAgotada("geoapify", "geoapify_keys", "KEY1");

        pool.marcarAgotada("geoapify", "geoapify_keys", "KEY2");

        verify(publisher, times(1)).publicarAlertaApiKeyPoolAgotado("geoapify");
    }

    @Test
    void noRepiteElAvisoSiLaKeyYaEstabaAgotada() {
        when(configuracionService.getString("geoapify_keys", "")).thenReturn("KEY1");
        pool.siguienteClave("geoapify", "geoapify_keys");
        pool.marcarAgotada("geoapify", "geoapify_keys", "KEY1");

        pool.marcarAgotada("geoapify", "geoapify_keys", "KEY1");

        verify(publisher, times(1)).publicarAlertaApiKeyPoolAgotado("geoapify");
    }

    @Test
    void avisaCuandoElCupoRestanteInformadoPorElProveedorEsBajo() {
        when(configuracionService.getString("openrouteservice_keys", "")).thenReturn("KEY1");
        pool.siguienteClave("openrouteservice", "openrouteservice_keys");

        // Límite diario conocido de openrouteservice: 2500 -> 10% = 250. 200 ya está por debajo.
        pool.actualizarRestanteInformado("openrouteservice", "openrouteservice_keys", "KEY1", 200);

        verify(publisher).publicarAlertaApiKeyPoolBajo("openrouteservice", 200);
    }

    @Test
    void noAvisaCupoBajoSiElRestanteTodaviaEstaComodo() {
        when(configuracionService.getString("openrouteservice_keys", "")).thenReturn("KEY1");
        pool.siguienteClave("openrouteservice", "openrouteservice_keys");

        pool.actualizarRestanteInformado("openrouteservice", "openrouteservice_keys", "KEY1", 2000);

        verify(publisher, never()).publicarAlertaApiKeyPoolBajo(any(), anyInt());
    }

    @Test
    void noRepiteElAvisoDeCupoBajoElMismoDia() {
        when(configuracionService.getString("openrouteservice_keys", "")).thenReturn("KEY1");
        pool.siguienteClave("openrouteservice", "openrouteservice_keys");

        pool.actualizarRestanteInformado("openrouteservice", "openrouteservice_keys", "KEY1", 200);
        pool.actualizarRestanteInformado("openrouteservice", "openrouteservice_keys", "KEY1", 150);

        verify(publisher, times(1)).publicarAlertaApiKeyPoolBajo(eq("openrouteservice"), anyInt());
    }

    @Test
    void alLlegarARestanteCeroAvisaAgotadoYNoCupoBajo() {
        when(configuracionService.getString("openrouteservice_keys", "")).thenReturn("KEY1");
        pool.siguienteClave("openrouteservice", "openrouteservice_keys");

        pool.actualizarRestanteInformado("openrouteservice", "openrouteservice_keys", "KEY1", 0);

        verify(publisher).publicarAlertaApiKeyPoolAgotado("openrouteservice");
        verify(publisher, never()).publicarAlertaApiKeyPoolBajo(any(), anyInt());
    }

    @Test
    void avisaCupoBajoPorEstimacionPropiaCuandoElProveedorNoInformaElRestante() {
        // graphhopper: limite diario conocido 500 -> 10% = 50. Sin dato real del proveedor,
        // el pool tiene que estimarlo solo contando cuantos usos reales ya hizo hoy.
        when(configuracionService.getString("graphhopper_keys", "")).thenReturn("KEY1");
        pool.siguienteClave("graphhopper", "graphhopper_keys");

        for (int i = 0; i < 451; i++) {
            pool.registrarUso("graphhopper", "graphhopper_keys", "KEY1");
        }

        verify(publisher, times(1)).publicarAlertaApiKeyPoolBajo(eq("graphhopper"), anyInt());
    }
}
