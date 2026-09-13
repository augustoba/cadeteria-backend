package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.ZonaDtos.ZonaRequest;
import com.cadeteria.backend.model.Zona;
import com.cadeteria.backend.repository.ZonaRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class ZonaService {

    private final ZonaRepository repo;

    public ZonaService(ZonaRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public List<Zona> findAll() {
        return repo.findAll();
    }

    @Transactional(readOnly = true)
    public Zona get(String id) {
        return repo.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Zona", id));
    }

    public Zona create(ZonaRequest req) {
        Zona z = new Zona();
        z.setId(UUID.randomUUID().toString());
        apply(z, req);
        return repo.save(z);
    }

    public Zona update(String id, ZonaRequest req) {
        Zona z = get(id);
        apply(z, req);
        return repo.save(z);
    }

    public void delete(String id) {
        repo.delete(get(id));
    }

    /** Desactivar/reactivar sin borrar (ronda 10, punto 99). */
    public Zona setActivo(String id, boolean activo) {
        Zona z = get(id);
        z.setActivo(activo);
        return repo.save(z);
    }

    /** Relacion simetrica: se inserta el par en ambos sentidos (diseno-tecnico.md sección 2). */
    public Zona agregarAdyacente(String zonaId, String zonaVecinaId) {
        Zona z = get(zonaId);
        Zona vecina = get(zonaVecinaId);
        z.getZonasAledanas().add(vecina);
        vecina.getZonasAledanas().add(z);
        repo.save(vecina);
        return repo.save(z);
    }

    public Zona quitarAdyacente(String zonaId, String zonaVecinaId) {
        Zona z = get(zonaId);
        Zona vecina = get(zonaVecinaId);
        z.getZonasAledanas().remove(vecina);
        vecina.getZonasAledanas().remove(z);
        repo.save(vecina);
        return repo.save(z);
    }

    private void apply(Zona z, ZonaRequest req) {
        z.setNombre(req.nombre().trim());
        z.setCentroLat(req.centroLat());
        z.setCentroLng(req.centroLng());
        z.setRadioM(req.radioM());
        z.setTarifaSugerida(req.tarifaSugerida());
        if (req.poligono() == null || req.poligono().size() < 3) {
            z.setPoligono(null);
        } else {
            String serializado = req.poligono().stream()
                    .map(p -> p.lat() + "," + p.lng())
                    .reduce((a, b) -> a + ";" + b)
                    .orElse(null);
            z.setPoligono(serializado);
        }
    }
}
