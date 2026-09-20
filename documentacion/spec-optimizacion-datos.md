# Spec — Optimización de datos e imágenes (costo de servidor)

Fecha: 2026-09-20. Estado: **propuesta, pendiente de revisión**.

**Motivo:** el backend va a pasar a un servidor alojado y se va a pagar por el tráfico.
Todo byte que se mande de más se paga. Las fotos son, por lejos, el ítem más caro.

Specs hermanos: [`spec-antiabuso-pedidos-publicos.md`](./spec-antiabuso-pedidos-publicos.md),
[`spec-app-mejoras-visuales.md`](./spec-app-mejoras-visuales.md).

---

## 1. Qué ya está hecho (no rehacer)

Se hizo el 2026-09-20 (ver `MEJORAS-PROPUESTAS.md`):

- **N+1 de "paradas"** — `PedidoResponse.fromResumen()` saca la colección lazy del listado;
  el detalle y el WebSocket siguen mandándola completa. `PedidoAdminController.list()` la usa.
- **Refetch por WebSocket en el dashboard** — ahora parchea el ítem en memoria en vez de
  volver a pedir la lista completa.
- **Gzip** — `server.compression` en `application.yml` (json/text/css/js, >1024 bytes).
- **`CadeteResumen`** (`PedidoDtos.java:46`) — el cadete dentro de un pedido ya va liviano:
  `id, nombre, apellido, fotoUrl, tipoVehiculo`. El pedido **no** arrastra el DNI ni las 4
  fotos del vehículo.
- **Las fotos de retiro/entrega en el detalle del pedido ya están detrás de un botón "ver
  foto"** — no se cargan al abrir el listado. Fue una decisión explícita para no gastar
  transferencia de Cloudinary.

## 2. Qué falta — imágenes (el más grande)

### El problema

**No hay una sola transformación de Cloudinary en todo el proyecto.** Ni `q_auto`, ni
`f_auto`, ni `w_`. Lo que devuelve el upload se guarda crudo y se sirve crudo: la foto
original de la cámara del celular, típicamente **2-5 MB**.

Y se usa en miniaturas. Los casos concretos:

| Archivo | Tamaño mostrado | Qué descarga |
| --- | --- | --- |
| `solicitudes-cadete.component.ts:78-87` | **56×56 px**, ×4 fotos por postulante | 4 originales |
| `seguimiento.component.ts:126` | 64×64 px (avatar del cadete) | 1 original |
| `seguimiento.component.ts:166` | max 256 px de alto | 1 original |
| `image-upload.component.ts:11` | 64×64 px | 1 original |

Cuatro fotos de 3 MB para pintar cuatro cuadraditos de 56 px son ~12 MB **por postulante**.
Con 30 cadetes dando de alta, es el tráfico más caro de todo el sistema, y es puro
desperdicio: el navegador tira el 99,9% de esos bytes al renderizar.

### La solución: transformar en la entrega

Cloudinary transforma **por URL** — no hace falta volver a subir nada. Se insertan los
parámetros después de `/upload/`:

```
antes:  https://res.cloudinary.com/{cloud}/image/upload/v123/foto.jpg
después: https://res.cloudinary.com/{cloud}/image/upload/f_auto,q_auto,w_120/v123/foto.jpg
```

- `f_auto` — formato automático (WebP/AVIF según el navegador).
- `q_auto` — calidad automática (Cloudinary decide el mejor balance peso/calidad).
- `w_{n}` — ancho. Con `c_limit` para que **nunca agrande** una imagen más chica.

**Lo importante: arregla las fotos que ya están subidas.** Es un cambio de construcción de
URL, no una migración. No hay que re-subir ni tocar la base.

### Cómo se implementa

Un helper en cada lado, con los anchos por caso de uso:

**Panel (`admin-front`)** — `core/utils/imagen.util.ts`:

| Uso | Transformación |
| --- | --- |
| Miniatura en listado (56-64 px) | `f_auto,q_auto,c_limit,w_120` |
| Avatar mediano | `f_auto,q_auto,c_limit,w_240` |
| Lightbox / "ver foto" | `f_auto,q_auto,c_limit,w_1600` |
| Firma (trazo, no necesita resolución) | `f_auto,q_auto,c_limit,w_800` |

**App (`cadete-app`)** — mismo helper en Kotlin, aplicado antes de pasarle la URL a Coil
(perfil y vehículo). Coil además ya cachea en disco, así que el ahorro se multiplica.

**Reglas que tiene que cumplir el helper:**

1. **Si no es de Cloudinary, devolver la URL tal cual.** Hay fotos que pueden venir de otro
   lado; romper eso sería peor que el problema.
2. **Si ya tiene una transformación, no insertar otra** — no duplicar parámetros.
3. **Acepta `null`/vacío** y devuelve lo mismo, para no romper los `@if` que ya existen.

### El costo de subida (segundo orden)

`CloudinaryUploader` (app) y `cloudinary-upload.service.ts` (panel) suben el archivo
original. Se podría comprimir **antes** de subir (menos tiempo de subida para el cadete,
que muchas veces está con datos móviles malos). Pero eso es un problema distinto al del
servidor: el tráfico de subida va a Cloudinary, no al backend. **Va después**, y solo si
molesta el tiempo de subida.

## 3. Qué falta — DTOs

### Corrección: el primer instinto estaba mal

El planteo original era *"el listado manda 30 campos para pintar tres cosas, hagamos un
`CadeteResumenResponse`"*. **Verificado contra el front, es falso.**

El listado de cadetes (`cadetes.component.ts`) tiene estas columnas: Nombre, **DNI**,
**Teléfono**, **Usuario**, App (versión), Vehículo, **Calificación**, Estado, **Pago**. Más
`lat`/`lng` para el botón "Encontrar" del mapa. O sea: la mayoría de los campos **se usan de
verdad**, y un "resumen agresivo" habría roto la pantalla en silencio — Angular no valida
contra el DTO en tiempo de compilación, así que un campo que falta se ve como `undefined` en
la celda, no como un error.

### El caso real (mucho más chico)

Lo que el listado **no usa** y sí viaja en cada carga:

| Campo | Peso |
| --- | --- |
| `fotoUrl`, `fotoVehiculoUrl`, `fotoCarnetUrl`, `fotoTarjetaVerdeUrl` | 4 URLs por cadete |
| `cbu`, `aliasCbu` | datos de cobro del cadete |

Seis campos, no veinte. Y de esos, los que importan son las **4 URLs de fotos** — son las
que el panel no muestra en el listado, y las que alguien podría llegar a renderizar por
accidente bajando megabytes.

### Y un N+1 que apareció al escribir el plan (más caro que los 6 campos)

`CadeteService.toResponse()` (`CadeteService.java:115-120`) hace **una query por cadete** para
calcular la calificación:

```java
public CadeteResponse toResponse(Cadete c) {
    List<Pedido> calificados = pedidoRepo.findByCadeteAsignadoIdAndCalificacionEstrellasIsNotNull(c.getId());
    ...
}
```

Y `CadeteController.list()` es `service.findAll().stream().map(service::toResponse)`. O sea:
**30 cadetes = 30 queries**, en cada carga del listado. Es el mismo patrón que se sacó el
2026-09-20 con las paradas del listado de pedidos.

**Este ahorro es en la base, no en el tráfico** — pero es trabajo de servidor igual, y es más
grande que el de los 6 campos. Va junto con `fromListado`, en la misma tarea, porque los dos
tocan la misma llamada y ninguno se entiende solo.

Arreglo: **una sola query** que agrupe por cadete (promedio y cantidad), un `Map<cadeteId, ...>`
y `CadeteResponse.from(c, promedio, cantidad)` por cadete. De N queries a 1.

### La solución (proporcional al problema)

**No crear un DTO nuevo.** Sacar esos 6 campos del listado, con un
`CadeteResponse.fromListado()` que los deja en `null` — el mismo patrón exacto que
`PedidoResponse.fromResumen()` ya usa con `paradas` (`PedidoDtos.java:93`).

- `CadeteController.list()` (`CadeteController.java:57`) usa `fromListado()`.
- `GET /api/cadetes/{id}` (ficha) y `GET /api/cadetes/me` (perfil propio) **siguen completos**
  — ahí sí hacen falta (la ficha muestra las fotos y el CBU).
- **Antes de tocarlo**: grep del front por esos 6 nombres en `features/cadetes/` y
  `features/dashboard/`. Ya se verificó que no están en `cadetes.component.ts`, pero hay que
  confirmarlo sobre el árbol entero antes de mergear.

**El ahorro en bytes es modesto** (unas URLs contra imágenes de megabytes). El motivo real
para hacerlo es otro: que un dato de cobro y cuatro fotos no viajen donde no se usan, y que
nadie las renderice por accidente. Es higiene, no performance.

### El resto: auditar, no adivinar

No conviene salir a recortar DTOs a ciegas. El método que ya funcionó el 2026-09-20:

1. Prender `logging.level.org.hibernate.SQL=debug` momentáneamente.
2. Pegarle a cada endpoint pesado y mirar el JSON que sale.
3. Comparar contra lo que el consumidor realmente usa.

Endpoints a revisar, en orden de tráfico esperado:

| Endpoint | Sospecha |
| --- | --- |
| `GET /api/admin/pedidos?tipo=finalizados` | paginado — verificar que no traiga paradas |
| `GET /api/admin/cadetes` | el de arriba |
| `GET /api/pedidos/me/historial` | devuelve la lista completa además del resumen (ver nota abajo) |
| `GET /api/admin/clientes` | verificar el paginado |

> Nota sobre `/api/pedidos/me/historial`: la app lo va a llamar con `desde=hoy` para las
> estadísticas de Inicio (`spec-app-mejoras-visuales.md`), y devuelve toda la lista de
> pedidos del día cuando solo hacen falta dos números. Hoy es irrelevante (~8-30 viajes),
> pero si se agrega el filtro de "solo resumen" sale gratis.

## 4. Cómo se mide

Sin números, esto es opinión. Antes y después, mismo endpoint, misma pantalla:

- **Panel:** pestaña Network del navegador — peso transferido total de la pantalla de
  solicitudes de cadete (la peor).
- **Backend:** el tamaño de la respuesta de `GET /api/admin/cadetes` con 30 cadetes.
- **Ya hay herramienta instalada**: Actuator (agregado el 2026-09-20) y se usó autocannon
  para la prueba de carga. Sirve el mismo andamiaje.

Números esperados, para saber si funcionó:

- Miniatura de 56 px: de ~3 MB a **~5-15 KB** (~99% menos).
- `GET /api/admin/cadetes` con 30 cadetes: de ~30 campos × 30 a ~10 campos × 30.

## 5. Prioridad

| Fase | Contenido | Por qué en este orden |
| --- | --- | --- |
| 1 | **Helper de imágenes + miniaturas del panel** | Es el 90% del ahorro, es de bajo riesgo (si el helper falla, devuelve la URL original), y arregla las fotos ya subidas sin migrar nada |
| 2 | Helper en la app (Coil) | Mismo patrón, otro repo |
| 3 | Sacar 6 campos del listado de cadetes (`fromListado()`) | Ahorro en bytes modesto — es higiene de datos, no performance. Va después de las fotos, que son el 90% del ahorro real |
| 4 | Auditoría del resto de los endpoints | Requiere medir primero; sin medición es adivinar |

## 6. Decisiones abiertas

1. **¿Los anchos propuestos sirven?** (120 / 240 / 1600 / 800). El de la firma es el más
   discutible: un trazo se puede guardar mucho más chico, pero hay que ver cómo se ve.
2. **¿Se comprime también en la subida?** (sección 2, segundo orden) — va después.
3. **¿Cloudinary o mover las fotos a otro lado?** Fuera de alcance: hoy el diseño es
   "Cloudinary directo desde el cliente, el backend nunca ve el archivo" y eso está bien.
   Este spec no lo cambia.
