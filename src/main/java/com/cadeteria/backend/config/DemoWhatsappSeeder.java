package com.cadeteria.backend.config;

import com.cadeteria.backend.model.WhatsappChip;
import com.cadeteria.backend.model.WhatsappMensaje;
import com.cadeteria.backend.model.WhatsappRespuesta;
import com.cadeteria.backend.repository.WhatsappChipRepository;
import com.cadeteria.backend.repository.WhatsappMensajeRepository;
import com.cadeteria.backend.repository.WhatsappRespuestaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Chips, mensajes y respuestas de ejemplo para el panel de WhatsApp (ver memoria del
 * proyecto "whatsapp-gateway") — el gateway real todavía no se probó en vivo (falta el
 * primer chip descartable), así que sin esto el panel se ve vacío. IDs fijos con prefijo
 * "demo-" y `Random` con semilla fija: se borran y recrean en cada arranque, igual
 * criterio que {@link DemoPedidoSeeder}, y nunca van a colisionar con chips reales (que
 * el operador nombra "chip1", "chip2", etc. en la guía del gateway).
 *
 * Ningún mensaje demo queda en estado PENDIENTE a propósito: si quedara alguno y en
 * paralelo se conectara un gateway real, {@code WhatsappGatewayService.reenviarPendientes}
 * intentaría mandarlo de verdad a un número de teléfono inventado.
 */
@Component
@Order(3)
public class DemoWhatsappSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoWhatsappSeeder.class);
    private static final int CANTIDAD_MENSAJES = 260;
    private static final int CANTIDAD_RESPUESTAS = 35;
    private static final int DIAS_HISTORIAL = 6;

    private static final List<String> CHIP_IDS = List.of(
            "demo-chip-1", "demo-chip-2", "demo-chip-3", "demo-chip-4", "demo-chip-5", "demo-chip-6");
    private static final List<String> TELEFONOS = List.of(
            "3813012345", "3814098765", "3815551122", "3815223344", "3815667788", "3815884455",
            "3815990011", "3815223399", "3816001122", "3816112233", "3816223344", "3816334455",
            "3817445566", "3817556677", "3817667788");
    private static final List<String> PEDIDO_IDS_DEMO = List.of(
            "demo-pedido-01", "demo-pedido-02", "demo-pedido-03", "demo-pedido-04",
            "demo-pedido-05", "demo-pedido-06", "demo-pedido-07", "demo-pedido-08");
    private static final List<String> TEXTOS_ENVIADOS = List.of(
            "Hola! Tu pedido fue asignado a un cadete, en breve pasa a buscarlo por el local.",
            "El cadete ya retiró tu pedido y va en camino a destino.",
            "Tu pedido fue entregado. ¡Gracias por elegirnos!",
            "Hola, tuvimos un problema con la dirección de entrega, ¿nos confirmás el domicilio?",
            "Tu pedido está un poco demorado por tránsito, ya está en camino, gracias por la paciencia.",
            "Te confirmamos tu pedido para hoy en el horario acordado.");
    private static final List<String> TEXTOS_RESPUESTA = List.of(
            "Dale, gracias!", "¿Cuánto falta?", "Perfecto, los espero", "¿Puede pasar en 20 min mejor?",
            "Ok, gracias", "¿Seguro que ya salió?", "Muchas gracias!!", "Ya no lo necesito, ¿pueden cancelar?",
            "Todo bien, recibido", "Dale, cualquier cosa aviso");

    private final WhatsappChipRepository chipRepo;
    private final WhatsappMensajeRepository mensajeRepo;
    private final WhatsappRespuestaRepository respuestaRepo;
    private final AppProperties props;

    public DemoWhatsappSeeder(WhatsappChipRepository chipRepo, WhatsappMensajeRepository mensajeRepo,
                               WhatsappRespuestaRepository respuestaRepo, AppProperties props) {
        this.chipRepo = chipRepo;
        this.mensajeRepo = mensajeRepo;
        this.respuestaRepo = respuestaRepo;
        this.props = props;
    }

    @Override
    public void run(String... args) {
        if (!props.getDemo().isEnabled()) return;

        limpiarDemoAnterior();
        Instant ahora = Instant.now();
        Random rnd = new Random(42);

        sembrarChips(ahora);
        sembrarMensajes(ahora, rnd);
        sembrarRespuestas(ahora, rnd);

        log.info("Demo: {} chips, {} mensajes y {} respuestas de ejemplo sembrados para el panel de WhatsApp.",
                CHIP_IDS.size(), CANTIDAD_MENSAJES, CANTIDAD_RESPUESTAS);
    }

    private void limpiarDemoAnterior() {
        chipRepo.findAllById(CHIP_IDS).forEach(chipRepo::delete);
        mensajeIdsPosibles().forEach(id -> mensajeRepo.findById(id).ifPresent(mensajeRepo::delete));
        respuestaIdsPosibles().forEach(id -> respuestaRepo.findById(id).ifPresent(respuestaRepo::delete));
    }

    private List<String> mensajeIdsPosibles() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < CANTIDAD_MENSAJES; i++) ids.add("demo-wa-mensaje-" + i);
        return ids;
    }

    private List<String> respuestaIdsPosibles() {
        List<String> ids = new ArrayList<>();
        for (int i = 0; i < CANTIDAD_RESPUESTAS; i++) ids.add("demo-wa-respuesta-" + i);
        return ids;
    }

    /** Un chip de cada estado posible, para que el panel se vea completo sin tener que esperar a que pase de verdad. */
    private void sembrarChips(Instant ahora) {
        crearChip("demo-chip-1", "5493811111111", "CONECTADO", null, ahora.minus(3, ChronoUnit.HOURS));
        crearChip("demo-chip-2", "5493812222222", "CONECTADO", null, ahora.minus(50, ChronoUnit.MINUTES));
        // "Shadowban": conectado, pero con muy baja entrega (ver sembrarMensajes) — el indicador de la pestaña Chips lo tiene que marcar.
        crearChip("demo-chip-3", "5493813333333", "CONECTADO", null, ahora.minus(6, ChronoUnit.HOURS));
        crearChip("demo-chip-4", "5493814444444", "DESCONECTADO", null, ahora.minus(20, ChronoUnit.MINUTES));
        crearChip("demo-chip-5", "5493815555555", "BANEADO", null, ahora.minus(2, ChronoUnit.DAYS));
        crearChip("demo-chip-6", "5493816666666", "VINCULANDO", "48213976", ahora.minus(2, ChronoUnit.MINUTES));
    }

    private void crearChip(String id, String numero, String estado, String pairingCodigo, Instant ultimoCambio) {
        WhatsappChip c = new WhatsappChip();
        c.setId(id);
        c.setNumero(numero);
        c.setEstado(estado);
        c.setPairingCodigo(pairingCodigo);
        c.setUltimoCambioEstado(ultimoCambio);
        chipRepo.save(c);
    }

    /**
     * Spread de {@value #CANTIDAD_MENSAJES} mensajes en los últimos {@value #DIAS_HISTORIAL} días — a propósito
     * más que una sola página, para poder ver el paginado/filtro por fecha y por teléfono funcionando de verdad.
     * demo-chip-3 entrega mucho menos que el resto (simula shadowban); ningún mensaje queda PENDIENTE (ver
     * javadoc de la clase).
     */
    private void sembrarMensajes(Instant ahora, Random rnd) {
        List<WhatsappMensaje> lote = new ArrayList<>();
        for (int i = 0; i < CANTIDAD_MENSAJES; i++) {
            String telefono = TELEFONOS.get(rnd.nextInt(TELEFONOS.size()));
            String chipUsado = CHIP_IDS.get(rnd.nextInt(4)); // los primeros 4 (conectados/desconectado) son los que "mandaron" algo
            String texto = TEXTOS_ENVIADOS.get(rnd.nextInt(TEXTOS_ENVIADOS.size()));
            String pedidoId = rnd.nextInt(4) == 0 ? null : PEDIDO_IDS_DEMO.get(rnd.nextInt(PEDIDO_IDS_DEMO.size()));
            Instant creadoEn = ahora.minus(rnd.nextInt(DIAS_HISTORIAL * 24 * 60), ChronoUnit.MINUTES);
            boolean fallo = rnd.nextInt(12) == 0;

            WhatsappMensaje m = new WhatsappMensaje();
            m.setId("demo-wa-mensaje-" + i);
            m.setPedidoId(pedidoId);
            m.setTelefono(telefono);
            m.setTexto(texto);
            m.setCreadoEn(creadoEn);

            if (fallo) {
                m.setEstado("FALLIDO");
                m.setChipUsado(chipUsado);
                m.setError("Timeout al mandar el mensaje (chip sin señal en ese momento)");
                m.setEnviadoEn(creadoEn.plusSeconds(5));
            } else {
                m.setEstado("ENVIADO");
                m.setChipUsado(chipUsado);
                m.setEnviadoEn(creadoEn.plusSeconds(2));
                // demo-chip-3 (shadowban): solo ~15% de entrega. El resto: ~85% entregado, ~55% de eso leído.
                double probabilidadEntrega = "demo-chip-3".equals(chipUsado) ? 0.15 : 0.85;
                if (rnd.nextDouble() < probabilidadEntrega) {
                    Instant entregadoEn = m.getEnviadoEn().plusSeconds(3 + rnd.nextInt(30));
                    m.setEntregadoEn(entregadoEn);
                    if (rnd.nextDouble() < 0.55) {
                        m.setLeidoEn(entregadoEn.plusSeconds(10 + rnd.nextInt(600)));
                    }
                }
            }
            lote.add(m);
        }
        mensajeRepo.saveAll(lote);
    }

    private void sembrarRespuestas(Instant ahora, Random rnd) {
        List<WhatsappRespuesta> lote = new ArrayList<>();
        for (int i = 0; i < CANTIDAD_RESPUESTAS; i++) {
            WhatsappRespuesta r = new WhatsappRespuesta();
            r.setId("demo-wa-respuesta-" + i);
            r.setTelefono(TELEFONOS.get(rnd.nextInt(TELEFONOS.size())));
            r.setChipId(CHIP_IDS.get(rnd.nextInt(4)));
            r.setTexto(TEXTOS_RESPUESTA.get(rnd.nextInt(TEXTOS_RESPUESTA.size())));
            r.setRecibidoEn(ahora.minus(rnd.nextInt(DIAS_HISTORIAL * 24 * 60), ChronoUnit.MINUTES));
            lote.add(r);
        }
        respuestaRepo.saveAll(lote);
    }
}
