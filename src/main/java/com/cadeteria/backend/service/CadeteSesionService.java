package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteSesion;
import com.cadeteria.backend.repository.CadeteSesionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Registra cuánto tiempo estuvo "online" cada cadete (LIBRE u OCUPADO, no
 * DESCONECTADO) — para las métricas del panel (horas online por cadete). Se abre una
 * sesión al salir de DESCONECTADO y se cierra al volver a DESCONECTADO;
 * LIBRE↔OCUPADO durante un viaje no abre/cierra nada, la sesión sigue corrida.
 */
@Service
@Transactional
public class CadeteSesionService {

    private final CadeteSesionRepository repo;

    public CadeteSesionService(CadeteSesionRepository repo) {
        this.repo = repo;
    }

    /** Idempotente: si ya hay una sesión abierta para el cadete, no hace nada. */
    public void abrir(Cadete cadete) {
        if (repo.findFirstByCadeteIdAndDesconectadoEnIsNull(cadete.getId()).isPresent()) return;
        CadeteSesion s = new CadeteSesion();
        s.setId(UUID.randomUUID().toString());
        s.setCadete(cadete);
        s.setConectadoEn(Instant.now());
        repo.save(s);
    }

    /** No hace nada si no había ninguna sesión abierta. */
    public void cerrar(Cadete cadete) {
        repo.findFirstByCadeteIdAndDesconectadoEnIsNull(cadete.getId())
                .ifPresent(s -> {
                    s.setDesconectadoEn(Instant.now());
                    repo.save(s);
                });
    }

    /** Horas online del cadete dentro de [desde, hasta), recortando las sesiones a ese rango. */
    @Transactional(readOnly = true)
    public double horasOnline(String cadeteId, Instant desde, Instant hasta) {
        List<CadeteSesion> sesiones = repo.findSolapadas(cadeteId, desde, hasta);
        long segundos = 0;
        for (CadeteSesion s : sesiones) {
            Instant inicio = s.getConectadoEn().isBefore(desde) ? desde : s.getConectadoEn();
            Instant finReal = s.getDesconectadoEn() == null ? Instant.now() : s.getDesconectadoEn();
            Instant fin = finReal.isAfter(hasta) ? hasta : finReal;
            if (fin.isAfter(inicio)) {
                segundos += Duration.between(inicio, fin).getSeconds();
            }
        }
        return segundos / 3600.0;
    }
}
