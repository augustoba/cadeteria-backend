package com.cadeteria.backend.service;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import ch.qos.logback.core.AppenderBase;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Los últimos errores que escribió el backend en su registro (2026-10-05, pantalla "Sistema"):
 * para verlos desde el panel sin entrar al servidor a leer {@code docker compose logs}. Quedan en
 * memoria (los últimos {@link #MAXIMO}) y se pierden al reiniciar: es una ventana para ver qué
 * está fallando ahora, no un archivo histórico.
 */
@Component
public class ErroresRecientes {

    static final int MAXIMO = 50;
    private static final int DIAS = 7;
    private static final int LARGO_MENSAJE = 400;
    private static final ZoneId ARGENTINA = ZoneId.of("America/Argentina/Buenos_Aires");

    public record ErrorRegistrado(Instant cuando, String origen, String mensaje, String excepcion) {}

    private final Deque<ErrorRegistrado> ultimos = new ArrayDeque<>();
    private final Map<LocalDate, Integer> porDia = new LinkedHashMap<>();
    private long total;
    private AppenderBase<ILoggingEvent> appender;

    @PostConstruct
    void conectar() {
        ILoggerFactory fabrica = LoggerFactory.getILoggerFactory();
        if (!(fabrica instanceof LoggerContext contexto)) return; // otro motor de registros: no hay de dónde leer
        appender = new AppenderBase<>() {
            @Override
            protected void append(ILoggingEvent evento) {
                if (!evento.getLevel().isGreaterOrEqual(Level.ERROR)) return;
                IThrowableProxy causa = evento.getThrowableProxy();
                registrar(Instant.ofEpochMilli(evento.getTimeStamp()), evento.getLoggerName(), evento.getFormattedMessage(),
                        causa == null ? null : causa.getClassName() + (causa.getMessage() == null ? "" : ": " + causa.getMessage()));
            }
        };
        appender.setContext(contexto);
        appender.setName("errores-recientes");
        appender.start();
        contexto.getLogger(Logger.ROOT_LOGGER_NAME).addAppender(appender);
    }

    @PreDestroy
    void desconectar() {
        if (appender == null) return;
        if (LoggerFactory.getILoggerFactory() instanceof LoggerContext contexto) {
            contexto.getLogger(Logger.ROOT_LOGGER_NAME).detachAppender(appender);
        }
        appender.stop();
    }

    synchronized void registrar(Instant cuando, String origen, String mensaje, String excepcion) {
        total++;
        porDia.merge(LocalDate.ofInstant(cuando, ARGENTINA), 1, Integer::sum);
        while (porDia.size() > DIAS) porDia.remove(porDia.keySet().iterator().next());
        ultimos.addFirst(new ErrorRegistrado(cuando, nombreCorto(origen), recortar(mensaje), recortar(excepcion)));
        while (ultimos.size() > MAXIMO) ultimos.removeLast();
    }

    /** Del más nuevo al más viejo. */
    public synchronized List<ErrorRegistrado> ultimos() {
        return new ArrayList<>(ultimos);
    }

    /** Errores por día (hora de Argentina) desde que arrancó el backend, hasta 7 días. */
    public synchronized Map<LocalDate, Integer> porDia() {
        return new LinkedHashMap<>(porDia);
    }

    public synchronized long total() {
        return total;
    }

    private static String nombreCorto(String logger) {
        return logger == null ? "" : logger.substring(logger.lastIndexOf('.') + 1);
    }

    private static String recortar(String texto) {
        if (texto == null) return null;
        return texto.length() <= LARGO_MENSAJE ? texto : texto.substring(0, LARGO_MENSAJE) + "…";
    }
}
