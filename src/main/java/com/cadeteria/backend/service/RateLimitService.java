package com.cadeteria.backend.service;

import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Deque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Límite de "ventana deslizante" en memoria (mejora 2026-09-17, pedida por el dueño para
 * frenar abuso de los endpoints públicos — spam de pedidos falsos, fuerza bruta de
 * códigos, martillar las APIs de geocoding). No hace falta Redis ni nada externo: corre
 * un solo backend, y perder el historial en un reinicio no es grave (a lo sumo alguien
 * gana una ventana extra justo después de reiniciar).
 */
@Service
public class RateLimitService {

    private final ConcurrentHashMap<String, Deque<Long>> historial = new ConcurrentHashMap<>();
    private final AtomicLong contadorLimpieza = new AtomicLong();

    /**
     * @return true si la acción identificada por {@code clave} está permitida ahora
     * (y queda registrada); false si ya se alcanzó el máximo dentro de la ventana.
     */
    public boolean permitir(String clave, int maxIntentos, Duration ventana) {
        long ahora = System.currentTimeMillis();
        long limite = ahora - ventana.toMillis();
        Deque<Long> deque = historial.computeIfAbsent(clave, k -> new ConcurrentLinkedDeque<>());
        synchronized (deque) {
            while (!deque.isEmpty() && deque.peekFirst() < limite) {
                deque.pollFirst();
            }
            if (deque.size() >= maxIntentos) {
                return false;
            }
            deque.addLast(ahora);
        }
        limpiarOcasionalmente();
        return true;
    }

    /** Cada tanto, saca del mapa las claves que ya quedaron vacías — evita crecer sin límite con IPs que no vuelven. */
    private void limpiarOcasionalmente() {
        if (contadorLimpieza.incrementAndGet() % 500 != 0) return;
        historial.entrySet().removeIf(e -> e.getValue().isEmpty());
    }
}
