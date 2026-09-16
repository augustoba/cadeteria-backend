package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "zona")
public class Zona {

    @Id
    private String id;

    @Column(nullable = false)
    private String nombre;

    @Column(name = "centro_lat", nullable = false)
    private Double centroLat;

    @Column(name = "centro_lng", nullable = false)
    private Double centroLng;

    @Column(name = "radio_m", nullable = false)
    private Integer radioM;

    /** Precio sugerido al cargar un pedido en esta zona — null = sin sugerencia (ronda 6, punto 36). */
    @Column(precision = 12, scale = 2)
    private java.math.BigDecimal tarifaSugerida;

    /**
     * Polígono libre en vez de círculo (ronda 3, punto 27) — puntos "lat,lng" separados
     * por ";" (ej. "-26.82,-65.20;-26.83,-65.21;..."), en orden, sin cerrar (el último
     * se une al primero). Null o con menos de 3 puntos = seguir usando el círculo
     * centroLat/centroLng/radioM, que además siempre se guarda como fallback y como
     * referencia visual del centro de la zona.
     */
    @Lob
    private String poligono;

    /** Desactivar temporalmente sin borrar (ronda 10, punto 99) — no participa de la asignación mientras esté en false. */
    @Column(nullable = false)
    private boolean activo = true;

    /**
     * Relación simétrica: al marcar dos zonas como aledañas se inserta el par en ambos
     * sentidos (ver diseno-tecnico.md sección 2).
     */
    @ManyToMany
    @JoinTable(
            name = "zona_adyacente",
            joinColumns = @JoinColumn(name = "zona_id"),
            inverseJoinColumns = @JoinColumn(name = "zona_vecina_id")
    )
    private Set<Zona> zonasAledanas = new LinkedHashSet<>();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Double getCentroLat() {
        return centroLat;
    }

    public void setCentroLat(Double centroLat) {
        this.centroLat = centroLat;
    }

    public Double getCentroLng() {
        return centroLng;
    }

    public void setCentroLng(Double centroLng) {
        this.centroLng = centroLng;
    }

    public Integer getRadioM() {
        return radioM;
    }

    public void setRadioM(Integer radioM) {
        this.radioM = radioM;
    }

    public java.math.BigDecimal getTarifaSugerida() {
        return tarifaSugerida;
    }

    public void setTarifaSugerida(java.math.BigDecimal tarifaSugerida) {
        this.tarifaSugerida = tarifaSugerida;
    }

    public Set<Zona> getZonasAledanas() {
        return zonasAledanas;
    }

    public void setZonasAledanas(Set<Zona> zonasAledanas) {
        this.zonasAledanas = zonasAledanas;
    }

    public String getPoligono() {
        return poligono;
    }

    public void setPoligono(String poligono) {
        this.poligono = poligono;
    }

    public boolean isActivo() {
        return activo;
    }

    public void setActivo(boolean activo) {
        this.activo = activo;
    }

    public List<double[]> getPuntosPoligono() {
        List<double[]> puntos = new ArrayList<>();
        if (poligono == null || poligono.isBlank()) {
            return puntos;
        }
        for (String par : poligono.split(";")) {
            String[] latLng = par.split(",");
            if (latLng.length != 2) continue;
            try {
                puntos.add(new double[]{Double.parseDouble(latLng[0].trim()), Double.parseDouble(latLng[1].trim())});
            } catch (NumberFormatException ignored) {
                // punto corrupto, se ignora en vez de romper toda la zona
            }
        }
        return puntos;
    }

    /** true si el punto cae dentro del polígono (si hay uno cargado) o del círculo, si no. */
    public boolean contienePunto(double lat, double lng, double distanciaAlCentroM) {
        List<double[]> puntos = getPuntosPoligono();
        if (puntos.size() >= 3) {
            return dentroDelPoligono(puntos, lat, lng);
        }
        return radioM != null && distanciaAlCentroM <= radioM;
    }

    /** Ray casting estándar (par/impar de cruces del rayo horizontal desde el punto). */
    private static boolean dentroDelPoligono(List<double[]> puntos, double lat, double lng) {
        boolean dentro = false;
        int n = puntos.size();
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double latI = puntos.get(i)[0], lngI = puntos.get(i)[1];
            double latJ = puntos.get(j)[0], lngJ = puntos.get(j)[1];
            boolean cruza = ((lngI > lng) != (lngJ > lng))
                    && (lat < (latJ - latI) * (lng - lngI) / (lngJ - lngI) + latI);
            if (cruza) dentro = !dentro;
        }
        return dentro;
    }

    /**
     * Área aproximada (en grados² — no metros², pero alcanza para comparar entre zonas de
     * esta misma cadetería, todas en una zona geográfica chica donde la distorsión de
     * latitud es despreciable). Sirve para {@link GeocodingService#resolverZona} cuando un
     * punto cae dentro de varias zonas a la vez (ej. zonas concéntricas tipo "4 avenidas"
     * dentro de una zona más grande que las rodea): gana la más chica, no una zona
     * arbitraria por orden de lista.
     */
    public double aproxArea() {
        List<double[]> puntos = getPuntosPoligono();
        if (puntos.size() >= 3) {
            double shoelace = 0;
            int n = puntos.size();
            for (int i = 0, j = n - 1; i < n; j = i++) {
                shoelace += puntos.get(j)[1] * puntos.get(i)[0] - puntos.get(i)[1] * puntos.get(j)[0];
            }
            return Math.abs(shoelace) / 2.0;
        }
        double radioGrados = (radioM != null ? radioM : 0) / 111_000.0;
        return Math.PI * radioGrados * radioGrados;
    }
}
