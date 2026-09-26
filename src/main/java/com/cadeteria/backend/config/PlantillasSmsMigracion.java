package com.cadeteria.backend.config;

import com.cadeteria.backend.service.ConfiguracionService;
import com.cadeteria.backend.service.PedidoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 2026-09-25: los mensajes al cliente pasaron a llevar el nombre de la cadetería y el número de
 * pedido. Si una plantilla guardada sigue siendo EXACTAMENTE el texto viejo de fábrica, se
 * reemplaza por el nuevo; si alguien ya la había cambiado a mano, no se toca.
 */
@Component
public class PlantillasSmsMigracion implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(PlantillasSmsMigracion.class);

    private static final Map<String, String[]> VIEJO_A_NUEVO = Map.of(
            "sms_template_aceptado", new String[]{"Tu pedido esta en camino, seguilo aca: {link}", PedidoService.SMS_ACEPTADO_DEFAULT},
            "sms_template_finalizado", new String[]{"Tu pedido fue entregado. Mira el detalle, descarga el comprobante y calificanos aca: {link}", PedidoService.SMS_FINALIZADO_DEFAULT},
            "sms_template_reenvio", new String[]{"Seguí tu pedido acá: {link}", PedidoService.SMS_REENVIO_DEFAULT});

    private final ConfiguracionService configuracionService;

    public PlantillasSmsMigracion(ConfiguracionService configuracionService) {
        this.configuracionService = configuracionService;
    }

    @Override
    public void run(ApplicationArguments args) {
        VIEJO_A_NUEVO.forEach((clave, textos) -> {
            if (textos[0].equals(configuracionService.getString(clave, null))) {
                configuracionService.set(clave, textos[1]);
                log.info("Plantilla {} actualizada al texto nuevo (con cadetería y número de pedido).", clave);
            }
        });
    }
}
