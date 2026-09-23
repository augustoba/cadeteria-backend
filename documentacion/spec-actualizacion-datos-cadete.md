# Spec — Actualización de datos del cadete desde la app, con revisión del admin

Fecha: 2026-09-23. Estado: **propuesta, aprobada en brainstorming, pendiente de plan de
implementación** — nada de esto está implementado todavía.

Alcance: 3 repos — `cadeteria` (backend), `admin-front` (pantalla nueva de revisión),
`cadete-app` (pantalla nueva "Actualizar mis datos").

---

## 1. El pedido original

Hoy, si un cadete quiere cambiar la foto de su vehículo, la tarjeta verde, o corregir marca/
modelo/color/patente/año, no tiene forma de hacerlo — solo el admin puede editarlo desde el
panel (`cadete-form.component.ts`). El dueño quiere que el cadete pueda actualizar estos datos
él mismo desde la app, pero **sin que se apliquen directo**: le preocupa que, después de darse
de alta con datos reales (verificados), un cadete actualice más adelante con fotos que no
correspondan a su vehículo real (fraude). Por eso cada cambio propuesto queda **en revisión**
hasta que el admin lo aprueba o rechaza — y debe quedar un registro permanente de qué se pidió,
cuándo, y qué se resolvió (no se pisa nada).

**CBU/alias queda fuera de este mecanismo** — el cadete ya los edita hoy sin pasar por nadie
(`PATCH /api/cadetes/me/cuenta`, `PerfilScreen.kt`) y eso no cambia. Nombre, apellido, DNI y
teléfono tampoco entran acá — siguen siendo edición exclusiva del admin (el teléfono además ya
tiene su propio endpoint instantáneo de auto-servicio, `PATCH /api/cadetes/me/telefono`, que
tampoco se toca).

## 2. Qué existe ya (importante: no reconstruir)

| Pieza | Dónde | Relevancia |
| --- | --- | --- |
| Flujo de revisión con estados PENDIENTE/EN_REVISION/APROBADA/RECHAZADA, para altas nuevas | `SolicitudCadete`, `SolicitudCadeteService`, `SolicitudCadeteAdminController` (`/api/admin/solicitudes-cadete`), `solicitudes-cadete.component.ts` | Mismo espíritu (revisión admin antes de tocar el `Cadete` real), pero es **todo o nada** por solicitud — no sirve tal cual porque acá se pidió aprobar/rechazar **campo por campo**. Se usa como referencia de estilo (back y front), no se reusa la tabla. |
| Edición de CBU/alias por el propio cadete, instantánea | `CadeteController.actualizarCuenta` (`PATCH /api/cadetes/me/cuenta`), `PerfilScreen.kt` | No se toca — queda igual, fuera de este mecanismo. |
| Edición de teléfono por el propio cadete, instantánea | `CadeteController.actualizarTelefonoPropio` (`PATCH /api/cadetes/me/telefono`) | No se toca — el teléfono no entra en el flujo de revisión (el cadete no lo puede cambiar por ESTE mecanismo; el endpoint instantáneo existente es aparte y sigue como está). |
| Edición directa de fotos/vehículo por el admin | `CadeteService.apply`, `cadete-form.component.ts` | Sigue existiendo sin cambios — el admin siempre puede editar directo si hace falta (ej. corregir un error de carga), la revisión es solo para cambios que **inicia el cadete**. |
| Prefijo de endpoints de auto-servicio del cadete | `CadeteController`, todos bajo `/api/cadetes/me/**` | Los endpoints nuevos de este feature siguen ese mismo prefijo. |
| Prefijo y patrón de endpoints admin de revisión | `SolicitudCadeteAdminController`, `/api/admin/solicitudes-cadete` con `/aprobar` y `/rechazar` | Los endpoints admin nuevos siguen ese mismo patrón, bajo `/api/admin/cadetes/actualizaciones`. |
| Badge/alerta en vivo de solicitudes pendientes | No existe para `solicitudes-cadete` (el admin entra a la pantalla y ve la lista, sin notificación push ni contador en el nav) | Este feature sigue el mismo nivel de simplicidad: sin WebSocket ni contador en el nav — es una pantalla más que el admin revisa. Si más adelante se quiere un aviso en vivo, se agrega igual que para cualquier otra alerta (`WebSocketPublisher`), pero no es parte de este alcance. |

## 3. Alcance de campos

| Campo | Editable por el cadete | Pasa por revisión |
| --- | --- | --- |
| `fotoUrl` (foto de perfil) | Sí | Sí |
| `fotoVehiculoUrl` | Sí | Sí |
| `fotoTarjetaVerdeUrl` / `fotoTarjetaVerdeDorsoUrl` | Sí | Sí |
| `vehiculoMarca` / `vehiculoModelo` / `vehiculoColor` / `vehiculoPatente` / `vehiculoAnio` | Sí | Sí |
| `fotoCarnetUrl` / `fotoCarnetDorsoUrl` (DNI) | **No** | — |
| `cbu` / `aliasCbu` | Sí | **No** (instantáneo, ya existe) |
| `nombre` / `apellido` / `dni` | **No** | — |
| `telefono` | No por este flujo (usa su propio endpoint instantáneo ya existente) | — |
| `tipoVehiculo` (MOTO/BICI) | **No** — fuera de alcance, solo admin | — |

## 4. Modelo de datos

Dos entidades nuevas — ninguna reusa `SolicitudCadete`:

```java
@Entity
@Table(name = "cadete_actualizacion")
public class CadeteActualizacion {
    @Id private String id;
    @ManyToOne(optional = false) @JoinColumn(name = "cadete_id") private Cadete cadete;
    @Column(nullable = false) private Instant creadoEn;
    // Sin estado propio: el estado del lote es derivado de sus items (ver 5.2).
}
```

```java
@Entity
@Table(name = "cadete_actualizacion_campo")
public class CadeteActualizacionCampo {
    @Id private String id;
    @ManyToOne(optional = false) @JoinColumn(name = "actualizacion_id") private CadeteActualizacion actualizacion;
    /** Uno de: FOTO_PERFIL, FOTO_VEHICULO, FOTO_TARJETA_VERDE, FOTO_TARJETA_VERDE_DORSO,
     *  VEHICULO_MARCA, VEHICULO_MODELO, VEHICULO_COLOR, VEHICULO_PATENTE, VEHICULO_ANIO */
    @Column(nullable = false) private String campo;
    @Column(length = 1000) private String valorAnterior; // snapshot al momento de crear el item
    @Column(length = 1000, nullable = false) private String valorPropuesto;
    /** PENDIENTE / APROBADO / RECHAZADO */
    @Column(nullable = false) private String estado = "PENDIENTE";
    private String motivoRechazo;
    private Instant resueltoEn;
    private String resueltoPorUsername;
}
```

`campo` es un `String` con valores fijos validados en el service (no una tabla de parametría
tipo `Lookup` — son 9 valores fijos del código, no algo que el admin necesite editar desde
Configuración, así que no amerita el patrón `Lookup`/seeder que sí tiene sentido para
`EstadoPedido`/`TipoVehiculo`).

`valorAnterior`/`valorPropuesto` son `String` para los 9 campos por igual (fotos son URLs,
`vehiculoAnio` se guarda como texto — se parsea a `Integer` recién al aplicar, con el mismo
`blankToNull`/parseo que ya usa `CadeteService.apply`).

**Esta tabla ES el historial pedido** — nunca se borra (no entra en `RetencionDatosService`,
es auditoría de identidad, no una foto de un pedido puntual), queda como registro permanente de
qué se pidió, cuándo, y cómo se resolvió.

## 5. Reglas de negocio (`CadeteActualizacionService`, nuevo)

### 5.1. Crear un lote (`POST /api/cadetes/me/actualizaciones`, auth cadete)

```java
public record ActualizacionCadeteRequest(
        String fotoUrl, String fotoVehiculoUrl,
        String fotoTarjetaVerdeUrl, String fotoTarjetaVerdeDorsoUrl,
        String vehiculoMarca, String vehiculoModelo, String vehiculoColor,
        String vehiculoPatente, Integer vehiculoAnio
) {}
```

Todos los campos son opcionales — el cadete manda solo los que quiere cambiar (mínimo 1, si
vienen todos `null` se rechaza con `BadRequestException`). Antes de crear:

- Si el cadete ya tiene algún `CadeteActualizacionCampo` en `PENDIENTE` (de cualquier lote
  anterior), se rechaza: *"Ya tenés una actualización esperando revisión — esperá a que se
  resuelva antes de mandar otra."*
- Para cada campo no-nulo del request, se compara contra el valor actual del `Cadete`: si es
  igual al valor actual, se ignora (no tiene sentido "proponer" lo mismo que ya tiene).
- Se crea un `CadeteActualizacion` (el lote) + un `CadeteActualizacionCampo` por cada campo que
  efectivamente cambia, con `valorAnterior` = valor actual del `Cadete`, `estado = PENDIENTE`.

### 5.2. Ver mi historial (`GET /api/cadetes/me/actualizaciones`, auth cadete)

Devuelve los lotes del cadete (más reciente primero) con sus items — la app usa esto para
mostrar "pendiente" o "rechazado: `<motivo>`" debajo de cada campo en la pantalla de perfil. El
"estado" de un lote, para mostrarlo agrupado, se deriva en el DTO (no se persiste): si tiene
algún item `PENDIENTE` → lote pendiente; si no, y tiene algún `RECHAZADO` → lote con
rechazos; si todos `APROBADO` → lote aprobado.

### 5.3. Listar pendientes (`GET /api/admin/cadetes/actualizaciones?estado=PENDIENTE`, auth admin)

Devuelve los `CadeteActualizacionCampo` en ese estado, con datos del cadete (nombre, foto
actual) y del lote, agrupados por cadete+lote para la pantalla de revisión.

### 5.4. Aprobar un campo (`POST /api/admin/cadetes/actualizaciones/campos/{id}/aprobar`, auth admin)

- Exige que el item esté en `PENDIENTE` (si no, `BadRequestException`: "ya fue resuelto").
- Aplica `valorPropuesto` al `Cadete` real (mismo `blankToNull`/parseo de `vehiculoAnio` que usa
  `CadeteService.apply`).
- Marca el item `APROBADO`, `resueltoEn = now()`, `resueltoPorUsername = admin.getName()`.

### 5.5. Rechazar un campo (`POST /api/admin/cadetes/actualizaciones/campos/{id}/rechazar`, auth admin)

Body: `{ "motivo": String | null }`. Marca el item `RECHAZADO` con el motivo — **no** toca el
`Cadete` (el valor actual queda igual). El cadete puede volver a mandar un valor nuevo para ese
mismo campo en un lote futuro, en cuanto no le quede ningún item `PENDIENTE`.

## 6. admin-front

Nueva pantalla `revision-cadetes.component.ts`, ruta `/revision-cadetes`, item de nav al lado de
"Solicitudes de cadete" (mismo ícono/estilo). Reusa el patrón visual de
`solicitudes-cadete.component.ts`: una tarjeta por lote, mostrando cada campo pendiente con
valor anterior vs. propuesto lado a lado (fotos como miniaturas clickeables para ver grande,
texto para los datos del vehículo), botones "✔ Aprobar"/"✖ Rechazar" por campo — al rechazar,
un input de motivo (opcional) antes de confirmar, mismo patrón de modal que ya usa el dashboard
para otras acciones con motivo.

## 7. cadete-app

Nueva sección "Actualizar mis datos" dentro de `PerfilScreen.kt` (no una pantalla aparte —
mismo lugar donde ya está CBU/alias, para que el cadete tenga todo su perfil en un solo lugar).
Reusa los mismos helpers de captura de foto ya implementados en `ViajeScreen.kt`
(`crearArchivoFotoTemporal`, `corregirRotacionExif`, `decodificarFotoCorregida` — mover a un
archivo compartido tipo `util/FotosUtil.kt` si `PerfilScreen` los necesita, para no duplicar
código entre pantallas) para que las fotos nuevas no salgan rotadas, igual que ya se corrigió
para las fotos de viaje.

Debajo de cada campo editable, si tiene un item no resuelto de su último lote: chip "⏳
Pendiente de revisión" o "❌ Rechazado: `<motivo>`" (si lo rechazaron). Mientras haya algo
`PENDIENTE`, el botón "Guardar cambios" para ESTE flujo queda deshabilitado con el mensaje ya
mencionado en 5.1 (evita mandar un request que el backend va a rechazar de entrada).

## 8. Testing

- Backend: tests de `CadeteActualizacionService` (mock de repos, mismo estilo que
  `PedidoServiceExclusionQuitarTest`) cubriendo: crear lote ignora campos iguales al actual;
  crear lote falla si hay un pendiente; aprobar aplica el valor y no permite doble-resolución;
  rechazar no toca el `Cadete`; listar admin filtra por estado.
- Sin tests de integración HTTP nuevos más allá de los que ya cubre el patrón existente del
  proyecto (no hay tests de controller en el repo hoy, ver `PedidoServiceMatchingPorDistanciaTest`
  y similares — todos son de service).

## 9. Migración

Tablas nuevas (`cadete_actualizacion`, `cadete_actualizacion_campo`), Hibernate
`ddl-auto: update` las crea solas — no hace falta ningún script de migración manual (no se toca
ninguna columna existente).

## 10. Fuera de alcance (YAGNI, confirmado con el usuario)

- Cancelar un lote ya mandado (el cadete espera la resolución).
- Cambiar `tipoVehiculo` (MOTO↔BICI) por este flujo.
- Aviso en vivo (WebSocket/badge) al admin — sigue el mismo nivel que `solicitudes-cadete` hoy.
- Editar la foto del DNI por este flujo (documento de identidad, no del vehículo).
