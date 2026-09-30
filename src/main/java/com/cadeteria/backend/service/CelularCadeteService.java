package com.cadeteria.backend.service;

import com.cadeteria.backend.common.ForbiddenException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.repository.CadeteRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Un celular por cadete (pedido del usuario, 2026-09-29): que no le pase usuario y contraseña a otro para
 * que trabaje por él. Android no deja leer el IMEI, así que la app manda un identificador propio de ese
 * celular (ANDROID_ID: sobrevive a reinstalar la app, cambia con un reseteo de fábrica).
 * <ul>
 *   <li>El primer login lo vincula. Desde otro celular no entra, y el intento queda en la ficha.</li>
 *   <li>"Habilitar nuevo celular" (panel) borra el vinculado y cierra su sesión: el próximo login queda
 *       como el único. Nunca hay dos habilitados.</li>
 *   <li>APK vieja (no manda el identificador): entra igual. Para obligar a actualizar: version_minima_app.</li>
 *   <li>Con {@link #CLAVE_ACTIVO} apagado en Configuración no bloquea, solo anota los intentos.</li>
 * </ul>
 */
@Service
public class CelularCadeteService {

    private static final Logger log = LoggerFactory.getLogger(CelularCadeteService.class);

    public static final String CLAVE_ACTIVO = "celular_unico_activo";
    static final String OTRO_CELULAR = "Esta cuenta está vinculada a otro celular. Si cambiaste de celular, "
            + "pedile a la cadetería que habilite el nuevo.";

    private final CadeteRepository cadeteRepo;
    private final ConfiguracionService configuracion;

    public CelularCadeteService(CadeteRepository cadeteRepo, ConfiguracionService configuracion) {
        this.cadeteRepo = cadeteRepo;
        this.configuracion = configuracion;
    }

    /** Lo llama el login del cadete, ya con la contraseña validada y antes de emitir el token. */
    public void controlarAlLoguear(Cadete cadete, String celularId, String modelo) {
        if (celularId == null || celularId.isBlank()) return;
        String id = celularId.trim();
        String modeloLimpio = modelo == null || modelo.isBlank() ? null : recortar(modelo.trim(), 120);
        if (cadete.getCelularId() == null) {
            cadete.setCelularId(recortar(id, 100));
            cadete.setCelularModelo(modeloLimpio);
            cadete.setCelularVinculadoEn(Instant.now());
            log.info("Cadete {}: celular vinculado ({}).", cadete.getUsername(), modeloLimpio);
            return;
        }
        if (cadete.getCelularId().equals(recortar(id, 100))) {
            if (modeloLimpio != null) cadete.setCelularModelo(modeloLimpio);
            return;
        }
        cadete.setIntentosOtroCelular(cadete.getIntentosOtroCelular() + 1);
        cadete.setUltimoIntentoOtroCelularEn(Instant.now());
        cadete.setUltimoIntentoOtroCelularModelo(modeloLimpio);
        cadeteRepo.save(cadete);
        log.warn("Cadete {}: intento de entrar desde otro celular ({}; el vinculado es {}).",
                cadete.getUsername(), modeloLimpio, cadete.getCelularModelo());
        if (configuracion.getBoolean(CLAVE_ACTIVO, true)) {
            throw new ForbiddenException(OTRO_CELULAR);
        }
    }

    /** "Habilitar nuevo celular": borra el vinculado y cierra su sesión (el JWT viejo deja de valer). */
    @Transactional
    public Cadete habilitarNuevoCelular(String cadeteId) {
        Cadete cadete = cadeteRepo.findById(cadeteId).orElseThrow(() -> ResourceNotFoundException.of("Cadete", cadeteId));
        log.info("Cadete {}: se habilita un celular nuevo (se desvincula {}).", cadete.getUsername(), cadete.getCelularModelo());
        cadete.setCelularId(null);
        cadete.setCelularModelo(null);
        cadete.setCelularVinculadoEn(null);
        cadete.setSessionToken(UUID.randomUUID().toString());
        return cadeteRepo.save(cadete);
    }

    @Transactional(readOnly = true)
    public Cadete buscar(String cadeteId) {
        return cadeteRepo.findById(cadeteId).orElseThrow(() -> ResourceNotFoundException.of("Cadete", cadeteId));
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
