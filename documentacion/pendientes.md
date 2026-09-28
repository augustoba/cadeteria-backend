# Pendientes

Última actualización: 2026-09-28.

> El backlog largo (las 11 rondas de propuestas) vive en `MEJORAS-PROPUESTAS.md`, en la raíz
> del proyecto. **Ese archivo está fuera de cualquier repo git**, así que no viaja con el
> código. Este archivo es el pendiente corto y accionable, versionado acá a propósito.

Todo el trabajo nuevo va en la rama **`develop`** de los 3 repos (`cadeteria`, `admin-front`,
`cadete-app`). Lo del 2026-09-24 al 26 ya está en `develop` (pusheado el 2026-09-26), salvo la
rama `aviso-whatsapp-web` (backend + panel, aviso "en camino"), sin pushear al 2026-09-28.

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

## Plan acordado el 2026-09-28 (en este orden)

Primero los arreglos de lo que se usa todos los días, después la salida y recién ahí lo nuevo.

**Los puntos 1-5 se hacen en paralelo entre dos IAs**: reparto, archivos de cada una y reglas en
`plan-carriles-2026-09-28.md` (carril A = 2, 3, 4; carril B = 1, 5).

| # | Qué | Toca | Detalle |
| --- | --- | --- | --- |
| 1 | **3n. Editar el aviso "en camino" desde el panel** | panel + back (poco) | Configuración → WhatsApp: texto con botones para `{cadete}` `{numero}` `{link}` `{marca}`, vista previa con un **pedido real reciente**, aviso si falta `{link}`, "Volver al texto original". Lo edita el admin. Guardar quién lo editó y cuándo (una línea, sin historial) |
| 2 | **3j. Pin del mapa** | back + panel | El cartel "el pin está sobre X" mira primero la base propia y no avisa si OpenStreetMap no trae calle o trae una sin nombre (nunca bloquea). Pin del **cliente** (`/pedir`): nunca se aprende. Pin del **operador**: se guarda **sin confirmar** (sirve de sugerencia); lo confirma el GPS del cadete al marcar **Entregado** a menos de ~50 m, y si está más lejos gana el punto del cadete. **Un pin nunca pisa un punto ya confirmado por un cadete** (`cadete_gps` / `android_geocoder`). Si el admin finaliza a mano, el pin queda sin confirmar (dato débil, que se vea así en reportes) |
| 3 | **3i(4). Mirar el panel no suma confirmaciones** | back | `reverse` del panel (`alimentarCacheSiEsPreciso`) no suma `confirmaciones` a lo que devuelve Nominatim. El 3i(2) (punto aprendido más cercano) queda para después: es cosmético |
| 4 | **3h. "colom 4600" → "colombia"** | back | Sin alias exacto: calles conocidas que **empiecen** con lo tipeado (mínimo 4 letras). Una sola → se usa; varias → se muestran todas como opciones. **Corregir el nombre antes de preguntar afuera** (a Nominatim/Google les llega "colombia 4600"). Errores de tipeo (fuzzy): después, es donde más riesgo hay de corregir mal |
| 5 | **Retirado / Entregado solo en el lugar** (nuevo, ver abajo) | APK + back | 1-2 días |
| 6 | **Salida a producción** | — | "Falta probar", 3e, 3c, script de alias en producción, `develop` → `main`, `despliegue.md` |
| 7 | **5d. Avisos al dueño por WhatsApp** | back | Reclamo sin resolver, pedido sin asignar hace X min, cadete pendiente de aprobación; con escalamiento (primero el panel) |
| 8 | **5b. Avisos de la calle** | APK + back + panel | Ver 5b. Presentarlo como "avisos de la calle", no como anti-controles |

### Retirado / Entregado solo en el lugar — acordado 2026-09-28
Hoy el backend **deja finalizar sin Retirado**, la posición que manda la app al marcar se guarda
pero **no se controla**, y no se detecta **GPS falso**. Sí existe la cola sin señal
(`PendingActionsStore`: guarda Retirado/Finalizar con foto y posición y los manda al volver la
conexión).

Decidido:
1. **Orden obligatorio**: no se puede marcar Entregado sin Retirado.
2. **Radio de 150 m**: la app compara su posición con el origen (Retirado), cada parada y el
   destino (Entregado). Más lejos → no deja y dice "Estás a 800 m del retiro". **El backend vuelve a
   controlar** con la posición que recibe (no confiar solo en el teléfono).
3. **GPS falso** (ubicación simulada de Android): no deja marcar y queda registrado.
4. **Sin señal**: el GPS anda sin datos (tarda más en ubicarse: mostrar "Buscando tu ubicación…").
   El control se hace en el teléfono y la acción va a la cola con la **hora en que se tocó el
   botón** y su posición/precisión; el backend usa esa hora (no la de llegada) para registrar y
   controlar.
5. **"Estoy en el lugar"** cuando no lo deja (ej. dirección mal ubicada, como Colombia 4695): pide
   **foto obligatoria**, marca igual y **nadie lo aprueba**. Queda en rojo "fuera de zona" en el
   pedido y **en los registros del cadete** (cuántas veces lo usó, visible en su ficha y en
   Métricas). Si varios cadetes lo usan en el mismo punto, la dirección estaba mal cargada (se
   corrige con lo de 3j).
6. **GPS impreciso** (error > ~100 m, típico adentro de un local): no bloquea, marca con la
   advertencia "ubicación imprecisa" (también queda registrado).
7. **Finalizado por el admin** (desde el panel): se saltea el control de distancia, pero el admin
   **tiene que escribir el motivo** (obligatorio). Queda registrado quién, cuándo y por qué, y el
   pedido figura como **"Finalizado por el admin"** (en el pedido, el seguimiento del panel y el
   historial del cadete).

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
  - Con un reclamo por problema abierto: la hora a la que se cierra solo si el cliente no responde.
    A propósito **no** se le dice al cliente que el cadete queda bloqueado (es interno y le daría
    presión sobre el cadete).
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

## Hecho el 2026-09-26 (noche): errores, validaciones y doble toque

- **Cada error con su código HTTP** (antes casi todo era 400 o 500): 400 dato inválido, 401 login,
  403 sin permiso, 404, 405, 409 conflicto (la oferta venció, ya lo asignó otro admin, el reclamo
  ya se cerró, DNI duplicado), 410 link vencido o ya usado, 413 archivo grande, 415, 429 demasiados
  intentos, 503 servicio externo sin cupo. El cuerpo trae `codigo` fijo (ej. `CONFLICTO`,
  `VALIDACION`) además del `message`, y en validaciones `fieldErrors` campo por campo.
- **Los 500 ya no muestran el error interno** (podía traer el SQL): dicen "avisá a soporte con el
  código XXXX" y ese código queda en el log con el detalle. Antes no se logueaban.
- **Los errores siempre en JSON**: sin encabezado `Accept` (la APK no lo manda) salían en XML.
- **Validaciones en backend + panel + APK** con las mismas reglas (`common/Validaciones.java`,
  `core/utils/validaciones.ts`, `util/Validaciones.kt`): nombre y apellido solo letras, DNI 7 u 8
  números, teléfono 7 a 15 dígitos, email, patente de moto 123ABC o A123BCD (se guarda en
  mayúsculas sin espacios), color, marca y modelo, CBU 22 números, alias 6-20, contraseña 6-72,
  código de 6 números, montos no negativos, coordenadas válidas y largos máximos de cada texto
  (antes un texto largo tiraba 500). El nombre del **cliente** acepta números (puede ser un comercio).
- **Formulario de alta de cadete**: DNI de 7 u 8 números, patente obligatoria para moto, fotos
  obligatorias también en el backend (antes solo las pedía el front) y casilla **"Declaro que soy
  mayor de 18 años"**: se guarda cuándo la tildó y el panel lo muestra al revisar la solicitud. Al
  crear un cadete desde el panel, el admin tiene que tildar "Verifiqué que es mayor de 18" (queda
  guardado quién y cuándo).
- **Doble toque / reintento de la app**: aceptar, retirar y finalizar repetidos devuelven OK sin
  cobrar otra vez la comisión ni mandar otro SMS. Antes la cola sin conexión de la APK quedaba
  trabada para siempre si se perdía la respuesta. Las acciones sobre un pedido y el crédito del
  cadete ahora bloquean la fila, así que dos a la vez van una detrás de otra.
- **Límite por IP**: ya no se saltea con un `X-Forwarded-For` inventado (ver `TRUSTED_PROXIES` en
  `despliegue.md`).
- **Login**: ahora dice el motivo real (contraseña mal 401, cuenta bloqueada 429, cuota impaga 403,
  temporal vencida 401), también en la APK. Y **el bloqueo por intentos fallidos no andaba**: la
  excepción deshacía la transacción y el contador nunca subía. Arreglado y probado en vivo.
- **Columnas `@Lob`** (`incidencia.descripcion`, `zona.poligono`, `cadete.notas_internas`,
  `whatsapp_respuesta.texto`): en una base nueva quedaban TINYTEXT (255) — en producción guardar un
  polígono de zona habría fallado. Ahora tienen largo explícito.
- Tests: backend 178 (antes 158), panel 13, APK 14. En vivo contra `cadeteria_prueba_claude`: 16
  chequeos (códigos, login y bloqueo, 3 "Aceptar" a la vez → comisión una vez, retiro repetido, IP).

## Hecho el 2026-09-26 (madrugada): aviso de llegada al retiro y a la entrega (APK)

- Cuando el cadete **se queda ~40 s a menos de 150 m** del origen de un viaje EN CURSO sin haber
  marcado Retirado, el teléfono le avisa: "Llegaste al retiro — pedido #N. No te olvides de marcar
  Retirado". Lo mismo con cada parada sin entregar y con el destino ("Entregado"). Al tocarlo abre
  el viaje; **no marca nada solo** (el retiro puede pedir foto y la entrega nombre y firma).
- Un solo aviso por punto; pasar por al lado sin quedarse no avisa; si ya lo marcó, no sale (antes
  de avisar se releen los viajes) y si el aviso quedó en la barra, se borra solo.
- Es **local del teléfono**: no depende del servidor ni de Firebase. Usa el servicio de ubicación
  que ya corre mientras el cadete está disponible (no la API de geofences, que pedía el permiso de
  ubicación "todo el tiempo"). Pings con más de 250 m de error no cuentan.
- Canal de notificación propio, "Llegada al retiro o entrega" (sonido normal, vibración): el
  cadete lo puede ajustar aparte en Ajustes.
- Código: `location/AvisoLlegada.kt` (lógica, 6 tests) y `LocationTrackingService`. Radio, tiempo
  y precisión son constantes en `AvisoLlegada` (no configurables desde el panel todavía).
- Ojo: con `frecuencia_ubicacion_seg` alto (ej. 120) el aviso tarda más (hacen falta 2 pings
  adentro). Con el default (45 s) llega ~45–90 s después de llegar.

## Hecho el 2026-09-26 (madrugada): la cache de direcciones aprende del cadete y del respaldo

- **Retirado y Entregado alimentan la cache** (`cuadra_coords`, proveedor `cadete_gps`): la
  dirección escrita en el pedido + el GPS del teléfono en la puerta. La APK ahora pide una lectura
  de GPS **nueva y precisa** al marcar (antes mandaba la última guardada, que podía tener minutos y
  cientos de metros de error) y manda su precisión. El backend aprende solo si el error es
  ≤ `aprender_gps_precision_max_m` (50 m), si cae a menos de 2 km del pin del pedido y si el reverse
  confirma que es la misma calle (si el cadete marcó en otra calle, no se aprende). Lo encolado sin
  conexión no trae precisión y no enseña.
- **Qué fuente pisa a cuál en una cuadra**: pin puesto a mano > GPS del cadete / link de Google
  Maps > buscadores gratuitos > Google API. Antes ganaba siempre la primera que llegaba (un pin
  corregido a mano no pisaba lo que había interpolado un buscador).
- **Reverse con respaldo**: si Nominatim no encuentra la calle o no sabe la altura de un punto, se
  prueba LocationIQ y después Geoapify (hasta `reverse_respaldo_max_dia` = 500 por día, comparten
  cupo con el buscador). Sirve para el mapeo de calles de los cadetes, para confirmar la calle al
  aprender y para la calle que se muestra arriba del pin en el panel.
- En vivo contra `cadeteria_prueba_claude`: de 6 puntos de Tucumán, en 2 (Plaza Independencia, Las
  Talitas) Nominatim no daba la altura y la dio Geoapify. Un Retirado en "San Martín 650" con GPS a
  20 m y 12 m de precisión guardó la cuadra 600 y después "San Martín 620" salió de la cache sin
  consultar a nadie; con 180 m de precisión no aprendió; con una dirección de otra calle tampoco.
- Tests: backend 180, APK 20.

## Hecho el 2026-09-26 (mañana): calle del Geocoder del teléfono

- La APK resuelve la calle y altura de su posición con el **Geocoder de Android** (datos de Google,
  gratis, sin key) y la manda en el ping de ubicación. No en cada ping: cada ~120 m o 2 minutos, y
  solo con precisión ≤ 30 m (con un punto impreciso devuelve la cuadra de al lado). Si no la
  encuentra, la posición se manda igual. Android 13+ usa la versión asíncrona; antes, la bloqueante
  en segundo plano.
- El backend la guarda en la cache como `android_geocoder` (confianza de buscador gratuito: nunca
  pisa un pin manual ni el GPS de Retirado/Entregado), con la precisión ≤
  `aprender_geocoder_precision_max_m`. **Vence como lo de Google** (`google_cache_dias`) y la pausa
  de dev la cubre: zona gris con las condiciones de Google (datos de Google guardados en una base
  propia). Si se decide no usarlo más, se borra con `delete from cuadra_coords where proveedor='android_geocoder'`.
- **Abreviaturas**: "Av. Gral. Paz" se guarda como "Avenida General Paz" (así escribe OSM), con
  alias de la forma abreviada; sin esto la misma calle quedaba repetida con dos nombres. Nombres
  que difieren en más que abreviaturas (ej. "Juan B. Justo" vs "Juan Bautista Justo") todavía
  pueden duplicarse: mirar en la semana de pruebas.
- El mapeo de calles no vuelve a consultar a Nominatim/LocationIQ/Geoapify el punto de un cadete
  cuyo teléfono ya mandó la calle en ese intervalo (ahorra cupo).
- **Retirado/Entregado** mandan también la calle del teléfono: si el reverse (OSM) no confirma la
  calle del pedido pero el teléfono sí, se aprende igual.
- APK vieja (sin estos campos) sigue funcionando igual. Tests: backend 185, APK 23. En vivo: ping
  con calle y 12 m → guardado y después "Av Mate de Luna 2480" salió de la cache; con 90 m no se
  guarda; datos inválidos → 400.
- Para comparar en la semana de recorridos: `select proveedor, count(*) from cuadra_coords group by proveedor;`

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
- **Validaciones en pantalla (2026-09-26)**: el formulario de alta con la casilla de mayor de 18 y
  la patente, el alta de cadete del panel, nuevo pedido y `/pedir` — solo se compiló el panel. En
  la APK: mensajes del servidor al aceptar/finalizar/login y las validaciones del perfil (solo se
  compiló y corrieron los tests).
- **El GPS preciso al marcar Retirado/Entregado en un teléfono real**: cuánto tarda en fijar
  (espera hasta 8 s) y qué precisión da; si en la calle la precisión suele pasar de 50 m, subir
  `aprender_gps_precision_max_m` con cuidado.
- **El aviso de llegada en la calle** (solo tests): que llegue con la pantalla apagada, que no
  avise al pasar por al lado y que no salga si ya se marcó Retirado. Se prueba mañana con el túnel.
- **Cadetes ya cargados con datos que no cumplen el formato nuevo** (ej. una patente de auto o un
  nombre con números): al editarlos, el panel va a pedir corregir ese dato antes de guardar.

## Abierto

### 1. Probar en un teléfono real
Todo lo del 2026-09-24 se probó con tests, con un script de punta a punta contra el backend
(22 chequeos) y en el emulador. Falta el teléfono real: sonido del contador, recordatorio de
30 minutos (tarda 30 minutos en aparecer) y las tipografías en un celular chico.

### 2. Pasar `develop` → `main`
El 2026-09-26 `pendientes-2026-09-24` se pasó a `develop` en los 3 repos (avance directo, sin
conflictos: `develop` no tenía nada nuevo de la otra PC) y se pusheó todo. Falta `main`, cuando se
pruebe lo de "Falta probar". En la otra PC: pullear `develop` antes de seguir trabajando.

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

### 3f. App del cadete: permiso de ubicación, servicio que no arrancaba solo y login con DNI
**Hecho el 2026-09-26 (APK, rama `apk-permiso-servicio`)**: `PermisoUbicacionScreen` bloquea toda la
app (login incluido) sin permiso de ubicación **precisa**, con "Dar permiso" → "Abrir ajustes" cuando
Android ya no muestra el cartel (probado en el emulador: 1er no, 2do no, ajustes); `HomeScreen`
arranca el servicio si el cadete no está Desconectado (probado en el emulador: arrancó sin tocar el
estado; el envío real no, el emulador no tiene servicios de ubicación de Google → confirmar en un
celular); `LocationTrackingService` reemplaza la escucha anterior (antes Libre → Ocupado duplicaba
los envíos); login con usuario solo numérico. **Sigue abierto**: la precisión en la calle (abajo),
a medir en la próxima salida ahora que el respaldo ya no corre en paralelo (3k.4).

Lo que se había visto:
Visto probando en un Moto G32 el 2026-09-26: la app avisa si la **ubicación del teléfono** está
apagada (`UbicacionHabilitada.kt`), pero **no revisa el permiso de ubicación**. `HomeScreen` lo pide
al entrar (`RequestMultiplePermissions`) con un callback vacío y no hay ningún `checkSelfPermission`
de ubicación. Con el permiso denegado el cadete se puede poner **Libre**, recibir viajes y el
backend lo ve conectado **sin posición**. Si tocó "No permitir" dos veces, Android no vuelve a
preguntar nunca.

A programar (APK, ~30-40 min, después reinstalar encima; misma firma):
1. Revisar el permiso al abrir y en cada `ON_RESUME`.
2. Sin permiso: cartel fijo igual al de ubicación apagada, "Sin permiso de ubicación no podés
   recibir viajes", con botón **"Dar permiso"** (vuelve a pedirlo) que pasa a **"Abrir ajustes"**
   (`ACTION_APPLICATION_DETAILS_SETTINGS`) cuando `shouldShowRequestPermissionRationale` indica que
   Android ya no muestra el cartel.
3. No dejar pasar a **Libre** sin permiso; si se lo sacan estando libre, avisar.
4. "Solo esta vez" anda igual; cuando Android lo borra, vuelve el cartel.

**Y el servicio de ubicación que no arranca solo** (visto el mismo día: 20 min sin ubicaciones
con el cadete en Libre): `LocationServiceController.iniciar` solo se llama al tocar el botón de
estado (`HomeViewModel.toggleDisponibilidad` / `toggleOcupado`) y desde el widget. Si la app se
abre con el cadete ya **Libre u Ocupado** (reinstalación, reinicio del teléfono, Motorola que mató
el proceso), muestra "Libre" pero **no manda posición**. Arreglo: al cargar el cadete en `HomeScreen`
(y al volver a primer plano), si el estado no es DESCONECTADO y hay permiso, iniciar el servicio
(es idempotente). Mientras tanto, en la calle: Desconectado → Libre lo destraba. El panel ya lo
delata con "hace X min" en la cola de espera (rama `calle-cadete-panel`).

**Y la precisión en la calle**: `LocationTrackingService` pide `PRIORITY_BALANCED_POWER_ACCURACY`
(Wi-Fi/antenas, error de decenas a cientos de metros) y `ThrottleCalle` solo resuelve la calle con
error ≤ 30 m. En casa (Wi-Fi) anda; en la calle con datos móviles puede no mandar calle casi
nunca → el panel muestra 🗺️ (Nominatim) y se aprenden menos cuadras del teléfono. Evaluar con la
prueba del 2026-09-26: si pasa, pedir `PRIORITY_HIGH_ACCURACY` mientras el cadete está Libre o con
viaje (más batería) o, al menos, en ráfagas para resolver la calle.

Junto con esto, el **login con DNI**: el alta ya exige usuario = DNI, pero el login de la app acepta
letras y solo dice "usuario o contraseña incorrectos" → teclado numérico y aviso "tu usuario es tu
DNI, solo números". Ambos anotados también en `despliegue.md` (§2 y §7).

### 3g. Aprender direcciones al cotizar (sin esperar a guardar el pedido) — analizado 2026-09-26
Hoy un link de Google o un pin a mano se aprende recién al **crear el pedido** (panel) o al
**aprobar la solicitud** (`/pedir`), vía `aprenderPin`. Si el operador busca, ve el precio y no
guarda, se pierde. Además la cotización (`CotizacionService`) recibe solo lat/lng, no el texto.

Acordado:
- **Panel, sin botón nuevo** (el objetivo es que el operador haga lo mínimo): cuando se cotiza
  con las dos direcciones (ya pasa solo), mandar también texto + fuente de cada una y aprenderlas
  con los mismos controles de `aprenderPin` (altura al final, la calle del reverse coincide). Si
  después mueve el pin, se recotiza y se pisa con el punto corregido. **Solo si quien cotiza es
  admin.**
- **`/pedir`: botón "Cotizar" + cartel "El viaje sale $X"** en vez del precio que cambia solo
  (más claro para el cliente y menos cotizaciones/rutas por cada pin que mueve). **No aprender de
  lo que cotiza un cliente** (cualquiera puede inventar pines y ensuciar la cache del panel); del
  cliente se sigue aprendiendo al aprobar la solicitud. Variante a futuro: guardarlo "sin
  confirmar" y usarlo recién con 2-3 clientes distintos en el mismo punto (columna
  `confirmaciones` de `cuadra_coords`).

Toca backend (cotizar con texto/fuente, aprender solo con rol admin) y front (panel manda los
textos; `/pedir` botón + cartel). Rama aparte (el backend se edita también en la otra PC).

### 3h. Buscador: la base propia solo encuentra el nombre exacto de la calle — visto 2026-09-26
"colombia 4600" sale ✅ de la cache (cuadra aprendida por el teléfono, `android_geocoder`), pero
**"colom 4600" no da nada**: `DireccionCacheService.buscar` busca el alias exacto
(`findByVarianteNorm`) y los servicios de afuera tampoco entienden "colom".

A programar (backend):
1. Sin alias exacto, buscar calles de la cache que **empiecen** con lo tipeado ("colom" →
   "colombia") y tengan esa cuadra.
2. Mínimo 3-4 letras (con "sa" saldrían todas las "San…").
3. Si coinciden varias (ej. "san 800" → San Juan 800 y San Lorenzo 800), **devolver todas** como
   opciones, no adivinar.
4. Segundo paso: tolerar errores de tipeo chicos ("colombai").

Relacionado, menor: en `CadeteController.actualizarUbicacion`, `registrarCalleDelTelefono` se
llama aunque `aprenderDelTelefono` no guarde (GPS con más de 30 m de error), así que ese punto
tampoco lo consulta el respaldo por Nominatim del mapeo de calles. Llamarlo solo si guardó (el
método ya devuelve `boolean`).

### 3i. Panel: "Ubicación aproximada" del cadete ignora la calle del teléfono — visto 2026-09-26
En la cola de espera (`cadetes-libres.component.ts` → `reverse`) la ubicación del cadete se
traduce siempre con **Nominatim**, que engancha el punto a la calle más cercana de OSM: con el
cadete en Colombia 4600 mostraba **Camino del Perú 1600** (la de 2 cuadras), aunque la cache tenía
`colombia 4600` (`android_geocoder`, del teléfono) a ~10 m. Además cada consulta del panel
**suma confirmaciones** a lo que devuelve Nominatim (`camino del peru 1600` llegó a 18 solo de
mirar el panel).

A programar (backend + panel):
1. Guardar en el cadete la **última calle que mandó su teléfono** (con hora) y mostrar esa.
2. Si no hay, el punto **aprendido más cercano** de `cuadra_coords` (ej. < 30 m, prefiriendo
   `cadete_gps` / `android_geocoder`).
3. Nominatim solo como último recurso.
4. Que mirar el panel **no sume `confirmaciones`** (hoy `alimentarCacheSiEsPreciso` en `reverse`).
5. Mostrar **hace cuánto** es la ubicación ("hace 12 min"): el cadete puede figurar Libre con una
   posición vieja si la app dejó de mandar (pasó el mismo día con un Fake GPS).

**Hecho el 2026-09-26 (rama `calle-cadete-panel`, backend + front):** 1 (`cadete.calle_telefono` /
`calle_telefono_en`, se muestra con 📱 si llegó hasta 3 min antes de la última posición; si no, 🗺️
Nominatim como antes), 5 ("· hace X min" pasados 2 min) y la cola de espera ahora escucha
`/topic/admin/ubicaciones` en vivo (antes solo el Mapa). Con 📱 ya no se consulta Nominatim, así que
tampoco suma confirmaciones. **Falta** 2 (punto aprendido más cercano) y 4 (que el reverse del panel
no sume `confirmaciones` cuando cae a Nominatim). Probado en la calle el mismo día.

### 3k. Misma calle con varios nombres en la cache (prueba en la calle del 2026-09-26)
Salida de ~1 h 30 y ~25 km con 2 celulares: 301 cuadras nuevas, 113 calles. Google (teléfono) y OSM
(Nominatim) nombran distinto la misma calle y la cache las guarda separadas: "general lamadrid" /
"lamadrid" / "araoz de lamadrid gregorio", "avenida belgrano" / "avenida manuel belgrano", "avenida
general roca" / "avenida nestor kirchner" / "avenida presidente nestor kirchner" (misma avenida,
verificado por coordenadas), "mendoza" / "provincia de mendoza", etc. Buscar con un nombre no ve lo
aprendido con el otro.

Causa: `DireccionCacheService.guardar()` pasa por los alias lo tipeado, pero el nombre del
proveedor (`calleCanonica`) se usa crudo para crear el alias y para la clave de `cuadra_coords`.

Plan acordado (analizado también con otra IA, Kimi, que armó la lista):
1. **Lista curada a mano** de alias variante → canónica (~23 pares), no reglas automáticas: con
   ~120 calles es más seguro; las reglas de texto rompen trampas (camino del peru ≠ peru, bernabe
   araoz ≠ araoz de lamadrid, general paz ≠ marcos paz, juan luis nougues ≠ pasaje ambrosio
   nougues). Dudosos a verificar en el mapa: combate de san lorenzo / san lorenzo, eduardo bulnes /
   avenida bulnes, las piedras / combate de las piedras. **Decidir antes**: canónica corta
   ("lamadrid") u oficial ("araoz de lamadrid").
2. **Script SQL** con backend parado: backup (`bak_cuadra_coords`, `bak_direccion_alias`), alias,
   re-key de `cuadra_coords` y merge de las filas que chocan en la clave única (calle + localidad
   + cuadra; ~52 grupos). Sobreviviente según la confianza de fuente que ya usa el código; sumar
   `confirmaciones`.
3. **Código**: un helper `canonicalizar(nombre)` (alias → canónica) usado en `guardar()` para el
   nombre del proveedor **y** en `mismaCalle` (hoy no reconoce Roca = Kirchner y no aprende el pin).
4. **Salteo del respaldo** (`MapeoCallesCadetesService`): hoy saltea al cadete si el teléfono mandó
   calle en los últimos `intervalo` seg (10 s en la prueba) pero el teléfono la manda cada 120 m / 2
   min → el respaldo corrió casi siempre en paralelo (62 % de lo aprendido) e infló "camino del
   peru" estando quieto. Saltear si mandó calle en los últimos 2-3 min y no se movió > 120 m.
5. **Esquinas** (aparte, calidad): puntos idénticos guardados con dos calles (perú 3700 / paraguay
   3800). Promediar los puntos de cada cuadra con un contador **nuevo** de muestras — no con
   `confirmaciones`, que también suma en cada búsqueda.
**Hecho el 2026-09-26 (rama `alias-curados`)**, nombre **corto** (como busca el cliente):
- 1-2: `documentacion/sql/2026-09-26-alias-curados.sql` (23 variantes → 20 canónicas), probado
  antes sobre una copia y corrido en la base local: 324 → 269 filas, 52 grupos fusionados con la
  regla de confianza del código (no la que proponía Kimi: los pares a 0 m muestran que el respaldo
  también guarda la posición del auto, no un centro de cuadra). Respaldos en `cadeteria\backups\`
  y en la base (`bak_cuadra_coords_20260926`, `bak_direccion_alias_20260926`). **Falta correrlo
  en producción.**
- 3: `DireccionCacheService.canonicalizar()` (también con "Av." / "Gral." expandidos) en
  `guardar()`, en `buscar()` y en `aprender()` (`mismaCanonica`).
- 4: salteo del respaldo: 3 min y 120 m desde donde el teléfono resolvió (y solo si se guardó).
- 5: columna `muestras`; dos pasadas de la misma fuente "en movimiento" se promedian (peso tope 20).
  Lo del teléfono (vence) no se mezcla en una fila que no vence.
- Extra: el buscador muestra la calle de la cache prolija ("Lamadrid 650", no "lamadrid 650"), y
  **no guarda en la cache lo que un buscador devolvió para otra calle** (probando, "Avenida
  Presidente Néstor Kirchner" mal codificado dio "Presidente Perón" y creó un alias permanente
  Kirchner → Perón; borrado).
- De 3j: link de Google Maps se aprende aunque Nominatim no confirme; pin a mano si el reverse no
  trae calle (`manual_sin_confirmar`, confianza baja). Si el reverse trae OTRA calle (Colombia 4695
  → "Camino del Perú") el pin a mano sigue sin aprenderse; el link sí.

6. Próxima prueba: **con un pedido EN CURSO** (sin eso `pedido_ubicacion` queda vacía y no se ven
   huecos ni km reales) y los celulares en autos distintos.

### 3j. Buscador: "el pin está sobre X" falla donde OSM tiene calles sin nombre — visto 2026-09-26
Colombia 4695 (Barrio Tarcos): en OpenStreetMap las calles a 24 y 47 m del punto **no tienen
nombre**, Colombia está a 65 m y no hay alturas cargadas. Resultado: escribir "Colombia 4695" pone
el pin en otra altura, y al moverlo a la puerta el aviso `calleDistinta` del `address-picker` dice
"estás sobre **Camino del Perú**" (la avenida a 113 m), que es falso. Google (teléfono y link) sí
lo sabe.

A programar:
1. El aviso tiene que mirar primero la **cache propia** (punto aprendido más cercano, sobre todo
   `android_geocoder` / `cadete_gps`) y recién después Nominatim. Mismo criterio que 3i.
2. Si el reverse no trae calle o cae en una sin nombre, **no avisar**. (El aviso no bloquea el
   pedido: el viaje y el precio salen con el pin corregido.)
3. **Lo que sí traba: `aprenderPin` no aprende** si el reverse no confirma la calle tipeada, justo
   en las calles que Nominatim no conoce (las que más hay que aprender): cada pedido a Colombia 4695
   obliga a volver a arrastrar el pin. Aflojar solo en lo confiable:
   - `google_link`: el punto es de Google → aprender aunque Nominatim no confirme.
   - `manual` del admin: aprender si el reverse no trae calle o trae una sin nombre, con **menos
     peso** (lo pisa lo que después aprenda el teléfono de un cadete, `android_geocoder` /
     `cadete_gps`).
   - Pin de cliente en `/pedir`: sin cambios (se aprende al aprobar la solicitud).
4. Opcional, arreglo de raíz y gratis: **editar OpenStreetMap** (nombrar esas calles y cargar
   alturas del barrio). Nominatim toma los cambios en minutos y sirve para todos.

### 3l. ⚠️ Avisos de pedido por WhatsApp (no por SMS) — pedido del dueño, 2026-09-26
Hoy los avisos al cliente salen por **SMS** (`PedidoService`: "aceptado", "finalizado" y
"reenvío" usan `SmsGatewayService`) y **"retirado" no manda nada**. WhatsApp solo se usa para
códigos de verificación, confirmación de solicitudes de `/pedir` y seguimiento de reclamos
(`whatsapp-gateway.md`: "conectar el envío real a los eventos del negocio" sigue pendiente).

A programar:
1. **Un solo aviso por pedido** (decisión del dueño, 2026-09-26): "su cadete está en camino, siga su
   envío acá: {link}" al aceptar el viaje, por **WhatsApp** (`WhatsappGatewayService`). No se avisa
   retirado ni entregado: el link ya lo muestra. **Hecho del lado SMS** (rama `aviso-unico`): texto
   corto sin tildes (1 SMS), "entregado" apagado por defecto (plantilla vacía = no se manda) y todo
   SMS sale sin tildes ni "°" (con tildes cada aviso costaba 3-4 SMS).
2. **SMS NO es respaldo automático**: es un canal que se prende o apaga, **opción solo del
   superadmin** (hoy variable `SMS_ENABLED`; pasarla a Configuración → Sistema). Por ahora apagado.
3. Variar un poco el texto entre envíos y espaciar (riesgo de ban).
3b. SMS con picos: la cadetería trabaja de 8 a 20 con horas pico (15-25 avisos en media hora) y
   Android frena a ~30 SMS cada 30 min por app mostrando un cartel que hay que tocar → al instalar
   el celular gateway (app "SMS Gateway for Android"), subir el límite por adb
   (`settings put global sms_outgoing_check_max_count` / `sms_outgoing_check_interval_ms`).
4. Chips: con ~70 viajes/día son ~210 mensajes/día → **alcanza 1 chip**. El riesgo es el ban, no el
   volumen: chip "calentado" 1-2 semanas antes de usarlo y un **segundo chip de reserva** (sin SMS
   no hay respaldo). Cada chip necesita un celular para activarlo y prenderlo cada tanto (WhatsApp
   desvincula los dispositivos si el principal pasa ~2 semanas sin conectarse).

### 3n. "Avisar al cliente" con la app de WhatsApp — hecho el 2026-09-26 (ramas `aviso-whatsapp-web`)
Solución de ahora (decisión del dueño), mientras no se use el gateway ni SMS: en cada pedido en curso
(tabla y kanban del dashboard) el botón **"📲 Avisar al cliente"** abre la **app de escritorio de
WhatsApp** (`whatsapp://send`) en el chat del cliente con el aviso escrito; el admin solo aprieta
Enter. **El clic cuenta como avisado** (`pedido.cliente_avisado_en`, "✓ Avisado 14:32"); se puede
volver a tocar para reenviar. Texto en Configuración (`whatsapp_template_en_camino`, con `{marca}`,
`{cadete}`, `{numero}`, `{link}`); teléfono normalizado a 549 + área + número (saca 0 y 15).
**No se usa WhatsApp Web** a propósito: no deja abrir un chat sin recargar la pestaña y desconecta la
otra pestaña donde el admin chatea con los clientes ("Usar aquí"). La app y WhatsApp Web son
dispositivos vinculados distintos: conviven con la misma cuenta.
Falta: editar la plantilla desde el panel (hoy solo por la base) y probarlo con la app instalada.

### 3m. Superadmin — hecho el 2026-09-26 (rama `superadmin`, backend + panel)
- Rol **Superadmin** con el permiso nuevo **"Sistema"**; el Admin tiene todo menos eso. El usuario
  del admin inicial (`ADMIN_USER`) pasa a ser superadmin (en una base nueva y en la existente).
- Solo el superadmin ve y cambia (`ConfiguracionSistema`): Cloudinary, URLs de servidores, "No
  borrar" / días de Google / link vence, frecuencia de ubicación y mapeo de calles, aprendizaje de
  direcciones, versión mínima de la app, retención de fotos, límites anti-abuso, claves web push,
  número de pedido y las **API keys del sistema**. El backend responde 403 aunque se arme el pedido
  a mano; al admin ni le llegan esos valores.
- **API keys**: dos listas por proveedor. Las del sistema (superadmin) se usan primero; las de la
  cadetería (`*_cliente`) las agrega el admin pero **no las puede quitar** (403); el superadmin
  borra las dos. Una key **inválida (401)** ahora se saltea y se ve en rojo en el semáforo (antes se
  seguía usando y fallaba en cada consulta). LocationIQ se sumó al semáforo.
- Un admin no puede darle el permiso "Sistema" a un rol, asignar el rol Superadmin, ni editar,
  bloquear o resetear a un superadmin; tampoco los ve en Roles / Usuarios.
- Falta: el SMS on/off como opción de sistema (3l) y, a futuro con varias cadeterías, que el
  superadmin sea de la plataforma y no de cada base.

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

### 5d. Ideas analizadas el 2026-09-26 (sin código todavía)

**App o avisos para el dueño.** Si el dueño sale y deja el panel a alguien sin todos los permisos,
hoy no se entera de nada. Orden propuesto:
1. **WhatsApp al dueño** con el gateway que ya existe (~1 día): problema con la entrega, "sigue el
   problema", solicitud de cadete nueva, pedido sin asignar hace más de X min, chip baneado.
   Con **escalamiento**: primero el panel; si nadie toca "Visto" en N min, recién le llega al dueño.
   Configurable por persona (qué avisos y horario de silencio) y respetando los permisos.
   Las demoras de retiro/entrega solo si escalan (se cierran solas al retirar/entregar).
2. Si hace falta actuar desde el celular: **versión "dueño" de la APK del cadete** (mismo login,
   push y conexión): lista de avisos, reclamos (ver/llamar/cerrar), solicitudes de cadetes
   (aprobar/rechazar con fotos), pedidos urgentes sin asignar, resumen del día. ~1 semana; necesita
   Firebase.
3. Que se caiga el servidor no lo puede avisar el propio servidor: monitor externo gratis que
   mande WhatsApp al dueño.

**Usar la ubicación del cadete sin pedidos.** Hoy el GPS de la APK ya corre mientras está
disponible (con o sin pedido); se guarda la última posición y el mapeo de calles la usa cada
`mapeo_calles_cadetes_intervalo_seg`, pero el recorrido (`pedido_ubicacion`) solo con un pedido
EN_CURSO. Propuesta, en orden:
1. Que **Retirado y Entregado alimenten la cache de direcciones** (`retiro_lat/lng`,
   `entrega_lat/lng` son la puerta real confirmada): la mejor fuente y casi gratis. Verificar si
   hoy ya lo hace.
2. Guardar recorridos de cadetes libres **filtrados** (solo si se movió >50 m, buena precisión,
   retención 30 días) para **tiempos de viaje reales por zona y horario** y dónde esperan los
   cadetes — no para aprender direcciones: un punto en movimiento (modo balanceado, error de
   decenas de metros, en la calle y no en la puerta) enseña alturas equivocadas.
3. Volumen: un punto cada 45 s son ~1.000/cadete/día (70 cadetes ≈ 2 M filas/mes). Avisar al
   cadete por escrito al darlo de alta; nunca trackear desconectado (como hoy).

**Aviso "llegaste al origen / destino" en la APK** → **hecho el 2026-09-26**, ver "Hecho el
2026-09-26 (madrugada)". Queda como idea: que la app le avise al backend "llegó al origen a las
HH:MM" (tiempo de espera en cada comercio, "el cadete llegó" en el seguimiento del cliente,
detectar quien marca Retirado lejos del origen).

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
- Con `DEMO_ENABLED=true`, en el primer minuto después de arrancar el job `avisosDemora` puede
  chocar (deadlock) con el cargador de la demo que borra y vuelve a crear los pedidos. Se reintenta
  solo al minuto; en producción no hay demo. Ya pasaba antes.
- La ficha del cadete todavía no muestra la constancia de mayor de edad (sí la revisión de la
  solicitud); el dato está guardado en `cadete.mayor_edad_declarada_en/por`.
- La vista **Kanban** del dashboard todavía no muestra los colores de reclamos ni de sin asignar.
