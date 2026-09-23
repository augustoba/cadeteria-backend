# Spec — Asignación por distancia (sin zona) + tope de BICI al retiro

Fecha: 2026-09-22. Estado: **propuesta, pendiente de revisión** — nada de esto está
implementado.

Alcance: 3 repos — `cadeteria` (backend), `admin-front` (panel), `cadete-app` (solo un DTO,
ver sección 9).

---

## 1. El pedido original

Faltaba un tope de distancia para cadetes en BICI: hoy `distancia_maxima_bici_km` ya bloquea
la asignación automática si el **viaje** (origen→destino) supera esa distancia, pero no existe
ningún tope sobre la distancia entre **dónde está el cadete y el punto de retiro** — un
cadete en bici puede terminar matcheado con un pedido cuyo origen está a varios km de él, si
cae en su misma zona o es el más cercano por GPS en el fallback.

Al charlarlo surgieron dos cambios más, todos con el mismo espíritu (reemplazar zona por
distancia, ya decidido para la cotización — ver `CotizacionService`, sección 7):

1. El pedido dejó de necesitar una **Zona** cargada a mano — nunca se usó para cotizar (ver
   sección 7) y ahora tampoco hace falta para asignar.
2. El campo "tipo de vehículo requerido" (MOTO/BICI, obligatorio hoy) no es en realidad un
   requisito del pedido — es la forma en que hoy el admin fuerza "esto va con moto porque es
   lejos". Pasa a ser un checkbox opcional **"Requiere moto"**: tildado, solo motos; sin
   tildar, cualquiera (moto o bici), sujeto a los topes de distancia de bici.

## 2. Qué existe ya (importante: no reconstruir)

| Pieza | Dónde | Estado |
| --- | --- | --- |
| Tope de viaje para BICI (origen→destino) | `PedidoService.viajeSuperaTopeDeBici`, config `distancia_maxima_bici_km` | Funciona, pero hoy es "todo o nada": si el pedido "requiere BICI" y el viaje supera el tope, no se ofrece a **nadie** (ni a motos). Se corrige en sección 4. |
| Selector de método de cotización (Automático/Zona/Distancia) | `CotizacionService`, panel Configuración → Tarifas | Funciona, completo. Ya soporta "Siempre por km" — la cotización por distancia no es código nuevo. |
| Matching por zona del cadete (`zonaActual`) | `PedidoService.buscarCandidato`, método `zonasCompatibles` | Se elimina (sección 4) — el matching pasa a ser 100% por distancia GPS. |
| Agrupar pedidos "de la misma zona" en un lote | `PedidoService.asignarLote`, `dashboard.component.ts: loteZonaMezclada()` | Se rompe si sacamos zona del pedido — se reemplaza por cercanía entre orígenes (sección 5). |

## 3. Modelo de datos

- **`Pedido`**: se saca `zona` como requisito (`@ManyToOne(optional=false)` → `optional=true`,
  columna `zona_id` pasa a nullable). Se saca `tipoVehiculoRequerido` (la relación con
  `TipoVehiculo`) por completo y se agrega `boolean requiereMoto` (default `false`, columna
  nueva). Nada se borra de pedidos viejos: conservan su zona y (en la columna vieja,
  ya no mapeada) su tipo de vehículo.
- **`SolicitudPedido`**: `zona` ya es nullable hoy (`@ManyToOne` sin `optional=false`) — se
  deja de mapear, sin necesidad de tocar el esquema. `tipoVehiculoRequerido` se reemplaza por
  `boolean requiereMoto` (columna nueva, sin backfill: son solicitudes de vida corta, se
  resuelven en pedido enseguida).
- **Migración de esquema** (MySQL, `ddl-auto: update` no relaja NOT NULL solo): un runner de
  arranque, mismo estilo que los `*Seeder` existentes en `config/`, ejecuta en cada boot
  (idempotente, no rompe si ya corrió):
  ```sql
  ALTER TABLE pedido MODIFY COLUMN zona_id VARCHAR(36) NULL;
  ALTER TABLE pedido MODIFY COLUMN tipo_vehiculo_requerido_id VARCHAR(36) NULL;
  UPDATE pedido SET requiere_moto = (tipo_vehiculo_requerido_id = 'MOTO')
      WHERE tipo_vehiculo_requerido_id IS NOT NULL;
  ```
  El `WHERE` de la última línea es lo que hace esto seguro de correr siempre: los pedidos
  nuevos nunca cargan `tipo_vehiculo_requerido_id`, así que el backfill nunca los toca.
  `TipoVehiculo` como entidad no se toca — sigue siendo el tipo de vehículo real del cadete
  (`Cadete.tipoVehiculo`, obligatorio, sin cambios).

## 4. Matching automático (`PedidoService.buscarCandidato`)

- Se elimina la etapa "buscar primero en la misma zona" (bloque `enZona`) y el método privado
  `zonasCompatibles`, que queda sin otro uso. El matching pasa a ser **siempre** la lógica de
  fallback que ya existía: el elegible más cercano por distancia GPS en línea recta al origen
  del pedido.
- Filtro de vehículo: `!pedido.isRequiereMoto() || "MOTO".equals(cadete.getTipoVehiculo().getId())`.
  Sin requisito, entran motos y bicis por igual.
- `viajeSuperaTopeDeBici` deja de cortar la búsqueda entera (`return Optional.empty()` al
  principio de `buscarCandidato`) y pasa a ser un filtro **por candidato**, aplicado solo
  a cadetes en BICI, junto con el nuevo tope de retiro:
  ```java
  .filter(c -> !"BICI".equals(c.getTipoVehiculo().getId()) || (
          dentroDelTopeDeViajeBici(pedido) && dentroDelTopeDeRetiroBici(c, pedido)))
  ```
  Nueva config `distancia_maxima_bici_retiro_km` (default `0` = sin límite, mismo patrón que
  `distancia_maxima_bici_km`): distancia Haversine entre la ubicación actual del cadete
  (`c.getLat()/getLng()`) y el origen del pedido.
- Consecuencia esperada, no un bug: como el tope de viaje ya no bloquea a todo el mundo, un
  pedido largo que hoy no le queda ofrecido a nadie (por estar tildado BICI) ahora sí se
  ofrece — a una moto.
- Efecto colateral a tener en cuenta: como el matching pasa a ser 100% por GPS, un cadete sin
  ubicación cargada ya no es candidato de la asignación automática (antes sí podía matchear
  por zona sin GPS). Sigue pudiendo asignarse a mano vía `candidatosValidos`, que no cambia
  (ya era agnóstico a zona/vehículo, "a propósito").

## 5. Asignación en lote (`asignarLote` + `loteZonaMezclada`)

Agrupa pedidos "de la misma zona (o zonas aledañas)" en una sola tanda de ofertas a un
cadete. Sin zona, se reemplaza por cercanía entre orígenes: nueva config
`distancia_maxima_lote_km` (default `3`) — todos los orígenes del lote tienen que estar,
cada uno, a esa distancia o menos del origen del primer pedido del lote. Mismo chequeo
duplicado en el front (`dashboard.component.ts: loteZonaMezclada()`) para deshabilitar el
botón antes de mandar el request, ahora comparando distancias en vez de `zona.id`.

## 6. DTOs y alta de pedido (dos entradas: admin directo y revisión de solicitud)

- `PedidoRequest`: se saca `zonaId`. `tipoVehiculoRequeridoId` (String, `@NotBlank`) se
  reemplaza por `requiereMoto` (boolean).
- `PedidoResponse` / `SolicitudPedidoResponse`: el campo `zona` (`LookupResponse`) se saca;
  `tipoVehiculoRequerido` (`LookupResponse`) se reemplaza por `requiereMoto` (boolean).
- `PedidoService.crear`: ya no busca `Zona` ni `TipoVehiculo` — `p.setRequiereMoto(req.requiereMoto())`.
- `PedidoService.repetirPorToken`: hoy arma el `PedidoRequest` con
  `original.getZona().getId()` y `original.getTipoVehiculoRequerido().getId()` — se
  reemplaza por `original.isRequiereMoto()`, sin zona.
- `SolicitudPedidoDtos.RevisarSolicitudRequest`: se sacan `zonaId` y `tipoVehiculoRequeridoId`,
  se agrega `requiereMoto` (boolean).
- `SolicitudPedidoService.confirmarDirecto` / `.cotizar`: pierden los parámetros
  `zonaId`/`tipoVehiculoId` y sus lookups; reciben `requiereMoto` y lo pasan directo.
- `crearPedidoDesde`: arma el `PedidoRequest` con `s.isRequiereMoto()`, sin zona.
- `SolicitudPedidoAdminController`: actualiza la firma de los dos endpoints que llaman a
  `confirmarDirecto`/`cotizar`.

## 7. Qué NO se toca

Entidad `Zona`, pantalla de Zonas del panel, `ZonaController`/`ZonaService`/`ZonaDtos`,
cotización por zona (`CotizacionService`, sigue soportando `ZONA`/`AUTOMATICO`/`DISTANCIA`),
`Cadete.zonaActual` (sigue actualizándose solo, pasa a ser puramente informativo en el panel),
y el reporte de métricas por zona (`MetricasService.metricasPorZona`) — simplemente deja de
sumar pedidos nuevos porque ya no tienen zona cargada; los históricos siguen apareciendo.

## 8. Configuración nueva (panel → Configuración, junto a los campos de BICI existentes)

| Clave | Qué hace | Default |
| --- | --- | --- |
| `distancia_maxima_bici_retiro_km` | Tope de distancia cadete-BICI → origen del pedido | `0` (sin límite) |
| `distancia_maxima_lote_km` | Tope de distancia entre orígenes para agrupar pedidos en un lote | `3` |

## 9. Front-end

**`admin-front`**:
- `nuevo-pedido.component.ts` (alta directa) y `solicitudes-pedido.component.ts` (revisión de
  solicitud, `RevisarSolicitudRequest`): se saca el combo de Zona; el combo MOTO/BICI se
  reemplaza por un checkbox "Requiere moto".
- `dashboard.component.ts`: las 4 apariciones de `{{ p.zona.nombre }}` /
  `{{ p.tipoVehiculoRequerido.nombre }}` (tarjeta de sugerencia de candidato, selección de
  lote, ficha del pedido) pasan a mostrar "Cualquier vehículo" cuando `!requiereMoto`, y se
  saca toda mención a zona. `loteZonaMezclada()` pasa a comparar distancias entre orígenes en
  vez de `zona.id` (sección 5).
- `configuracion.component.ts`: los dos campos nuevos de la sección 8, mismo bloque visual que
  "Distancia máxima de viaje para BICI (km)".
- Modelos (`pedido.model.ts`, `solicitud-pedido.model.ts`): sacar `zona`, reemplazar
  `tipoVehiculoRequerido` por `requiereMoto: boolean`.

**`cadete-app`**: `PedidoDtos.kt` declara `zona: LookupDto` y `tipoVehiculoRequerido: LookupDto`
como no-nulos, pero ninguno de los dos se lee en ningún lado de la app (son campos muertos del
DTO). Se actualiza el DTO para reflejar el contrato nuevo (sacar `zona`, `tipoVehiculoRequerido`
→ `requiereMoto: Boolean`) y no arrastrar un contrato desactualizado, aunque hoy no cause un
crash visible.

## 10. Testing

Tests unitarios nuevos en el estilo de `PedidoServiceReasignacionTest` (mocks, sin Spring
context):
- Un cadete BICI lejos del origen (> `distancia_maxima_bici_retiro_km`) queda afuera del
  candidato automático; una MOTO en la misma posición no.
- Un pedido con `requiereMoto=false` deja pasar tanto MOTO como BICI (bici sujeta a los topes
  de distancia).
- Un pedido con `requiereMoto=true` solo deja pasar MOTO, sin importar distancia.
- Un viaje BICI que supera `distancia_maxima_bici_km` ya no bloquea a todo el mundo — sigue
  pudiendo asignarse a una MOTO.
- `asignarLote` rechaza un lote con orígenes a más de `distancia_maxima_lote_km` entre sí, y
  acepta uno con orígenes cercanos (reemplaza el test equivalente que hoy sería por zona, que
  no existe todavía como test explícito).
