package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Map;

/**
 * Push a la app de cadetes via FCM (spec sección 7 — mismo esquema que ya usan y
 * funciona bien en otro proyecto). Se usa tanto para el fix del bug (evento de
 * "desasignacion") como para avisos de chat y viaje nuevo cuando la app esta en
 * background/cerrada.
 */
@Service
public class FcmService {

    private static final Logger log = LoggerFactory.getLogger(FcmService.class);

    private final AppProperties props;
    private boolean habilitado = false;

    public FcmService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        String path = props.getFcm().getCredencialesPath();
        if (path == null || path.isBlank()) {
            log.warn("app.fcm.credenciales-path no configurado — push a cadetes deshabilitado.");
            return;
        }
        try (FileInputStream in = new FileInputStream(path)) {
            if (FirebaseApp.getApps().isEmpty()) {
                FirebaseApp.initializeApp(FirebaseOptions.builder()
                        .setCredentials(GoogleCredentials.fromStream(in))
                        .build());
            }
            habilitado = true;
        } catch (IOException e) {
            log.error("No se pudo inicializar Firebase con {}: {}", path, e.getMessage());
        }
    }

    /** Para el panel de salud del sistema (mejora 48). */
    public boolean isHabilitado() {
        return habilitado;
    }

    @Async
    public void enviar(String fcmToken, String titulo, String cuerpo, Map<String, String> datos) {
        if (!habilitado || fcmToken == null || fcmToken.isBlank()) return;
        try {
            Message message = Message.builder()
                    .setToken(fcmToken)
                    .setNotification(Notification.builder().setTitle(titulo).setBody(cuerpo).build())
                    .putAllData(datos == null ? Map.of() : datos)
                    .build();
            FirebaseMessaging.getInstance().send(message);
        } catch (Exception e) {
            log.error("Fallo el push FCM: {}", e.getMessage());
        }
    }
}
