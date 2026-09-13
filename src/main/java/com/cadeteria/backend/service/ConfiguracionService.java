package com.cadeteria.backend.service;

import com.cadeteria.backend.model.Configuracion;
import com.cadeteria.backend.repository.ConfiguracionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.Map;

/** Parametros globales editables desde el panel admin, sin hardcodear (diseno-tecnico.md sección 3/7/8). */
@Service
@Transactional
public class ConfiguracionService {

    private final ConfiguracionRepository repo;

    public ConfiguracionService(ConfiguracionRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Map<String, String> findAll() {
        Map<String, String> valores = new LinkedHashMap<>();
        repo.findAll().forEach(c -> valores.put(c.getClave(), c.getValor()));
        return valores;
    }

    public void set(String clave, String valor) {
        Configuracion c = repo.findById(clave).orElseGet(Configuracion::new);
        c.setClave(clave);
        c.setValor(valor);
        repo.save(c);
    }

    @Transactional(readOnly = true)
    public boolean getBoolean(String clave, boolean porDefecto) {
        return repo.findById(clave).map(c -> Boolean.parseBoolean(c.getValor())).orElse(porDefecto);
    }

    /** Para valores de texto largo editables desde Configuración (ej. plantillas de SMS, ronda 10, punto 106). */
    @Transactional(readOnly = true)
    public String getString(String clave, String porDefecto) {
        return repo.findById(clave).map(c -> c.getValor() == null || c.getValor().isBlank() ? porDefecto : c.getValor()).orElse(porDefecto);
    }

    @Transactional(readOnly = true)
    public int getInt(String clave, int porDefecto) {
        return repo.findById(clave).map(c -> {
            try {
                return Integer.parseInt(c.getValor());
            } catch (NumberFormatException e) {
                return porDefecto;
            }
        }).orElse(porDefecto);
    }

    @Transactional(readOnly = true)
    public BigDecimal getBigDecimal(String clave, BigDecimal porDefecto) {
        return repo.findById(clave).map(c -> {
            try {
                return new BigDecimal(c.getValor());
            } catch (NumberFormatException e) {
                return porDefecto;
            }
        }).orElse(porDefecto);
    }

    /**
     * Número correlativo simple para los pedidos (arranca en 1500000, ver
     * DataSeeder.seedConfiguracion): lee "proximo_numero_pedido", lo devuelve, y deja
     * guardado el siguiente. No usa un lock explícito — a este volumen (~100
     * pedidos/día, un solo admin) alcanza con la atomicidad de la transacción que
     * envuelve a PedidoService.crear().
     */
    public long siguienteNumeroPedido() {
        long actual = getLong("proximo_numero_pedido", 1_500_000L);
        set("proximo_numero_pedido", String.valueOf(actual + 1));
        return actual;
    }

    private long getLong(String clave, long porDefecto) {
        return repo.findById(clave).map(c -> {
            try {
                return Long.parseLong(c.getValor());
            } catch (NumberFormatException e) {
                return porDefecto;
            }
        }).orElse(porDefecto);
    }
}
