package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.PedidoPushSubscription;
import com.cadeteria.backend.repository.PedidoPushSubscriptionRepository;
import jakarta.annotation.PostConstruct;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Utils;
import nl.martijndwars.webpush.jwt.nimbus.NimbusJwtFactory;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import org.bouncycastle.jce.interfaces.ECPrivateKey;
import org.bouncycastle.jce.interfaces.ECPublicKey;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Security;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;

/**
 * Notificaciones Web Push a la página de seguimiento (mejora 89) — complemento del SMS,
 * no lo reemplaza. El cliente se suscribe con un clic (Notification API + Service
 * Worker) desde /seguimiento/:token; acá se guarda esa suscripción y se le manda un push
 * en los mismos momentos en que ya se manda SMS (aceptado/finalizado).
 */
@Service
public class WebPushService {

    private static final Logger log = LoggerFactory.getLogger(WebPushService.class);
    private static final String CLAVE_PUBLICA = "vapid_public_key";
    private static final String CLAVE_PRIVADA = "vapid_private_key";

    private final ConfiguracionService configuracionService;
    private final PedidoPushSubscriptionRepository subscriptionRepo;

    private PushService pushService;
    private String publicKeyBase64Url;

    public WebPushService(ConfiguracionService configuracionService, PedidoPushSubscriptionRepository subscriptionRepo) {
        this.configuracionService = configuracionService;
        this.subscriptionRepo = subscriptionRepo;
    }

    @PostConstruct
    void init() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
        try {
            KeyPair keyPair = cargarOGenerarKeyPair();
            this.pushService = PushService.builder()
                    .withVapidKeyPair(keyPair)
                    .withVapidSubject("mailto:soporte@cadeteria.local")
                    .withJwtFactory(new NimbusJwtFactory())
                    .build();
            this.publicKeyBase64Url = Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(Utils.encode((ECPublicKey) keyPair.getPublic()));
        } catch (Exception e) {
            log.error("No se pudo inicializar Web Push — las notificaciones push van a quedar deshabilitadas.", e);
        }
    }

    private KeyPair cargarOGenerarKeyPair() throws Exception {
        var valores = configuracionService.findAll();
        String publicaB64 = valores.get(CLAVE_PUBLICA);
        String privadaB64 = valores.get(CLAVE_PRIVADA);
        if (publicaB64 != null && !publicaB64.isBlank() && privadaB64 != null && !privadaB64.isBlank()) {
            PublicKey pub = Utils.loadPublicKey(Base64.getDecoder().decode(publicaB64));
            PrivateKey priv = Utils.loadPrivateKey(Base64.getDecoder().decode(privadaB64));
            return new KeyPair(pub, priv);
        }
        KeyPairGenerator generator = KeyPairGenerator.getInstance("EC", "BC");
        generator.initialize(new ECGenParameterSpec("secp256r1"));
        KeyPair keyPair = generator.generateKeyPair();
        configuracionService.set(CLAVE_PUBLICA,
                Base64.getEncoder().encodeToString(Utils.encode((ECPublicKey) keyPair.getPublic())));
        configuracionService.set(CLAVE_PRIVADA,
                Base64.getEncoder().encodeToString(Utils.encode((ECPrivateKey) keyPair.getPrivate())));
        log.info("Generado un par de claves VAPID nuevo para Web Push.");
        return keyPair;
    }

    /** Clave pública en base64url — es lo único que necesita el navegador para suscribirse (no es secreta). */
    public String getPublicKeyBase64Url() {
        return publicKeyBase64Url;
    }

    public boolean isHabilitado() {
        return pushService != null;
    }

    public void suscribir(Pedido pedido, String endpoint, String p256dh, String auth) {
        if (subscriptionRepo.existsByPedidoIdAndEndpoint(pedido.getId(), endpoint)) return;
        PedidoPushSubscription s = new PedidoPushSubscription();
        s.setId(UUID.randomUUID().toString());
        s.setPedido(pedido);
        s.setEndpoint(endpoint);
        s.setP256dh(p256dh);
        s.setAuth(auth);
        subscriptionRepo.save(s);
    }

    /** No bloquea ni rompe el flujo del pedido si falla — mismo criterio que SmsGatewayService. */
    public void enviarA(Pedido pedido, String titulo, String cuerpo) {
        if (pushService == null) return;
        List<PedidoPushSubscription> subs = subscriptionRepo.findByPedidoId(pedido.getId());
        if (subs.isEmpty()) return;
        String payload = "{\"title\":\"" + escapar(titulo) + "\",\"body\":\"" + escapar(cuerpo) + "\"}";
        for (PedidoPushSubscription s : subs) {
            try {
                Notification notification = Notification.builder()
                        .endpoint(s.getEndpoint())
                        .userPublicKey(s.getP256dh())
                        .userAuth(s.getAuth())
                        .payload(payload)
                        .build();
                pushService.send(notification);
            } catch (Exception e) {
                log.warn("No se pudo mandar el push a una suscripción del pedido {}: {}", pedido.getId(), e.getMessage());
            }
        }
    }

    private String escapar(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
