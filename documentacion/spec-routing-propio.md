# Motor de ruteo propio y matching de trazas GPS — Documento de diseño

> **Estado:** Diseño aprobado 2026-09-22, implementación no arrancada.
> **Corrección 2026-09-22 (ubicación):** los dos subproyectos NO viven dentro del repo del backend
> (`cadeteria/`) — van como carpetas propias en la raíz del workspace (`C:\proyectos\cadeteria\`),
> mismo patrón que `whatsapp-gateway/` (repo Git propio, proceso standalone). Esto además simplifica
> el Subproyecto B: en vez de ser un job embebido en el backend Spring, es un servicio Node.js
> aparte que lee/escribe **directo en la misma base MySQL** — así el backend sigue con **cero
> cambios de código**, ni siquiera un endpoint nuevo. Detalle en §3.2 y §4.
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

Carpeta nueva **`routing/`**, repo Git propio en la raíz del workspace
(`C:\proyectos\cadeteria\routing\`, hermana de `cadeteria/`, `whatsapp-gateway/`, etc.) — **no**
dentro del repo del backend:

```
routing/                       (repo Git propio)
  docker-compose.routing.yml   # levanta osrm-backend, puertos 5000 (car) y 5001 (bicycle)
  scripts/
    01-descargar-extracto.sh   # baja el .pbf de Argentina (Geofabrik)
    02-recortar-tucuman.sh     # osmium extract con bbox de la provincia
    03-preparar-perfil.sh      # osrm-extract + osrm-partition + osrm-customize, por perfil
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
| Dónde vive | Carpeta/repo propio **`traza-matching/`** en la raíz del workspace (hermana de `routing/`, `cadeteria/`, `whatsapp-gateway/`) — **no** dentro del backend | Corrección 2026-09-22: mismo patrón que `whatsapp-gateway` (proceso standalone), no un job embebido en el Spring Boot del backend. |
| Cómo lee/escribe los datos | **Conexión directa a la misma base MySQL** (`cadeteria`, `localhost:3306`, mismas credenciales `DB_USER`/`DB_PASSWORD` que ya usa el backend) — lee `pedido`, `pedido_ubicacion`, `tipo_vehiculo`; escribe solo en la tabla nueva `pedido_ruta_matcheada`, que este servicio crea y es dueño de ella | Evita agregar **cualquier** endpoint nuevo al backend — cero cambios de código Java, ni de infraestructura de auth (`/api/interno/` no existe todavía y no hace falta crearlo para esto). Es consistente con la instrucción del dueño de no tocar el backend hasta verificar que funciona. |
| Stack | Node.js | Mismo stack que `whatsapp-gateway/` — reutiliza la convención ya establecida del proyecto para procesos standalone, sin sumar un segundo runtime distinto (ej. Python) sin necesidad. |
| Disparo | Polling con intervalo propio (`setInterval`, configurable por variable de entorno) | Equivalente al patrón `@Scheduled` + `ConfiguracionService` del backend (`MapeoCallesCadetesService`), pero como este servicio no vive en el backend, no tiene acceso a la tabla `configuracion` — se configura por entorno, como ya hace `whatsapp-gateway` con sus propias variables. |
| Qué procesa | Pedidos recién `FINALIZADO` (estado terminal exitoso real en `estado_pedido` — corrección 2026-09-22, se llamaba `ENTREGADO` en versiones previas de este documento y ese estado no existe) con puntos en `pedido_ubicacion` sin procesar todavía | Solo tiene sentido matchear un recorrido completo, no uno en curso. |
| Dónde guarda el resultado | Tabla nueva `pedido_ruta_matcheada` (pedido_id, distancia_m, duracion_s, geometria_geojson, confianza, procesado_en) — creada por este servicio (`CREATE TABLE IF NOT EXISTS` al arrancar, no vía Hibernate) | **En paralelo** al cálculo naive existente en `MetricasService` — no lo reemplaza todavía. Permite comparar ambos antes de confiar en el matching. |
| Consumidores | **Ninguno por ahora.** El backend no lee esta tabla ni sabe que existe. | Es justamente el requisito del dueño: construir sin linkear hasta verificar que funciona bien. |
| Fuera de alcance en esta fase | Agregación por tramo de calle (construir el grafo de tiempos "propio" que reemplazaría a `RutaService`) | Necesita mucho más volumen de datos acumulado del que hay hoy — queda documentado como paso siguiente (ver §6), no se implementa ahora. |

### 4.2 Flujo

```
Pedido pasa a FINALIZADO (en el backend, sin cambios)
        │
        ▼
traza-matching/ (proceso Node aparte) hace polling cada N segundos:
  SELECT pedidos FINALIZADO recientes sin fila en pedido_ruta_matcheada
        │
        ▼
Por cada uno: SELECT sus puntos en pedido_ubicacion, ordenados por capturado_en
        │
        ▼
POST http://localhost:5000/match/v1/driving/{puntos}   (perfil car)
POST http://localhost:5001/match/v1/driving/{puntos}   (perfil bicycle, según tipo_vehiculo)
        │
        ▼
INSERT distancia_m, duracion_s, geometria_geojson, confianza en pedido_ruta_matcheada
```

### 4.3 Verificación

- Comparar, para los mismos pedidos, el "km real" naive de `MetricasService` contra la distancia
  matcheada — debería ser igual o mayor (la línea recta entre puntos GPS nunca sobreestima
  respecto a seguir la calle real; corrección 2026-09-22, la versión previa de este punto lo
  tenía invertido).
- Revisar visualmente algunas geometrías matcheadas (por ejemplo pegándolas en geojson.io) para
  confirmar que siguen calles reales y no saltan de forma rara.
- Vigilar la **calidad del match** que devuelve OSRM (confidence score) — trazas con pings muy
  espaciados (cada ~45 seg a velocidad de moto) pueden dar matches de baja confianza; si eso pasa
  seguido, es una señal a resolver antes de confiar en los datos (no en el alcance de esta fase).

---

## 5. Qué NO cambia con este trabajo

- **Cero cambios en el repo del backend (`cadeteria/`).** Ni un archivo — `RutaService`,
  `CotizacionService` y `MetricasService` siguen funcionando exactamente igual que hoy, y no se
  agrega ningún endpoint nuevo. El backend real sigue pidiendo distancia a OSRM público/
  GraphHopper/ORS.
- No hay ningún cambio de comportamiento visible para el cliente, el cadete ni el admin.
- Todo lo de este documento vive en dos carpetas/repos nuevos y standalone (`routing/`,
  `traza-matching/`), verificable de forma aislada, para decidir más adelante — con datos reales
  en la mano — si vale la pena linkearlo.

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
- [ ] Repo `traza-matching/` (Node.js) con conexión directa a MySQL y creación de
      `pedido_ruta_matcheada` (`CREATE TABLE IF NOT EXISTS`, no vía Hibernate/backend).
- [ ] Polling configurable por variable de entorno, misma idea que `MapeoCallesCadetesService`.
- [ ] Llamada a `/match` del motor propio (Subproyecto A) para pedidos `FINALIZADO` sin procesar.
- [ ] **Hito:** tabla poblándose sola con cada pedido entregado, comparable contra el km naive de
      `MetricasService` — sin ningún cambio en el repo del backend.

**Estado real (2026-09-22): ambas fases completas y verificadas en vivo** — ver
`routing/README.md` y `traza-matching/README.md` para el detalle de la verificación end-to-end
(motor propio corriendo, pipeline matcheando pedidos de prueba con distancia mayor a la naive
haversine, como se esperaba).

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
