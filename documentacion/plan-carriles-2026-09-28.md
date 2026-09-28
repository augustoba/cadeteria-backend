# Plan en dos carriles (2026-09-28)

Trabajan **dos IAs a la vez** sobre los mismos repos. Para no pisarse, el trabajo está partido en
dos **carriles** con **archivos propios**. Cada IA hace SOLO su carril y SOLO toca los archivos de
su carril. Si para terminar necesita tocar un archivo del otro carril, **no lo toca**: lo anota en
"Pedidos al otro carril" (al final de su sección) y se lo avisa al usuario.

| Carril | Quién | Tema | Repos |
| --- | --- | --- | --- |
| **A — Direcciones** | Claude (PC de Windows del usuario) | 3i(4), 3j, 3h (buscador, pin del mapa, cache de direcciones) | backend + panel |
| **B — Pedidos en el lugar** | la otra IA | 3n (aviso "en camino" editable) + Retirado/Entregado solo en el lugar + finalizado por el admin con motivo | backend + panel + APK |

El contexto de cada punto (por qué, qué se vio en la calle) está en `pendientes.md`: secciones
"Plan acordado el 2026-09-28", 3h, 3i, 3j, 3k y 3n. Leerlas antes de empezar.

---

## Reglas para las dos

1. **Punto de partida**: `develop` de cada repo (backend `3a7bd6a` o posterior, panel `595d18f`,
   APK `97b64e6`). Antes de empezar: `git fetch` y `git switch develop && git pull`.
2. **Ramas**:
   - Carril A: `carril-a-direcciones` (backend y panel).
   - Carril B: `carril-b-en-el-lugar` (backend, panel y APK).
3. **Commits** chicos, en español y sin tildes en el mensaje (como el historial). Pushear la rama
   propia seguido. **Nunca** `push --force`, **nunca** commitear directo en `develop` ni en `main`.
4. **Pasar a `develop` solo cuando el usuario lo pida.** En ese momento: `git fetch`, mergear
   `origin/develop` en la rama propia, correr los tests (`./mvnw test` en el backend,
   `npx ng build` en el panel, `./gradlew testDebugUnitTest assembleDebug` con JDK 17 en la APK),
   y recién ahí llevarlo a `develop` y pushear.
5. **Base de datos**: el esquema lo arma Hibernate (`ddl-auto: update`), no hay migraciones
   numeradas. Solo **agregar** columnas nuevas y que acepten null (o con default), en las
   entidades del propio carril. No renombrar ni borrar columnas. Si hace falta un script SQL,
   va en `documentacion/sql/` con un nombre que empiece con la fecha y el carril
   (`2026-09-28-carril-a-...sql`).
6. **Configuración nueva**: claves nuevas con su default en el código (`configuracionService.getX(clave, default)`),
   con prefijo propio para no chocar. No tocar `application.yml`.
7. **Documentación**: cada IA escribe **solo en su sección de este archivo** ("Avance" y "Para
   despliegue"). **No editar** `pendientes.md` ni `despliegue.md`: al juntar los dos carriles se
   pasan ahí (si algo es para producción, anotarlo en "Para despliegue" de su sección).
8. **Tests**: los nuevos van en archivos de test nuevos o en los del propio carril. Los 153+ tests
   que ya hay tienen que seguir pasando.
9. Idioma: todo (textos de pantalla, comentarios, mensajes de error) en español rioplatense como
   el resto del proyecto ("tocá", "podés").

### Archivos de cada carril

| Archivo | Dueño |
| --- | --- |
| backend `service/DireccionCacheService.java` | **A** |
| backend `service/GeocodingProxyService.java` | **A** |
| backend `controller/GeocodingProxyController.java` | **A** |
| backend `model/CuadraCoords.java`, `model/DireccionAlias.java` y sus repositories | **A** |
| backend `service/MapeoCallesCadetesService.java` | **A** |
| panel `shared/address-picker.component.ts`, `shared/mapa-picker.component.ts` | **A** |
| panel `features/dashboard/cadetes-libres.component.ts` (solo si hace falta para 3i) | **A** |
| backend `service/PedidoService.java` | **B** |
| backend `controller/PedidoCadeteController.java`, `controller/PedidoAdminController.java` | **B** |
| backend `model/Pedido.java`, `model/Cadete.java`, `dto/PedidoDtos.java` | **B** |
| backend `service/ConfiguracionService.java`, `model/Configuracion.java` | **B** |
| panel `features/configuracion/*`, `features/dashboard/*` (menos `cadetes-libres`), `features/pedidos/*`, `features/cadetes/*`, `features/metricas/*`, `features/seguimiento/*`, `shared/aviso-cliente.component.ts` | **B** |
| **toda la APK** (`cadeteria-apk`) | **B** |
| `documentacion/pendientes.md`, `documentacion/despliegue.md` | nadie (se juntan al final) |
| cualquier archivo nuevo | el carril que lo crea |

### El único punto de contacto entre los dos carriles

Cuando el cadete marca **Retirado** o **Entregado**, `PedidoCadeteController` (carril B) ya llama a
`GeocodingProxyService.aprenderDeCadete(direccion, pinLat, pinLng, lat, lng, precisionM, calleTelefono, localidadTelefono)`
(carril A). Ese es el contrato:
- **Carril B** sigue llamándolo igual (mismos parámetros, con el GPS del momento en que el cadete
  tocó el botón). No lo saca aunque el cadete haya usado "Estoy en el lugar".
- **Carril A** no cambia la firma de los métodos públicos que usa B (`aprenderDeCadete`,
  `aprenderPin`, `aprenderDelTelefono`); cambia solo lo que hacen por dentro.

---

## Carril A — Direcciones (Claude)

Objetivo: que la base propia de direcciones sea confiable y se use primero; que el pin del mapa
no se equivoque ni moleste; que el buscador entienda nombres incompletos.

### A1. 3i(4): mirar el panel no suma confirmaciones (chico, empezar por acá)
Hoy `GeocodingProxyService.reverse()` llama a `alimentarCacheSiEsPreciso(r)` y eso **suma
`confirmaciones`** en `cuadra_coords` cada vez que alguien consulta `/api/publico/direcciones/reverse`
(el panel lo llama al mirar la cola de espera y al mover un pin; `/pedir` también). Con solo tener
el panel abierto, "camino del peru 1600" llegó a 18 confirmaciones sin que nadie confirmara nada.
- Lo que viene del endpoint público `/reverse` **no suma confirmaciones**. Revisar si
  `alimentarCacheSiEsPreciso` debe seguir guardando filas nuevas desde ahí; si guarda, con la
  confianza de Nominatim y sin sumar.
- Revisar quién más llama a `reverse()` por dentro (`aprender`, mapeo de calles): ahí puede
  seguir como está.
- Test que lo demuestre (llamar `reverse` varias veces con el mismo punto → `confirmaciones` no
  cambia).

### A2. 3j: el pin del mapa
Caso real: **Colombia 4695** (barrio Tarcos). En OpenStreetMap las calles de alrededor no tienen
nombre, así que el reverse devuelve "Camino del Perú" (la avenida a 113 m). Resultado: el cartel
del panel dice "El pin está sobre Camino del Perú", cosa que es falsa, y el pin del operador
**no se aprende**, así que cada pedido a esa dirección obliga a volver a arrastrarlo.

Backend (`GeocodingProxyService.aprender` / `aprenderPin`, `DireccionCacheService`):
1. **Pin del operador** (`manual`) cuyo reverse **no confirma** la calle tipeada (trae otra calle,
   o ninguna, o una sin nombre) → guardarlo como **sin confirmar**
   (`PROVEEDOR_MANUAL_SIN_CONFIRMAR`, que ya existe para el caso "sin calle"; ahora también para
   "otra calle"). Sirve como sugerencia la próxima vez que se busque esa dirección, pero con la
   confianza más baja. Si el reverse sí confirma, queda como hoy (`manual`).
2. **Un pin nunca pisa un punto confirmado por un cadete.** OJO: hoy `DireccionCacheService.confianza()`
   le da `manual` = 4 y `cadete_gps` = 3, o sea que un pin del operador **pisa** lo que el cadete
   confirmó parado en la puerta. Cambiarlo para que `cadete_gps` le gane a `manual` y a
   `manual_sin_confirmar` (y revisar dónde queda `android_geocoder`, que hoy es 1). Test: cadete
   confirma un punto → operador arrastra un pin a 40 m → queda el del cadete.
3. **El cadete confirma el pin**: dentro de `aprenderDeCadete` (se llama al marcar Retirado y
   Entregado, ver "punto de contacto"). Si para esa dirección hay una fila `manual_sin_confirmar` o
   `manual` y el GPS del cadete es preciso (lo que ya controla `aprender_gps_precision_max_m`):
   - a menos de **50 m** del pin → queda **confirmada** (pasa a `cadete_gps`, con el punto del
     cadete o promediado con `muestras`, a criterio);
   - más lejos → **gana el punto del cadete** (el pin estaba mal). Hoy `DISTANCIA_MAX_AL_PIN_M`
     (2000 m) corta el aprendizaje si el cadete marcó muy lejos del pin: mantener ese corte como
     "marcó desde otro lado".
   - Recordar que el carril B va a dejar marcar con "Estoy en el lugar" aunque el cadete esté a más
     de 150 m del pin: justamente es el caso en que el pin estaba mal, así que que el cadete gane es
     lo correcto.
4. **Pin del cliente** (`/pedir`): no se aprende nunca solo. Hoy se aprende recién cuando el admin
   aprueba la solicitud (`SolicitudPedidoService` → `aprenderPin`): que en ese caso también entre
   como **sin confirmar**, igual que el pin del operador. (Si para eso hay que tocar
   `SolicitudPedidoService`, es de A: B no lo toca.)
5. Si el admin finaliza a mano (no hay GPS de cadete), el pin queda sin confirmar: está bien, es un
   dato débil y tiene que verse así (ver "confianza" en la cache).

Panel (`address-picker.component.ts`, el cartel `calleDistinta`):
6. El cartel "⚠️ El pin está sobre X" tiene que mirar **primero la base propia**: el punto aprendido
   más cercano (menos de ~30 m), prefiriendo `cadete_gps` / `android_geocoder`. Recién si no hay,
   Nominatim. Lo más simple: que el backend lo resuelva en `reverse` (buscar primero en
   `cuadra_coords` cerca del punto y devolver esa calle) — eso es además el **3i(2)** y mejora la
   "Ubicación aproximada" de la cola de espera sin tocar el panel.
7. Si el reverse **no trae calle** o cae en una **sin nombre**, **no mostrar el cartel**.
8. El cartel **nunca bloquea** (hoy tampoco, verificar que siga así). El texto puede quedar más
   suave: "El mapa marca otra calle (X). Si el pin está en la puerta, dejalo así."

### A3. 3h: "colom 4600" encuentra "colombia"
Hoy `DireccionCacheService.buscar` busca el alias **exacto** (`findByVarianteNorm`); con "colom 4600"
no encuentra nada y los buscadores de afuera tampoco.
1. Sin alias exacto: buscar las calles conocidas (canónicas y alias) que **empiecen** con lo
   tipeado, **mínimo 4 letras** (con "sa" saldrían todas las "San…").
2. **Una sola** coincidencia → se usa. **Varias** ("san 800" → San Juan, San Lorenzo, San Martín) →
   devolver **todas como opciones** (el buscador del panel ya muestra una lista de resultados), no
   adivinar.
3. **Clave**: aunque la cache no tenga esa cuadra, **corregir el nombre antes de preguntar afuera**:
   si "colom" → "colombia" es la única coincidencia, a Nominatim / Geoapify / Google les llega
   "colombia 4600", no "colom 4600".
4. Tolerancia a errores de tipeo ("colombai", "lamadird"): **NO por ahora**. Anotarlo como
   siguiente paso.
5. Relacionado y chico (3h de `pendientes.md`): en `CadeteController.actualizarUbicacion`, el
   registro de la calle del teléfono se hace aunque `aprenderDelTelefono` no haya guardado.
   `CadeteController` no es de ningún carril: si lo tocás, solo esa línea.
6. Tests: prefijo único, prefijo con varias, menos de 4 letras (no busca por prefijo), nombre
   corregido antes de ir afuera.

### Orden sugerido para A
A1 → A2 (backend 1-5) → A2 (panel 6-8) → A3.

### Avance (carril A)
_(anotar acá qué se hizo, commits y qué se probó)_

### Para despliegue (carril A)
_(lo que haya que hacer en producción: scripts SQL, claves de configuración, etc.)_

### Pedidos al otro carril (A → B)
_(si necesita algo de un archivo de B)_

---

## Carril B — Pedidos en el lugar (la otra IA)

Objetivo: que el cadete no pueda marcar Retirado ni Entregado si no está en el lugar (con una
salida para cuando la dirección del pedido está mal ubicada), que funcione sin señal, que el
admin tenga que explicar por qué finaliza a mano, y que el texto del aviso "en camino" se edite
desde el panel. Contexto del negocio: el dueño quiere salir de la oficina, así que **nada de esto
puede necesitar que alguien apruebe algo en el panel**: se deja registrado y listo.

### Cómo está hoy (verificado el 2026-09-28)
- **Retirado**: APK → `POST /api/pedidos/me/{id}/recepcion` (`PedidoCadeteController.recepcion` →
  `PedidoService.registrarRecepcion`), body `RecepcionRequest` (`fotoUrl`, `lat`, `lng`,
  `archivoPerdido`, `precision`, `calleDetectada`, `localidadDetectada`). Guarda `retiroLat/Lng`
  en `Pedido`. **No controla la distancia.**
- **Entregado**: `POST /api/pedidos/me/{id}/finalizar` (`PedidoService.finalizar`, línea ~1274),
  body `FinalizarRequest` (nombre de quien recibe, foto, firma, `lat`, `lng`, `precision`, ...).
  Exige paradas entregadas y foto/nombre/firma si la configuración los pide. **No exige Retirado
  antes y no controla la distancia.**
- **Parada intermedia**: `POST /api/pedidos/me/{id}/paradas/{paradaId}/entregada` — **no manda
  posición**. Hay que agregarle `lat`/`lng`/`precision`/hora (body opcional, para no romper APKs
  viejas).
- **Finalizar el admin**: `POST /api/admin/pedidos/{id}/finalizar` (`PedidoAdminController` →
  `PedidoService.finalizarComoAdmin`), usa el mismo `FinalizarRequest`. En el panel:
  `core/services/pedido.service.ts` → `finalizar()` (manda `receptorNombre` y `fotoUrl`); el botón
  está en `features/dashboard/dashboard.component.ts`. **No pide motivo.**
- **Sin señal**: la APK ya tiene una cola (`data/local/PendingActionsStore.kt`,
  `data/repository/PendingActionsRepository.kt`): guarda `RetiradoPendiente` / `FinalizarPendiente`
  con foto (URL o archivo local) y `lat`/`lng`, y los reintenta al volver la conexión. **No guarda
  la hora del toque ni la precisión**, así que el backend registra la hora de llegada.
- **Pantallas de la APK**: `ui/viaje/ViajeScreen.kt` y `ViajeViewModel.kt` (botones Retirado /
  Entregado / parada). Ubicación: `location/LocationTrackingService.kt`; ya existe
  `location/AvisoLlegada.kt` (avisa "llegaste al retiro" a menos de 150 m; sirve de referencia).
- **GPS falso**: no se detecta en ningún lado.
- **Aviso "en camino"**: `PedidoService` (~línea 1527) lee `whatsapp_template_en_camino` con
  default `WHATSAPP_EN_CAMINO_DEFAULT`; variables `{marca}`, `{cadete}`, `{numero}`, `{link}`.
  El botón del panel es `shared/aviso-cliente.component.ts`. Hoy el texto solo se cambia en la base.

### B1. 3n: editar el aviso "en camino" desde el panel (empezar por acá, es corto)
- Configuración → sección WhatsApp: cuadro de texto de `whatsapp_template_en_camino` con botones
  para insertar `{cadete}`, `{numero}`, `{link}`, `{marca}`.
- **Vista previa con un pedido real reciente** (el último en curso o finalizado), no uno inventado.
- Aviso si falta `{link}` (sin el link el aviso no sirve). Botón "Volver al texto original"
  (el default del código).
- Lo edita el **admin** (no hace falta superadmin: es texto de la cadetería).
- Guardar **quién lo editó y cuándo** (una línea: "Editado por X el 28/09 14:32"; sin historial).
- Los SMS salen sin tildes, pero este aviso va por la app de WhatsApp: acá las tildes están bien.

### B2. Retirado / Entregado solo en el lugar
Decidido con el usuario el 2026-09-28:

1. **Orden obligatorio**: no se puede marcar Entregado (ni una parada) sin Retirado. Backend
   (`finalizar` → error claro "Primero marcá Retirado") y APK (el botón Entregado deshabilitado
   hasta marcar Retirado).
2. **Radio de 150 m**: al tocar Retirado la APK compara su posición con el origen del pedido; en
   cada parada, con la parada; en Entregado, con el destino. Más lejos → no deja y dice "Estás a
   800 m del retiro". El **backend vuelve a controlar** con la posición recibida (no confiar solo en
   el teléfono). Radio en una clave de configuración con default 150 (`en_lugar_radio_m`), sin
   pantalla para editarla.
3. **GPS falso**: si la ubicación viene de una app de ubicación simulada (`Location.isMock` en
   Android 12+, `isFromMockProvider` antes), no deja marcar y queda registrado (mandar el dato al
   backend, que lo guarda en el pedido y lo cuenta en el cadete).
4. **Sin señal**: el GPS anda sin datos (tarda más en ubicarse: mostrar "Buscando tu ubicación…" y
   esperar un fix, con un tope razonable). El control de distancia se hace en el teléfono; a la cola
   (`RetiradoPendiente` / `FinalizarPendiente`, y lo nuevo de paradas) se le agregan **la hora del
   toque** y la **precisión**. El backend usa esa hora para `retiradoEn` / la hora de entrega y
   para sus controles, con un límite de sensatez (no aceptar horas futuras ni anteriores a la
   aceptación del viaje).
5. **"Estoy en el lugar"**: cuando el control no deja marcar (típico: la dirección del pedido está
   mal ubicada en el mapa, como Colombia 4695), aparece este botón. Pide **foto obligatoria** (aunque
   la configuración no exija foto) y marca igual. **Nadie lo aprueba.** Queda:
   - en el pedido, marcado **"fuera de zona"** (en rojo en el detalle del panel), con la distancia;
   - en los **registros del cadete**: cuántas veces lo usó, visible en su ficha
     (`features/cadetes/cadete-ficha.component.ts`) y en Métricas (`features/metricas/`).
6. **GPS impreciso** (error > ~100 m, típico adentro de un local): no bloquea, marca con la
   advertencia "ubicación imprecisa" guardada en el pedido.
7. **Finalizado por el admin**: se saltea el control de distancia pero el **motivo es obligatorio**
   (campo de texto en el panel, validado también en el backend). Se guarda quién, cuándo y por qué,
   y el pedido figura **"Finalizado por el admin"** en el detalle, en el seguimiento del panel y en
   el historial del cadete.
8. **Compatibilidad**: las APK viejas mandan los requests sin los campos nuevos; el backend no se
   puede romper con eso (campos opcionales). Decidir si en ese caso controla con lo que llegue o
   solo registra. Existe `version_minima_app` en Configuración para forzar la actualización al
   desplegar.
9. **No tocar** las llamadas a `geocodingProxyService.aprenderDeCadete(...)` de
   `PedidoCadeteController` (ver "punto de contacto"). Si se agrega GPS a las paradas, **no**
   agregar una llamada nueva a `aprenderDeCadete`: anotarlo en "Pedidos al otro carril".

Datos nuevos sugeridos en `Pedido` (nullable): `retiro_fuera_zona`, `retiro_distancia_m`,
`entrega_fuera_zona`, `entrega_distancia_m`, `ubicacion_simulada`, `ubicacion_imprecisa`,
`finalizado_por_admin` (usuario), `finalizado_admin_motivo`. Los contadores del cadete pueden salir
de una consulta sobre los pedidos (no hace falta columna en `Cadete`).

Tests del backend: finalizar sin Retirado → error; lejos sin "Estoy en el lugar" → error; lejos con
"Estoy en el lugar" y foto → ok y marcado; sin foto → error; GPS simulado → error; admin sin motivo
→ error; hora del toque respetada; request viejo sin campos nuevos → no rompe.

### Cómo compilar y probar la APK
- **JDK 17** (con JDK 21 `assembleDebug` falla en `jlink`):
  `./gradlew testDebugUnitTest assembleDebug -Dorg.gradle.java.home=<ruta a un JDK 17>`.
- `app/build/outputs/apk/debug/app-debug.apk` está versionado: no commitear el que cambia en cada
  build salvo que el usuario lo pida.
- El emulador no tiene servicios de ubicación de Google: la posición se fija a mano desde sus
  controles. El GPS real, la precisión y la ubicación simulada conviene confirmarlos en un celular.
  Usuarios demo y cómo levantar el backend: `documentacion/apk.md` y `backend.md`.

### Orden sugerido para B
B1 (3n) → B2 backend (1, 2, 4-8, con tests) → B2 panel (motivo del admin, "fuera de zona",
registros en ficha y Métricas) → B2 APK (1-6).

### Avance (carril B)
Rama `carril-b-en-el-lugar` en los 3 repos, pusheada (2026-09-28). Trabajado en worktrees aparte
(`C:\proyectos\cadeteria\carril-b\...`) para no cambiarle la rama a las carpetas de siempre.

**B1 (3n) — hecho.** Backend `3a66b81`, panel `1384f66`.
- Endpoint nuevo `AvisoEnCaminoController` en `/api/admin/configuracion/aviso-en-camino` (queda bajo
  el permiso `configuracion`): GET (texto actual, original, quién/cuándo), PUT (valida `{link}`;
  vacío o igual al original = vuelve al del código), POST `/vista-previa` (arma el aviso con el último
  pedido en curso o finalizado que tenga cadete; 204 si no hay).
- Quién/cuándo en la clave `whatsapp_template_en_camino_editado` (`usuario|instante`, sin historial),
  vía `ConfiguracionService.setConAutor`.
- Panel: Configuración → Integraciones, debajo de las plantillas de SMS
  (`aviso-en-camino-editor.component.ts`, con su propio botón "Guardar aviso").

**B2 backend — hecho.** `f23fb09`, `311f79d`.
- Orden: parada y Entregado del cadete exigen Retirado ("Primero marcá Retirado.").
- Radio `en_lugar_radio_m` (150) contra origen / parada / destino. Lejos → 400 "Estás a 800 m del
  retiro…". Con `enElLugar=true` y foto → marca y queda `retiro_fuera_zona` / `entrega_fuera_zona` /
  `pedido_parada.fuera_zona` con la distancia. Sin foto → 400.
- GPS falso (`ubicacionSimulada=true`): 400 y queda registrado en `pedido.ubicacion_simulada` y en
  `cadete.intentos_ubicacion_simulada` (+ fecha). Se controla antes que foto/receptor/Retirado y la
  transacción no se deshace (`UbicacionSimuladaException` + `noRollbackFor`).
- GPS impreciso (`precision` > `en_lugar_precision_max_m`, 100): no bloquea, suma el error al radio y
  anota `ubicacion_imprecisa`. (Criterio mío: con 5 km de distancia y 150 m de error igual frena.)
- Hora del toque `tocadoEn`: se usa para `retiradoEn` / `finalizadoEn` / la parada, salvo que venga
  más de 2 min en el futuro o antes de la aceptación (retiro) / del retiro (parada y entrega): ahí
  se usa la hora de llegada (no se rechaza, para no trabar la cola offline).
- **APK vieja**: se reconoce porque no manda `tocadoEn`. No se la bloquea: solo se anota la distancia
  a la que marcó. Lo que sí la afecta es el orden (Entregado sin Retirado → 400).
- Admin: `POST /api/admin/pedidos/{id}/finalizar` ahora recibe `FinalizarAdminRequest`
  (`receptorNombre`, `fotoUrl`, `motivo` obligatorio). Guarda `finalizado_por_admin` y
  `finalizado_admin_motivo`; se saltea Retirado y el control del lugar.
- Parada: `POST .../paradas/{paradaId}/entregada` acepta body opcional (`lat`, `lng`, `precision`,
  `tocadoEn`, `enElLugar`, `ubicacionSimulada`, `fotoUrl`). Sin body = APK vieja, como antes.
- Registros: `GET /api/admin/cadetes/{id}/en-el-lugar` (ficha) y `GET /api/admin/metricas/en-el-lugar`
  (permiso `metricas`), `EnElLugarService`. Salen de los pedidos, salvo el GPS falso (en el cadete).
- `GET /api/cadetes/me/configuracion` suma `enLugarRadioM` y `enLugarPrecisionMaxM` (la APK controla
  con los mismos valores). Esto tocó `CadeteController.configuracion()` y `ConfiguracionDtos`
  (no son de ningún carril; A solo toca `actualizarUbicacion`, no deberían chocar).
- Tests nuevos: `AvisoEnCaminoControllerTest`, `PedidoServiceEnElLugarTest` (16 casos: los del plan
  más GPS impreciso, sin ubicación, paradas, horas insensatas), `EnElLugarServiceTest`. Ajustado
  `PedidoServiceDobleToqueTest` (ahora marca Retirado antes de finalizar). `./mvnw test`: 224 OK.

**B2 panel — hecho.** `93deaae`.
- Modal "Finalizar" del dashboard: motivo obligatorio (el botón no se habilita sin motivo).
- Detalle del pedido: "⚠️ Fuera de zona" en rojo (retiro, paradas con foto, entrega, con distancia),
  "🚫 GPS falso", "📡 ubicación imprecisa" y "🛠 Finalizado por el admin (usuario): motivo".
- Tabla del dashboard: "⚠️ fuera de zona" y "🛠 por el admin" junto a las horas.
- Ficha del cadete → Desempeño: "Marcas en el lugar" (totales + últimos 20).
- Métricas: tabla "Marcas en el lugar" por cadete para el rango aplicado.
- El seguimiento **público** (el del cliente) no muestra nada de esto a propósito.

**B2 APK — hecho.** `20968f2`.
- `location/ControlEnLugar.kt` (lógica pura, misma regla que el backend) + `ControlEnLugarTest`.
- Retirado / parada / Entregado: "Buscando tu ubicación…" (espera hasta 30 s un fix; la última
  conocida solo si es de hace menos de 2 min), control de distancia, tarjeta "📍 No estás en el lugar"
  con "Estoy en el lugar (sacar foto)" o "Volver". GPS falso (`isMock` / `isFromMockProvider`): no
  marca y avisa al backend para que quede registrado.
- "Finalizar viaje" deshabilitado hasta marcar Retirado ("Primero marcá Retirado.").
- Cola sin señal: `RetiradoPendiente` / `FinalizarPendiente` guardan `tocadoEn`, `precision`,
  `enElLugar`; nueva `ParadaPendiente`. Se reintenta en orden retiros → paradas → entregas.
- Historial: "🛠 Finalizado por el admin: motivo" y "📍 Marcado con Estoy en el lugar".
- `versionCode` 2 / `versionName` 1.1.0. `./gradlew testDebugUnitTest assembleDebug` (JDK 17): OK,
  29 tests. El `app-debug.apk` versionado **no** se commiteó.

**Probado contra la base de desarrollo** (backend de la rama en el 8081): GET/PUT/vista previa del
aviso, PUT sin `{link}` → 400, finalizar como admin sin motivo → 400 y con motivo → FINALIZADO con
quién y por qué, métricas "en el lugar" con ese registro. Hibernate agregó las columnas nuevas sin
problemas. **No probado**: el flujo del cadete en un teléfono (GPS real, precisión, app de ubicación
simulada, sin señal) y las pantallas del panel en el navegador (compilan, no las recorrí).

### Para despliegue (carril B)
- Columnas nuevas (Hibernate las crea, todas nullable): en `pedido` `retiro_fuera_zona`,
  `retiro_distanciam`, `entrega_fuera_zona`, `entrega_distanciam`, `ubicacion_simulada`,
  `ubicacion_imprecisa`, `finalizado_por_admin`, `finalizado_admin_motivo`; en `pedido_parada`
  `entrega_lat`, `entrega_lng`, `fuera_zona`, `distanciam`, `foto_url`; en `cadete`
  `intentos_ubicacion_simulada`, `ultimo_intento_ubicacion_simulada_en`. Sin script SQL.
- Claves de configuración nuevas (con default en el código, sin pantalla): `en_lugar_radio_m` (150),
  `en_lugar_precision_max_m` (100). Se guarda sola `whatsapp_template_en_camino_editado`.
- Backend y panel van juntos: el panel viejo manda el finalizar del admin sin `motivo` (400).
- APK nueva = `versionCode` 2. Después de repartirla a todos, subir `version_minima_app` a 2 para que
  las viejas (que no controlan distancia) dejen de poder entrar.

### Pedidos al otro carril (B → A)
- Ninguno obligatorio. No se tocaron las llamadas a `aprenderDeCadete` y no se agregó ninguna nueva
  para las paradas. Si A quiere aprender también de las paradas, ahora llega su GPS
  (`pedido_parada.entrega_lat/lng`, con `fuera_zona` si el cadete usó "Estoy en el lugar").

---

## Después (no empezar todavía)
Se reparte cuando los dos carriles estén en `develop`: salida a producción (ver `pendientes.md`),
**5d** avisos al dueño por WhatsApp, **5b** avisos de la calle.
