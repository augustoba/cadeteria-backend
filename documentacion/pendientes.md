# Pendientes

Última actualización: 2026-09-25.

> El backlog largo (las 11 rondas de propuestas) vive en `MEJORAS-PROPUESTAS.md`, en la raíz
> del proyecto. **Ese archivo está fuera de cualquier repo git**, así que no viaja con el
> código. Este archivo es el pendiente corto y accionable, versionado acá a propósito.

Todo el trabajo nuevo va en la rama **`develop`** de los 3 repos (`cadeteria`, `admin-front`,
`cadete-app`). Lo del 2026-09-24 está en la rama **`pendientes-2026-09-24`** de los 3, sin
mergear ni pushear todavía.

---

## Cerrado el 2026-09-24

| # anterior | Qué era | Cómo quedó |
| --- | --- | --- |
| 1 | Rebote del pedido al cadete que lo rechaza | Hecho el 2026-09-21 (`d5d8353`). El cartel con las reglas quedó en la pantalla de oferta nueva |
| 2 | El mapa del viaje no mostraba el origen | Ya estaba: pins de origen (naranja), destino (rojo) y cadete (azul) en `ViajeScreen.MapaViaje` |
| 3 | Bug de "Test Cadete" sin explicar | **Cerrado sin reproducir**: el matching se reescribió entero por distancia (`asignacion-por-distancia`, 2026-09-23) y el código donde pasó ya no existe. Si vuelve a aparecer, se abre de nuevo con datos del matching nuevo |
| 4 | Ver el pulso en el teléfono | Visto en el emulador (Pixel 5): el anillo aparece en LIBRE |
| 5 | Sonido del contador + `spec-app-mejoras-visuales.md` | Sonido hecho el 2026-09-21; el resto del spec (fases A–G) hecho el 2026-09-24 |
| 5 | `spec-antiabuso-pedidos-publicos.md` | Fases 1–4 hechas el 2026-09-24 (backend + panel + app) |
| 6 | Filas huérfanas del seeder demo | `DemoPedidoSeeder` borra todas las tablas hijas del pedido demo (excluidos, paradas, log de precio, push); antes podían romper el arranque por FK |
| 7 | Deudas de documentación | Seeder con DNI como usuario, README de la app (`./gradlew` anda), `frontend.md` sin las carpetas muertas |
| — | Fase 4 de `spec-optimizacion-datos.md` | Auditoría hecha, ver §7 del spec |

Además, pedido el 2026-09-24:
- **Piso, depto y observaciones por dirección** (origen y destino; piso y depto separados) en `/pedir` y en el alta del
  panel; el cadete los ve (junto con el detalle del pedido) recién al aceptar.
- `/pedir`: el cliente puede pedir moto; el alta del panel suma "Transporta valores".
- **Métricas → "Cómo entraron los pedidos"**: online vs. cargados en el panel, y por usuario.
- **Bug encontrado y arreglado**: si la foto de un viaje encolado sin señal se borraba del
  teléfono, el backend rechazaba el "Finalizar" sin foto y el viaje quedaba trabado en la cola
  para siempre. Ahora la app avisa `archivoPerdido` y queda un comentario automático.

## Hecho el 2026-09-25 (misma rama `pendientes-2026-09-24`, sin pushear)

Direcciones que el buscador no encuentra — para ir llenando la base propia sin gastar Google:
- **Pegar un link de Google Maps** en el mismo campo de dirección (admin y `/pedir`): link largo
  de la PC (Street View o lugar marcado) o corto de "Compartir" en la app (`maps.app.goo.gl`, lo
  resuelve el backend siguiendo la redirección, solo dominios de Google). No usa la API de Google.
  Botón "buscala en Google Maps" cuando no aparece.
- **Pin**: doble clic lo pone, arrastrar lo ajusta; con el pin quieto 1 s se consulta la calle y se
  muestra arriba del pin. Ya no pisa lo escrito (antes el reverse reemplazaba "Colombia 4695" por
  "Colombia" y se perdía la altura). Aviso si el pin quedó sobre otra calle.
- **Se aprende el pin** al crear el pedido (panel) o al confirmar la solicitud (`/pedir`, recién
  cuando el admin la revisa): calle del reverse + altura escrita, proveedor `manual` o
  `google_link`, solo si la calle escrita coincide con la del pin.
- **Ubicaciones de Google (API) en la cache**, con vencimiento: Configuración → Integraciones →
  "Direcciones encontradas con Google" (días, default 30; 0 = no guardar). Si una fuente propia
  confirma esa cuadra, la pisa y deja de vencer. Opción para que también venzan las de link.
- `/pedir`: si tilda "lleva dinero" o "transporta valores", el monto es obligatorio; el celular
  es obligatorio y con característica (10 a 13 dígitos). Lo valida el front y también el backend.
- **Recargo por volver al origen**: porcentaje del precio del viaje (no del recargo por dinero),
  Configuración → Pedidos → Tarifas, clave `recargo_retorno_origen_porcentaje`, default 50%. Se
  suma en el estimado de `/pedir` y en la sugerencia de precio al revisar la solicitud.
- **Alta de cadete con corrección**: el admin marca con ✕ cada dato o foto mal, con el motivo, y
  "Pedir corrección" le manda un mail con la lista y el mismo link, que abre el formulario
  precargado (las fotos marcadas hay que subirlas de nuevo). "Reenviar link" renueva 7 días (y
  reenvía el mail si estaba a corregir). "Rechazar del todo" también avisa por mail con el motivo.
- **Registro**: el usuario es el DNI (ya no se pide aparte). Un DNI que ya estuvo registrado no se
  bloquea: el admin ve "nunca se registró" o "ya estuvo registrado" con el motivo de la última baja
  y el link a la ficha. Si lo aprueba, se reactiva el mismo cadete (conserva su historial); si hay
  uno ACTIVO con ese DNI, no deja aprobar.
- **Ficha del cadete** con solapas (desempeño, datos personales, vehículo, incidencias, altas y
  bajas) y la foto arriba en todas.
- ⚠️ Los mails (corrección, rechazo, alta) salen solo con `app.mail.host` configurado; si no, el
  panel muestra el link para pasarlo a mano.
- La cache ya no toma en cuenta las filas aproximadas viejas (anteriores al 2026-09-24): hacían
  parecer ambigua una cuadra y la búsqueda no encontraba la ubicación buena.

## Abierto

### 1. Probar en un teléfono real
Todo lo del 2026-09-24 se probó con tests, con un script de punta a punta contra el backend
(22 chequeos) y en el emulador. Falta el teléfono real: sonido del contador, recordatorio de
30 minutos (tarda 30 minutos en aparecer) y las tipografías en un celular chico.

### 2. Mergear `pendientes-2026-09-24` → `develop` → `main`
En los 3 repos. El backend se edita también desde otra PC: pullear antes de mergear.

### 3. ⚠️ Probar y prender el código de verificación de `/pedir`
**Apagado a propósito el 2026-09-24** para poder probar `/pedir` sin SMS ni WhatsApp: hoy el
cliente pide sin código (Configuración → "Pedir un código al teléfono del cliente…", clave
`verificacion_telefono_activa`, default `false`). Mientras esté apagado, cualquiera puede cargar
un pedido a nombre de otro número — el admin igual revisa cada solicitud antes de confirmarla.

Pendiente: conectar el primer chip de WhatsApp (o prender `SMS_ENABLED` con el gateway de SMS),
probar de punta a punta que el código llegue al celular del cliente, y recién ahí prender la
opción. En desarrollo se puede probar el circuito con `WHATSAPP_MODO_SIMULADO=true` (el código
aparece en WhatsApp → Mensajes enviados; nunca en producción, Salud del sistema lo marca en rojo).

### 3b. Google para "No está mi dirección — buscar de nuevo" (opcional)
El segundo intento del buscador de direcciones usa Google si hay una key en Configuración →
"Google — API keys" (Geocoding API de Google Cloud; tope propio de 300/día por key ≈ 9.000/mes,
dentro del cupo sin cargo). Sin key, ese intento vuelve a probar los servicios gratuitos
salteando la memoria de direcciones. **Antes de cargar la key, revisar las condiciones de
Google Maps Platform**: no permiten mostrar sus resultados sobre un mapa que no sea de Google (el
panel y `/pedir` usan OpenStreetMap). Guardarlos sí, hasta 30 días: desde el 2026-09-25 van a la
cache con vencimiento (`google_cache_dias`). Una sola cuenta: rotar cuentas para multiplicar el
cupo gratis va contra sus condiciones.

### 3c. ⚠️ Antes de producción: "No borrar (solo para pruebas)"
Configuración → "Direcciones encontradas con Google" tiene un tilde **No borrar** (clave
`google_cache_pausar_borrado`) para probar en dev sin perder datos: mientras está tildado no se
borran ni se ignoran las ubicaciones de Google vencidas. **En producción tiene que estar
destildado**; antes de desplegar, sacar la opción o dejarla solo para un rol superadmin (hoy no
existe ese rol). También revisar que `google_cache_dias` siga en lo que permitan las condiciones
de Google en ese momento.

### 3e. App del cadete: sacar "Cambiar servidor" en producción
El login de la app tiene "Cambiar servidor" y la URL elegida queda guardada en el teléfono,
pisando la de fábrica (`CADETE_APP_DEFAULT_BASE_URL`). En producción el servidor tiene que
venir solo del código: mostrar el botón solo en debug (`BuildConfig.DEBUG`) y que release
ignore la URL guardada. Anotado también en `despliegue.md` (§2).

### 3d. `/pedir`: textos del link de Google Maps para clientes
El buscador de `/pedir` es el mismo componente que el del panel, así que ya acepta el link. Antes
de liberar `/pedir`, revisar los textos pensando en un cliente desde el celular (ej. "tocá
Compartir → Copiar y pegalo acá").

### 4. Evaluar autoalojar el motor de ruteo
Sin cambios respecto del 2026-09-21 — ver el razonamiento en el historial de este archivo
(`git log -p documentacion/pendientes.md`). Ya hay un OSRM propio levantado para el matching
de trazas (`spec-routing-propio.md`); la Fase C (tiempos por tramo) sigue sin umbral definido.

### 5. Importador de direcciones del sistema viejo
Esperando que se termine de limpiar a mano el Excel (`importador-direcciones/exportes/`).

### 5b. Idea a futuro: avisos de controles de tránsito entre cadetes (solo analizado)
Pedido el 2026-09-25, estilo Waze: el cadete toca "Avisar control", queda registrado con su
ubicación y hora, y a los cadetes que pasan a menos de ~500 m les llega "Control avisado a las
12:00 en Mate de Luna 2400". Análisis en la conversación de ese día; puntos a decidir antes de
hacerlo: cuánto dura un aviso (sugerido: 60–90 min, con "sigue ahí / ya no está" de otros
cadetes para extenderlo o bajarlo), no avisar mientras maneja (solo sonido/vibración y texto
corto), qué pasa con avisos falsos, y el tema legal (ver la conversación).

### 6. Menores anotados
- `GET /api/admin/clientes`: N+1 chico acotado por página (ver `spec-optimizacion-datos.md` §7).
- El bundle del panel supera el presupuesto de 500 kB (ya pasaba antes: 509,7 kB en `develop`).
- `cadeteria-apk/app/build/outputs/apk/debug/app-debug.apk` está versionado: cada build lo
  deja modificado en git.
- Con JDK 21 el `assembleDebug` falla en `jlink` (AGP 8.5): compilar con un JDK 17
  (`-Dorg.gradle.java.home=...jbr-17...`).
