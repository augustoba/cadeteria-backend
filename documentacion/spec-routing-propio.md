# Motor de ruteo propio y matching de trazas GPS — Documento de diseño

> **Estado:** Diseño aprobado 2026-09-22, implementación no arrancada.
> **Alcance:** dos subproyectos independientes, standalone, **sin linkear al backend todavía**.
> El backend actual sigue pidiendo distancia/ruta a OSRM público/GraphHopper/OpenRouteService
> (`RutaService`) hasta que se verifique que lo propio funciona bien.
> **Complementa a** [`spec-geocoding-cache.md`](./spec-geocoding-cache.md) §12 (los cadetes como
> mapeadores) — es la implementación concreta del "Beneficio 2" ahí mencionado (tiempos reales de
> viaje y red de calles), separado en fases manejables.

---

## 1. El problema

`RutaService` hoy depende de tres proveedores externos (OSRM demo público, GraphHopper,
OpenRouteService) para calcular distancia real por calle al cotizar (`CotizacionService`) y para
mostrarle la ruta sugerida al cadete. Son gratuitos pero:

- El demo público de OSRM **no tiene garantía de disponibilidad** (servidor comunitario).
- GraphHopper/ORS tienen **cupo diario** (500 y 2.500 req/día respectivamente).
- Ninguno usa **datos propios** de tráfico/tiempos reales de Tucumán.

Aparte, `MetricasService` calcula hoy un "km real" por pedido sumando **distancias en línea recta**
entre puntos de GPS consecutivos del cadete (`PedidoUbicacion`) — no sigue la calle, así que
sobreestima en cualquier tramo que no sea recto.

**Objetivo de este documento:** construir, de forma aislada y sin tocar el flujo de cotización
actual, la infraestructura para eventualmente (a) tener ruteo/distancia propios sin depender de
terceros y (b) aprovechar el GPS que ya mandan los cadetes para tener datos reales de tiempos de
viaje.

---

## 2. Qué ya existe en el código (relevado 2026-09-22)

| Pieza | Archivo | Qué hace hoy |
|---|---|---|
| Ruteo/distancia por calle | `service/RutaService.java` | OSRM público → GraphHopper → OpenRouteService, en ese orden; cae a línea recta si las tres fallan. |
| Cotización por distancia | `service/CotizacionService.java` | Usa `RutaService.resumenSiDisponible`, con fallback a `GeocodingService.distanciaKm` (haversine). |
| GPS del cadete por pedido | `service/CadeteService.java` (`registrarPuntoDeTrayecto`) + `model/PedidoUbicacion.java` | Cada ping de ubicación (~45 seg) guarda un punto **por cada pedido `EN_CURSO`** del cadete — ya soporta el caso de varios pedidos simultáneos (el mismo punto se tagea a todos los activos). No requiere cambios en la app de cadetes. |
| "Km real" actual | `service/MetricasService.java` (líneas ~190-198) | Suma haversine entre puntos consecutivos de `PedidoUbicacion` — **no sigue la calle real**, es una aproximación. |
| Mapeo pasivo de calles (geocoding) | `service/MapeoCallesCadetesService.java` | Job programado que muestrea la última posición del cadete cada tanto y alimenta la cache de **direcciones** (`cuadra_coords`) — no tiene relación con distancia/ruteo, es para geocoding. |

**Conclusión del relevamiento:** ya se junta gratis toda la materia prima (trazas GPS por pedido).
Falta: (A) un motor de ruteo propio para no depender de terceros, y (B) un proceso que use ese
motor para convertir las trazas crudas en distancia real por calle.

---

## 3. Subproyecto A — Motor de ruteo propio (OSRM auto-hospedado)

### 3.1 Decisiones

| Punto | Decisión | Por qué |
|---|---|---|
| Motor | **OSRM**, no Valhalla | `RutaService` ya habla el formato de respuesta de OSRM (lo usa hoy contra el servidor público) — el día que se decida linkear, el cambio es mínimo. Además trae `/match` (map-matching), que es justo lo que necesita el Subproyecto B. |
| Extracto de mapa | Argentina completa (Geofabrik) **recortada** a la provincia de Tucumán con `osmium` (bounding box) | Cubre todas las localidades de la provincia (decisión del dueño: "recorremos las localidades", no solo Gran San Miguel), sin cargar el país entero (mucho más liviano/rápido de procesar). |
| Perfiles | `car` y `bicycle` | Replican los dos perfiles que ya usa `RutaService` (`driving-car` / `cycling-regular` para MOTO/BICI). |
| Entorno | Docker Desktop (Windows, WSL2 por debajo) | No había Docker instalado; es el camino estándar para correr `osrm-backend` sin compilar nada a mano. |
| Motor de contracción | MLD (`osrm-partition` + `osrm-customize`) | Es el pipeline recomendado actual de OSRM (reemplazó a CH como default); soporta bien actualizaciones futuras del extracto. |

### 3.2 Estructura

Carpeta nueva **`routing/`** en la raíz del repo, standalone, fuera de `src/`:

```
routing/
  docker-compose.routing.yml   # levanta osrm-backend, puerto 5000
  scripts/
    descargar-extracto.sh      # baja el .pbf de Argentina (Geofabrik)
    recortar-tucuman.sh        # osmium extract con bbox de la provincia
    preparar-osrm.sh           # osrm-extract + osrm-partition + osrm-customize
  data/                        # .pbf y .osrm.* generados (gitignored, pesan GB)
  README.md                    # cómo levantarlo, probarlo con curl, y reconstruirlo
```

**Cero cambios en código Java.** `RutaService.java` no se toca en este subproyecto — el motor
queda corriendo aparte, expuesto en `localhost:5000`, y se prueba con `curl` directo:

```
curl "http://localhost:5000/route/v1/driving/-65.2226,-26.8083;-65.2176,-26.8241?overview=false"
```

### 3.3 Verificación

- Comparar manualmente 5-10 pares origen/destino conocidos contra lo que devuelve hoy
  `RutaService` (OSRM público) — las distancias deberían ser muy similares (mismo motor,
  distinto extracto/servidor).
- Confirmar que el perfil `bicycle` responde razonable para las mismas rutas.
- **No se linkea a `CotizacionService` ni a `RutaService` en este subproyecto.**

---

## 4. Subproyecto B — Pipeline de matching de trazas GPS

Usa el motor del Subproyecto A. Convierte las trazas crudas de `PedidoUbicacion` en distancia/ruta
real, vía el endpoint `/match` de OSRM (map-matching: "pegar" una secuencia de puntos GPS a las
calles reales).

### 4.1 Decisiones

| Punto | Decisión | Por qué |
|---|---|---|
| Disparo | Job programado (estilo `MapeoCallesCadetesService`), intervalo configurable desde Configuración | Mismo patrón ya validado en el código; no requiere cambios en la app de cadetes. |
| Qué procesa | Pedidos recién `ENTREGADO` (o estado final) con puntos en `PedidoUbicacion` sin procesar todavía | Solo tiene sentido matchear un recorrido completo, no uno en curso. |
| Dónde guarda el resultado | Tabla nueva `pedido_ruta_matcheada` (pedido, distanciaM, duracionS, geometría, calidad del match, timestamp) | **En paralelo** al cálculo naive existente en `MetricasService` — no lo reemplaza todavía. Permite comparar ambos antes de confiar en el matching. |
| Consumidores | **Ninguno por ahora.** No se lee desde `CotizacionService`, `RutaService` ni `MetricasService`. | Es justamente el requisito del dueño: construir sin linkear hasta verificar que funciona bien. |
| Fuera de alcance en esta fase | Agregación por tramo de calle (construir el grafo de tiempos "propio" que reemplazaría a `RutaService`) | Necesita mucho más volumen de datos acumulado del que hay hoy — queda documentado como paso siguiente (ver §6), no se implementa ahora. |

### 4.2 Flujo

```
Pedido pasa a ENTREGADO
        │
        ▼
Job periódico encuentra pedidos con PedidoUbicacion sin matchear
        │
        ▼
Arma la lista de puntos (lat, lng, timestamp) ordenada por capturadoEn
        │
        ▼
POST http://localhost:5000/match/v1/driving/{puntos}  (motor propio, Subproyecto A)
        │
        ▼
Guarda distanciaM, duracionS, geometría y calidad del match en pedido_ruta_matcheada
```

### 4.3 Verificación

- Comparar, para los mismos pedidos, el "km real" naive de `MetricasService` contra la distancia
  matcheada — debería ser igual o menor (la línea recta entre puntos sobreestima en curvas).
- Revisar visualmente algunas geometrías matcheadas (por ejemplo pegándolas en geojson.io) para
  confirmar que siguen calles reales y no saltan de forma rara.
- Vigilar la **calidad del match** que devuelve OSRM (confidence score) — trazas con pings muy
  espaciados (cada ~45 seg a velocidad de moto) pueden dar matches de baja confianza; si eso pasa
  seguido, es una señal a resolver antes de confiar en los datos (no en el alcance de esta fase).

---

## 5. Qué NO cambia con este trabajo

- `RutaService`, `CotizacionService` y `MetricasService` siguen funcionando exactamente igual que
  hoy. El backend real sigue pidiendo distancia a OSRM público/GraphHopper/ORS.
- No hay ningún cambio de comportamiento visible para el cliente, el cadete ni el admin.
- Todo lo de este documento es **infraestructura y datos en paralelo**, verificable de forma
  aislada, para decidir más adelante — con datos reales en la mano — si vale la pena linkearlo.

---

## 6. Fases de implementación sugeridas

**Fase A — Motor de ruteo propio**
- [ ] Instalar Docker Desktop.
- [ ] Carpeta `routing/` con `docker-compose.routing.yml` + scripts de descarga/recorte/build.
- [ ] Descargar extracto de Argentina, recortar a Tucumán provincia con `osmium`.
- [ ] Preparar OSRM (extract + partition + customize) con perfiles `car` y `bicycle`.
- [ ] README con instrucciones de arranque y ejemplos `curl`.
- [ ] **Hito:** motor propio respondiendo en `localhost:5000`, verificado contra OSRM público.

**Fase B — Pipeline de matching**
- [ ] Tabla `pedido_ruta_matcheada`.
- [ ] `TrazaMatchingService` (job programado, config de intervalo en Configuración).
- [ ] Llamada a `/match` del motor propio para pedidos `ENTREGADO` sin procesar.
- [ ] **Hito:** tabla poblándose sola con cada pedido entregado, comparable contra el km naive de
      `MetricasService`.

**Fase C (futura, fuera de este alcance) — Agregación por tramo**
- [ ] Con volumen suficiente acumulado, agregar por tramo de calle para construir el grafo de
      tiempos propio (el "Beneficio 2" de `spec-geocoding-cache.md` §12).
- [ ] Recién ahí evaluar linkear como fuente en `RutaService`.

---

## 7. Decisiones abiertas

1. **Umbral de "volumen suficiente"** para evaluar pasar a la Fase C — no definido todavía,
   depende de qué tan rápido crece `pedido_ruta_matcheada`.
2. **Qué hacer con matches de baja confianza** (pings muy espaciados) — descartar, reintentar con
   un `/match` con parámetros más tolerantes, o solo marcarlos y seguir. Se decide con datos reales
   en mano.
3. **Visibilidad en el panel admin** de los resultados de B (endpoint de solo lectura o vista) —
   no incluido en el alcance inicial, se suma si hace falta para revisar los datos más cómodo que
   por SQL directo.
