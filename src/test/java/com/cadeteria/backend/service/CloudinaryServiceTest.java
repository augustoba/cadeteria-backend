package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Sin api_key/api_secret configurados (todavía no se contrató el plan con esas
 * credenciales, ver AppProperties.Cloudinary) el servicio queda deshabilitado y no debe
 * intentar pegarle a la Admin API de Cloudinary — RetencionDatosService igual limpia la
 * referencia en la base, este servicio solo agrega el borrado real cuando está disponible.
 */
class CloudinaryServiceTest {

    @Test
    void quedaDeshabilitadoSinApiKeyNiApiSecret() {
        CloudinaryService service = new CloudinaryService(new AppProperties(), mock(ConfiguracionService.class));
        service.init();

        assertFalse(service.isHabilitado());
    }

    @Test
    void borrarSiCorrespondeEsNoOpCuandoNoEstaHabilitado() {
        ConfiguracionService configuracionService = mock(ConfiguracionService.class);
        CloudinaryService service = new CloudinaryService(new AppProperties(), configuracionService);
        service.init();

        assertDoesNotThrow(() -> service.borrarSiCorresponde("https://res.cloudinary.com/demo/image/upload/v1/foto.jpg"));
        verify(configuracionService, never()).getString(anyString(), anyString());
    }

    @Test
    void quedaHabilitadoConApiKeyYApiSecretConfigurados() {
        AppProperties props = new AppProperties();
        props.getCloudinary().setApiKey("key");
        props.getCloudinary().setApiSecret("secret");
        CloudinaryService service = new CloudinaryService(props, mock(ConfiguracionService.class));
        service.init();

        assertTrue(service.isHabilitado());
    }
}
