package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.Properties;

/**
 * Envío de mails por SMTP estándar (mismo enfoque que el ecommerce: credenciales por
 * variables de entorno, se cargan las que correspondan más adelante) — usado para el
 * alta de cadete por formulario propio (ronda 7). Host vacío = deshabilitado: se loguea
 * y no rompe el alta, igual que FcmService cuando no hay credenciales.
 */
@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final AppProperties props;
    private JavaMailSender mailSender;
    private boolean habilitado = false;

    public EmailService(AppProperties props) {
        this.props = props;
    }

    @PostConstruct
    void init() {
        String host = props.getMail().getHost();
        if (host == null || host.isBlank()) {
            log.warn("app.mail.host no configurado — envio de emails deshabilitado.");
            return;
        }
        JavaMailSenderImpl sender = new JavaMailSenderImpl();
        sender.setHost(host);
        sender.setPort(props.getMail().getPort());
        sender.setUsername(props.getMail().getUsername());
        sender.setPassword(props.getMail().getPassword());
        Properties mailProps = sender.getJavaMailProperties();
        mailProps.put("mail.smtp.auth", "true");
        mailProps.put("mail.smtp.starttls.enable", "true");
        this.mailSender = sender;
        this.habilitado = true;
    }

    /** Para el panel de salud del sistema (mejora 48). */
    public boolean isHabilitado() {
        return habilitado;
    }

    @Async
    public void enviar(String destinatario, String asunto, String cuerpo) {
        if (!habilitado || destinatario == null || destinatario.isBlank()) {
            log.warn("Email no enviado (deshabilitado o sin destinatario) — asunto: {}", asunto);
            return;
        }
        try {
            SimpleMailMessage msg = new SimpleMailMessage();
            msg.setFrom(props.getMail().getFrom());
            msg.setTo(destinatario);
            msg.setSubject(asunto);
            msg.setText(cuerpo);
            mailSender.send(msg);
        } catch (Exception e) {
            log.error("Fallo el envio de email a {}: {}", destinatario, e.getMessage());
        }
    }
}
