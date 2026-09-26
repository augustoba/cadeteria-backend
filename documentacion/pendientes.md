# Pendientes

Última actualización: 2026-09-26.

> El backlog largo (las 11 rondas de propuestas) vive en `MEJORAS-PROPUESTAS.md`, en la raíz
> del proyecto. **Ese archivo está fuera de cualquier repo git**, así que no viaja con el
> código. Este archivo es el pendiente corto y accionable, versionado acá a propósito.

Todo el trabajo nuevo va en la rama **`develop`** de los 3 repos (`cadeteria`, `admin-front`,
`cadete-app`). Lo del 2026-09-24 al 26 está en la rama **`pendientes-2026-09-24`** de los 3, sin
mergear ni pushear todavía.

## Plan de salida (acordado con el cliente el 2026-09-25)

1. **Salir así**: la cadetería carga los pedidos que le llegan por WhatsApp (cotiza solo por
   distancia, no hace falta alguien que sepa de calles ni de precios). Direcciones difíciles: link
   de Google Maps pegado, que se va guardando en la base propia.
2. `/pedir` **no se les da a los clientes** todavía; **asignación automática apagada**.
3. Probar el gateway de WhatsApp, desplegar backend y panel.
4. Probar la APK en celulares propios y de empleados (no cadetes), con gente ajena al desarrollo.
5. Pasar la APK a todos los cadetes (cambio de sistema en un día fijo, con el sistema viejo de
   respaldo una semana).
6. Probar la asignación automática en horarios de pocos pedidos (fines de semana).
7. Recién después, liberar `/pedir` (ver 5a).

Objetivo de fondo: que el dueño pueda salir a buscar clientes. Minimizar lo que necesita un
operador en la oficina (los reclamos se resuelven entre cliente y cadete y se cierran solos).

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

## Hecho el 2026-09-25 y 26 (misma rama `pendientes-2026-09-24`, sin pushear)

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
- **Direcciones habituales del cliente**: en "Nuevo pedido", al escribir el teléfono aparecen hasta
  5 direcciones que ya usó (compara solo dígitos), con "Usar como origen / destino".
- **Objetos de valor suman recargo** igual que el dinero (sobre la suma de ambos), en el alta del
  panel, `/pedir` y la revisión de solicitudes.
- **Página de seguimiento** (`/seguimiento/{token}`):
  - Pasos: Pedido recibido, En camino, Retirado, Entregado, con texto según el momento.
  - Tarjeta del cadete desde que se asigna: foto, nombre y apellido, DNI, teléfono (llamar y
    WhatsApp), foto y patente del vehículo, alias/CBU con "Copiar".
  - Fotos del viaje: retiro (desde que retira), entrega y firma (al entregar).
  - Descargar comprobante desde "En camino". Logo y nombre de la cadetería arriba.
  - Aviso de verificar al cadete (datos o QR) antes de entregar el pedido, dinero o valores.
  - El link vale hasta las 23:59 del día en que terminó el pedido (salvo reclamo abierto).
  - Solo por token: sin búsqueda por número ni teléfono.
- **Comprobante (PDF)**: encabezado logo | CADEM CADETERÍA | comprobante; sin teléfono fijo ni
  "Firma de conformidad"; muestra objetos de valor y el teléfono del cadete; logo rehecho (el viejo
  tenía bordes blancos en las letras).
- **Mensajes al cliente** con `{marca}` y `{numero}` (plantillas editables); el de "cadete aceptó"
  pide verificar al cadete o escanear el QR.
- **QR del pedido en la app**: botón "Mostrar QR al cliente" durante el viaje; abre el seguimiento.
- **Reclamos del cliente** desde el seguimiento (botón según el momento, con confirmación):
  - Demora en el retiro / demora en la entrega: avisan al cadete (app y push) y se cierran solos
    cuando retira / entrega.
  - Problema con la entrega (el cliente escribe qué pasó): abre un **incidente GRAVE** vinculado al
    pedido; **mientras esté abierto el cadete no recibe pedidos** (automática, candidatos, manual y
    lote). A los `reclamo_seguimiento_min` (10) se le escribe al cliente por WhatsApp (o SMS); si no
    responde en `reclamo_cierre_min` (10) se cierra solo. "Ya se solucionó" lo cierra; "Sigue el
    problema" abre el WhatsApp de atención (`whatsapp_atencion_cliente`, puede ser el celular del
    dueño) y queda abierto hasta que un admin lo cierre.
  - App del cadete: recuadro rojo del reclamo en el viaje (con Llamar/WhatsApp) y en el historial;
    aviso en inicio mientras esté bloqueado; la push del reclamo abre el viaje.
  - Panel: filas de color por tipo (ámbar retiro, naranja entrega, rojo problema) que parpadean
    hasta "Visto"; "Cerrar"; cuadro "Reclamos abiertos" con los ya entregados; alerta con sonido.
- **Panel, dashboard**: pedidos sin asignar hace más de `minutos_pedido_urgente_reintentar` (30)
  parpadean en violeta; selector "Ordenar" (reclamos primero, demorados en retirar, sin asignar hace
  más tiempo); leyenda de colores.
- **Sonidos del panel**: uno distinto por tipo de aviso (mensaje de cadete, pedido nuevo, pedido web,
  reclamo por demora, problema con la entrega como alarma, alertas operativas). Antes era el mismo
  beep y varias alertas no sonaban. Configuración → "Sonidos del panel" para escucharlos.
- **Registro de cadete**: al enviar, cartel de que le llega un mail al darlo de alta o si hay que
  corregir. **Ficha del cadete**: apellido, DNI, fotos y datos en solapas.
- **Demo**: al arrancar con `DEMO_ENABLED=true` se siembra un pedido por cada situación (incluidos
  los tres reclamos y uno sin asignar hace 45 min) y se imprimen en el log los links de seguimiento
  de cada uno, para mostrarle al cliente.
- **`documentacion/despliegue.md`**: checklist de producción (variables, mail, APK, cosas de prueba
  a apagar, cuentas externas).
- **Bugs encontrados y arreglados**: `whatsapp_mensaje.texto` era TINYTEXT (todo WhatsApp de más de
  255 caracteres fallaba); `/api/publico/configuracion` respondía XML que el navegador cacheaba (la
  página de seguimiento mostraba "Algo salió mal"); la columna de observaciones de las solicitudes
  de cadete no se creaba (`columnDefinition` entre comillas); el job de reclamos deshacía el cierre
  si fallaba un envío.

## ⚠️ Falta probar (no se probó todavía)

Lo de arriba se probó con tests (153 del backend), compilando panel y APK, y en su mayoría con
pruebas contra el backend en la base `cadeteria_prueba_claude`. **No se probó:**
- **La APK en el emulador ni en un teléfono** con los cambios de estos días: recuadro del reclamo,
  QR, aviso de bloqueo en inicio, que la push del reclamo abra el viaje. Hay que recompilarla y
  reinstalarla.
- **El panel en el navegador**: filas que parpadean y sus colores, "Visto" y "Cerrar", cuadro
  "Reclamos abiertos", selector "Ordenar", leyenda, violeta de sin asignar. (Solo se compiló.)
- **Página de seguimiento en el navegador**: botones "Ya se solucionó" / "Sigue el problema", campo
  de texto del reclamo, aviso de verificar al cadete y logo. (Sí se vieron los 4 pasos, la tarjeta
  del cadete, las fotos y el comprobante.)
- **Push a la app** (necesita Firebase configurado) y **WhatsApp real**: el seguimiento del reclamo
  solo se probó con `WHATSAPP_MODO_SIMULADO=true`.
- **Mails reales** (alta, corrección y rechazo de cadetes): no hay SMTP configurado en esta PC.
- **Los sonidos del panel** en el navegador (solo se compiló). Ojo: el navegador no deja sonar nada
  hasta que alguien hace un clic en la página después de abrirla.
- **Link corto de Google Maps** de la app del celular (`maps.app.goo.gl`): no se probó con uno real.
- **Registro de cadete con DNI repetido** en la base real (se probó en la de prueba).
- Que el QR se lea desde otro celular (necesita `FRONT_BASE_URL` pública; con `localhost` no abre).

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

### 5a. Próxima etapa (antes de liberar `/pedir`): límite de pedidos por cliente
Hoy hay un límite general por IP para todo `/api/publico/**` (60 por minuto, configurable con
`rate_limit_publico_max` / `_ventana_seg`) más lo de `spec-antiabuso-pedidos-publicos.md`. Para
abrir `/pedir` al público revisar: que el IP se tome bien detrás del proxy del hosting
(`X-Forwarded-For` se puede falsificar si el backend queda expuesto directo), un tope por
teléfono además de por IP, y límites más bajos para los endpoints que gastan cupo (buscar,
buscar-ampliado con Google, link de Google Maps, cotizar).

### 5c. Mensajes al cliente: hoy salen solo por SMS
"El cadete aceptó" / "fue entregado" / "reenviar link" van por `SmsGatewayService`: sin el
gateway de SMS no le llega nada al cliente, aunque el de WhatsApp esté conectado. Evaluar que
salgan por WhatsApp (y SMS de respaldo), igual que el código de verificación.

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
- `whatsapp_respuesta.texto` también es TINYTEXT (`@Lob`): una respuesta de un cliente de más de 255
  caracteres fallaría al guardarse. Mismo arreglo que `whatsapp_mensaje` (length 4000).
- La vista **Kanban** del dashboard todavía no muestra los colores de reclamos ni de sin asignar.
