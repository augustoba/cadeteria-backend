package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.RecordatorioConfirmacion;
import com.cadeteria.backend.repository.CadeteRepository;
import com.cadeteria.backend.repository.RecordatorioConfirmacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Cartel "Antes de arrancar" de la app (2026-09-29): antes eran 3 textos fijos en la APK; ahora se
 * editan en Configuración → App del cadete. Sale una vez por login y el "Entendido" queda registrado.
 *
 * <p>Cada renglón va en su propia clave ({@code recordatorio_entrar_1} … {@code _6}) porque
 * {@code configuracion.valor} admite 500 caracteres. Si no hay ninguna de esas claves valen los
 * textos de siempre; si están y quedaron vacías, no se muestra nada.
 */
@Service
@Transactional
public class RecordatoriosAppService {

    public static final int MAX_RENGLONES = 6;
    public static final int MAX_CARACTERES = 150;
    public static final int MAX_TITULO = 60;

    static final String CLAVE_ACTIVO = "recordatorios_entrar_activo";
    static final String CLAVE_TITULO = "recordatorios_entrar_titulo";
    static final String PREFIJO_RENGLON = "recordatorio_entrar_";

    static final String TITULO_POR_DEFECTO = "Antes de arrancar";
    static final List<String> TEXTOS_POR_DEFECTO = List.of(
            "Llevá toda la documentación en regla (DNI, licencia, cédula del vehículo, seguro).",
            "No te olvides los elementos de seguridad: casco, cadena y mochila.",
            "Marcá cada viaje como \"Retirado\" al levantar el pedido, y \"Finalizado\" con los datos correspondientes al entregarlo.");

    /** Lo que ve el cadete. Sin renglones (o apagado) la app no muestra el cartel. */
    public record Recordatorios(boolean activo, String titulo, List<String> textos) {}

    private final ConfiguracionService configuracion;
    private final CadeteRepository cadeteRepo;
    private final RecordatorioConfirmacionRepository repo;

    public RecordatoriosAppService(ConfiguracionService configuracion, CadeteRepository cadeteRepo,
                                   RecordatorioConfirmacionRepository repo) {
        this.configuracion = configuracion;
        this.cadeteRepo = cadeteRepo;
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Recordatorios actuales() {
        return desde(configuracion.findAll());
    }

    public static Recordatorios desde(Map<String, String> valores) {
        boolean activo = !"false".equalsIgnoreCase(valores.getOrDefault(CLAVE_ACTIVO, "true").trim());
        String titulo = valores.getOrDefault(CLAVE_TITULO, "").trim();
        if (titulo.isEmpty()) titulo = TITULO_POR_DEFECTO;
        boolean editados = false;
        List<String> textos = new ArrayList<>();
        for (int i = 1; i <= MAX_RENGLONES; i++) {
            String valor = valores.get(PREFIJO_RENGLON + i);
            if (valor == null) continue;
            editados = true;
            if (!valor.isBlank()) textos.add(valor.trim());
        }
        return new Recordatorios(activo, titulo, editados ? textos : TEXTOS_POR_DEFECTO);
    }

    /** Lo llama Configuración antes de guardar: el cartel tiene que entrar en una pantalla de celular. */
    public static void validar(String clave, String valor) {
        if (CLAVE_TITULO.equals(clave) && valor.trim().length() > MAX_TITULO) {
            throw new BadRequestException("El título puede tener hasta " + MAX_TITULO + " caracteres.");
        }
        if (clave.startsWith(PREFIJO_RENGLON)) {
            int numero;
            try {
                numero = Integer.parseInt(clave.substring(PREFIJO_RENGLON.length()));
            } catch (NumberFormatException e) {
                throw new BadRequestException("Ese renglón no existe.");
            }
            if (numero < 1 || numero > MAX_RENGLONES) {
                throw new BadRequestException("Puede haber hasta " + MAX_RENGLONES + " renglones.");
            }
            if (valor.trim().length() > MAX_CARACTERES) {
                throw new BadRequestException("Cada renglón puede tener hasta " + MAX_CARACTERES + " caracteres.");
            }
        }
    }

    /** "Entendido": guarda qué se le mostró en ese momento. */
    public RecordatorioConfirmacion confirmar(String cadeteUsername) {
        Cadete cadete = cadeteRepo.findByUsername(cadeteUsername)
                .orElseThrow(() -> ResourceNotFoundException.of("Cadete", cadeteUsername));
        Recordatorios r = actuales();
        RecordatorioConfirmacion c = new RecordatorioConfirmacion();
        c.setId(UUID.randomUUID().toString());
        c.setCadete(cadete);
        c.setConfirmadoEn(Instant.now());
        c.setTitulo(r.titulo());
        c.setTextos(String.join("\n", r.textos()));
        return repo.save(c);
    }

    /** Ficha del cadete: los últimos 10 "Entendido". */
    @Transactional(readOnly = true)
    public List<RecordatorioConfirmacion> confirmacionesDe(String cadeteId) {
        return repo.findTop10ByCadeteIdOrderByConfirmadoEnDesc(cadeteId);
    }
}
