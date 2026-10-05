# Despliegue — checklist para producción

Última actualización: 2026-09-26.

Lista de todo lo que hay que configurar, cambiar o apagar al subir a producción, para que no
se olvide nada. **Cada vez que se agrega una variable, una API o una opción "solo para
pruebas", se anota acá.** El detalle de qué hace cada cosa y cómo sacar cada key está en
[`configuracion.md`](./configuracion.md); esto es solo el checklist.

---

## 1. Backend — variables de entorno

Se definen en el servidor (variables de entorno o `application-local.yml` gitignoreado). Los
defaults de `application.yml` son **de desarrollo**.

### Obligatorias (sin esto no se puede salir)

- [ ] `DB_USER` / `DB_PASSWORD` — usuario propio de MySQL, no `root/root`.
- [ ] `JWT_SECRET` — texto aleatorio de 32+ caracteres. El default es público (está en el repo).
- [ ] `ADMIN_USER` / `ADMIN_PASSWORD` — el admin inicial. **No dejar `admin` / `cambiar123`.**
- [ ] `FRONT_BASE_URL` — URL pública del panel (ej. `https://panel.cadem.com.ar`). Se usa en los
      links que se mandan por SMS/WhatsApp/mail (seguimiento, alta y corrección de cadete).
- [ ] `CORS_ORIGINS` — la misma URL del front (si no, el navegador bloquea las llamadas).
- [ ] `DEMO_ENABLED=false` — si no, siembra pedidos y cadetes de ejemplo.
- [ ] `SEED_ENABLED` — `true` solo el primer arranque (crea estados, tipos de vehículo, admin).
- [ ] `WHATSAPP_MODO_SIMULADO=false` — si no, los códigos de verificación nunca salen.
- [ ] `WHATSAPP_GATEWAY_TOKEN` — token propio (el default es público); el mismo va en el gateway.

### Mail (alta, corrección y rechazo de cadetes)

Sin esto **no sale ningún mail**: ni el usuario/contraseña del cadete aprobado, ni el pedido de
corrección, ni el aviso de rechazo. El panel igual muestra la contraseña temporal y el link para
pasarlos a mano, pero no es la idea.

- [ ] `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM`.
- Con **Gmail**: `smtp.gmail.com`, puerto `587`, usuario = la cuenta, contraseña = una
  **contraseña de aplicación** (no la de la cuenta): se crea en
  https://myaccount.google.com/apppasswords y requiere la verificación en 2 pasos activada.
  `MAIL_FROM` = la misma cuenta (Gmail no deja mandar como otra dirección).
- Gmail gratis tiene un tope de ~500 mails/día; de sobra para esto. Los primeros pueden caer en
  spam: pedirle al primer cadete que lo marque como "no es spam".
- Probar: aprobar o pedir corrección de una solicitud de prueba y ver que llegue.

### Opcionales (si faltan, esa función queda apagada sin romper nada)

- [ ] `FCM_CREDENTIALS_PATH` — JSON de Firebase para las notificaciones push a la app.
- [ ] `SMS_ENABLED` / `SMS_GATEWAY_URL` / `SMS_GATEWAY_USER` / `SMS_GATEWAY_PASSWORD`.
- [ ] `VERIFICACION_TRANSPORTE` — `AUTO` (WhatsApp → SMS), `WHATSAPP` o `SMS`.
- [ ] `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` — solo para borrar fotos de verdad al
      purgar datos viejos; sin esto se limpia la referencia en la base y la foto queda en Cloudinary.
- [ ] `GEOAPIFY_KEY` / `LOCATIONIQ_KEY` — ⚠️ **el default de `application.yml` trae keys reales
      commiteadas** (de cuando estaban en el front). En producción cargar keys propias (mejor
      desde el panel, ver §3) y dar de baja las viejas.
- [ ] `TRUSTED_PROXIES` (2026-09-26) — regex con las IPs de los proxies de confianza (nginx, Caddy,
      Cloudflare). El backend toma la IP real del cliente de `X-Forwarded-For` **solo** si el pedido
      llega desde una de esas IPs (sirve para el límite por IP de `/api/publico/**` y el registro de
      accesos del login). Default: IPs privadas y localhost — alcanza si el proxy corre en el mismo
      servidor o en la red interna. **Si hay Cloudflare u otro proxy con IP pública delante, cargar
      sus rangos**; si no, todos los clientes van a compartir la IP del proxy y el límite de 60
      pedidos por minuto los va a cortar a todos juntos. Probar: desde dos redes distintas, que el
      límite corte a una sola.

---

## 2. Front (panel + `/pedir`) y app del cadete

- [ ] **Panel**: `src/app/core/config/site-config.ts` → `apiBaseUrl` y `wsBaseUrl` con la URL
      del backend (vacíos = usan el proxy de `ng serve`, solo sirve en desarrollo). Build con
      `npx ng build`.
- [ ] **App del cadete — servidor fijo por código**: en `cadeteria-apk/gradle.properties`,
      `CADETE_APP_DEFAULT_BASE_URL` = URL del backend de producción (hoy `http://10.0.2.2:8080`,
      que es el emulador).
- [ ] **App del cadete — sacar "Cambiar servidor"**: hoy el login tiene ese botón
      (`LoginScreen` → `ServerConfigScreen`) y la URL que se elige queda guardada en el teléfono
      (`SessionManager`, pisa a la de fábrica). En producción el servidor tiene que venir solo del
      código: mostrar el botón únicamente en el build de debug (`BuildConfig.DEBUG`) y que el build
      de release ignore la URL guardada. **Pendiente de programar** (pendientes 3e).
- [ ] **App del cadete — build de release firmado**: hoy se reparte `app-debug.apk`. Para
      producción: `assembleRelease` con un keystore propio (guardarlo bien: sin él no se pueden
      publicar actualizaciones que se instalen encima), subir `versionCode`/`versionName` en cada
      entrega. Compilar con JDK 17.
- [ ] **App del cadete — recompilar e instalar** en todos los celulares cada vez que cambia (QR
      del pedido, reclamos, aviso de bloqueo, etc.). Subir la "Versión mínima" en Configuración.
- [ ] **App del cadete — HTTPS**: el manifest permite tráfico sin cifrar
      (`usesCleartextTraffic="true"`, necesario para el emulador). Con el backend en `https://`,
      apagarlo en release.
- [x] **App del cadete — permiso de ubicación** (2026-09-26, hecho en la rama `apk-permiso-servicio`): la app lo pide al entrar a la
      pantalla principal pero **no revisa la respuesta** (no hay `checkSelfPermission` de
      ubicación): con el permiso denegado y la ubicación del teléfono prendida, no avisa nada y no
      manda posición. Solo avisa si la ubicación del teléfono está apagada
      (`UbicacionHabilitada.kt`). **Pendiente de programar**: pantalla que bloquea "Sin permiso de
      ubicación no podés recibir viajes" + botón "Abrir ajustes", y aviso si eligió "Solo esta vez".
- [x] **App del cadete — login con DNI** (2026-09-26, hecho en la rama `apk-permiso-servicio`): el alta ya exige usuario = DNI, pero el
      login de la app acepta letras y solo contesta "usuario o contraseña incorrectos". **Pendiente**:
      teclado numérico y aviso "tu usuario es tu DNI, solo números".
- [ ] Panel → Configuración → **Versión mínima de la app** = la versión del APK que se reparte.
- [ ] **PC del admin — app de escritorio de WhatsApp** (2026-09-26): instalarla (Microsoft Store) con
      el número de la cadetería que manda los avisos. La primera vez que se toca "Avisar al cliente",
      Chrome pregunta "¿Abrir WhatsApp?" → tildar **"Permitir siempre"**.
- [ ] **Nombre de la cadetería** (Configuración → `nombre_cadeteria`, ej. "Cadem"): es el `{marca}`
      de los avisos; sin cargarlo sale "Cadetería".
- [ ] **Usuarios del panel** (2026-09-26): `ADMIN_USER` / `ADMIN_PASSWORD` crea al **superadmin**
      (vos: ve lo técnico y las API keys del sistema). Desde el panel → Usuarios, crear un usuario
      con rol **Admin** (el dueño de la cadetería) y otro con rol **Operador**; cada uno recibe una
      contraseña temporal que lo obliga a cambiarla.
- [ ] **Base: nombres de calle unificados** (2026-09-26): con el backend nuevo parado, respaldar
      `cuadra_coords` y `direccion_alias` y correr `documentacion/sql/2026-09-26-alias-curados.sql`
      (idempotente salvo el `ALTER TABLE ... ADD COLUMN muestras`, que falla si ya existe: sacarlo si
      el backend ya arrancó una vez y la creó). Si se migra la base local entera, ya está aplicado.
- [ ] `/pedir` (pedidos de clientes): **todavía no se libera** (decisión 2026-09-25). Antes de
      liberarla, revisar los textos del link de Google Maps para el celular (pendientes 3d).

---

## 3. Panel → Configuración (después del primer arranque)

### Valores sugeridos (2026-09-26)

Los de "Producción" son los que ya trae el código por defecto (`DataSeeder` o el default en el
servicio): si la clave no está en la base, vale ese. "Pruebas" es para salir a la calle con 1-5
teléfonos; **volver a producción antes de tener cadetes reales**.

| Clave (Configuración) | Qué hace | Producción | Pruebas | Por qué ese número |
| --- | --- | --- | --- | --- |
| `frecuencia_ubicacion_seg` | Cada cuánto el teléfono manda su posición | **45** | 10-20 | Batería del cadete y volumen: con pedido en curso cada envío es una fila de `pedido_ubicacion` (~1.000/cadete/día a 45 s). No tiene costo externo. La app lo toma al arrancar el servicio. |
| `mapeo_calles_cadetes_intervalo_seg` | Respaldo con Nominatim para cadetes cuyo teléfono no resolvió la calle | **1200** | 10-60 | Límite de Nominatim: 1 consulta/seg compartida con el buscador. Cuenta segura = cadetes activos / intervalo. Piso duro de 10 s. |
| `aprender_gps_precision_max_m` | Error máximo del GPS para aprender la puerta al marcar Retirado/Entregado | **50** | 50 | Más alto aprende puertas corridas. |
| `aprender_geocoder_precision_max_m` | Error máximo para guardar la calle que resolvió el teléfono andando | **30** | 30 | Con más error el Geocoder devuelve la cuadra de al lado. |
| `reverse_respaldo_max_dia` | Consultas diarias a LocationIQ/Geoapify cuando Nominatim no sabe la altura | **500** | 500 | Sale del cupo gratis del buscador (5.000 y 3.000/día). |
| `google_cache_dias` | Días que se guarda lo de Google (API y teléfono) | **30** | 30 | Condiciones de Google. 0 = no guardar nada. |
| `google_cache_pausar_borrado` | "No borrar (solo para pruebas)" | **false** | true | En producción guardaría datos de Google para siempre. |
| `google_link_vence` | Que también venza lo sacado de un link de Google Maps | **false** | false | Zona gris; ver §2 y la decisión pendiente sobre datos de Google. |
| `here_geocoding_keys` | Keys de HERE Geocoding: segundo intento de "buscar de nuevo", después de Google (2026-10-05) | **key propia de producción** (cuenta HERE, plan Base, pide tarjeta) | vacío o la de prueba | Vacío = no se consulta a HERE. Tope propio de 900 búsquedas/día por key (cupo sin cargo: 30.000/mes, **confirmarlo en la cuenta**). Lo que encuentra vence con `google_cache_dias` (sus condiciones no dejan guardar más de 30 días: **leer el contrato antes de producción**). La key de prueba del 2026-10-05 quedó pegada en un chat: generar otra. |
| `retencion_imagenes_pedido_dias` | Días que se guardan foto de entrega, firma y comprobante | **60** | 60 | Espacio en Cloudinary. **Sin `CLOUDINARY_API_KEY`/`SECRET` solo se borra la referencia en la base, no el archivo** (§1). |
| `retencion_chat_dias` / `retencion_whatsapp_dias` | Días que se guardan chats y WhatsApp | **0** (nunca) | 0 | Decisión del dueño (privacidad, no espacio). |
| `verificacion_telefono_activa` | Código al teléfono en `/pedir` | **false** hasta probar WhatsApp/SMS | false | Ver pendientes 3. |

### Checklist

- [ ] **Cloudinary**: cloud name + upload preset unsigned. Sin esto el cadete no puede
      finalizar pedidos (la foto de entrega es obligatoria).
      ⚠️ **Hoy se usa una cuenta de prueba** (cloud `jitutkbc`, preset `estilospequenos`, de otro
      proyecto). Para producción: **cuenta propia de Cadem**, preset unsigned nuevo y, con la API key
      y el secret de esa cuenta, `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` en el servidor (§1)
      — sin eso la purga de 60 días no borra los archivos y el espacio crece para siempre.
      Consumo estimado (2026-09-26, APK que achica las fotos a 1600 px): ~0,3 MB por foto, ~45 MB/día
      a 70 viajes, ~2,7 GB guardados con 60 días → entra en el plan gratis (~25 créditos/mes).
- [ ] **Tarifas**: mínimo, km cubiertos, precio por km, factor de línea recta, recargo por dinero
      declarado y **recargo por volver al origen** (default 50%).
- [ ] **Direcciones — keys**: Geoapify y LocationIQ (cuentas gratuitas propias).
- [ ] **Aprendizaje de direcciones (2026-09-26)**, claves de Configuración (sin pantalla todavía;
      si no están, valen los defaults):
      - `aprender_gps_precision_max_m` (default **50**): error máximo del GPS del cadete para que
        Retirado/Entregado enseñen la dirección a la cache. 0 = no aprender del cadete.
      - `reverse_respaldo_max_dia` (default **500**): consultas por día a LocationIQ/Geoapify
        cuando Nominatim no sabe la altura de un punto. **Comparten el cupo gratis** con el buscador
        de los clientes (LocationIQ ~5.000/día, Geoapify ~3.000/día): no subirlo tanto que el
        buscador se quede sin cupo. 0 = solo Nominatim.
      - `aprender_geocoder_precision_max_m` (default **30**): error máximo del GPS para guardar la
        calle que resolvió el Geocoder del teléfono mientras el cadete anda. 0 = no guardar.
      - Lo del Geocoder del teléfono (`android_geocoder`, datos de Google) **vence igual que lo de
        Google** (`google_cache_dias`, 30) y lo cubre la misma opción "No borrar (solo para pruebas)".
      - `mapeo_calles_cadetes_intervalo_seg` (default 1200): para pruebas con pocos teléfonos se
        puede bajar a 30–60; volverlo a subir con muchos cadetes.
- [ ] **Google (opcional)**: key de Geocoding de **una sola cuenta** (rotar cuentas para
      multiplicar el cupo gratis va contra sus condiciones). En Google Cloud: facturación
      activada, **cuota diaria** en la API y **alerta de presupuesto**. Revisar antes las
      condiciones de Google Maps Platform (pendientes 3b: no permiten mostrar sus resultados sobre
      un mapa que no sea de Google).
- [ ] **Direcciones encontradas con Google**: días de borrado = lo que permitan las condiciones
      de Google en ese momento (hoy 30).
- [ ] **Marca**: nombre de la cadetería y teléfono de soporte. El nombre sale en los mensajes al
      cliente (`{marca}`) y arriba de la página de seguimiento; sin cargar dice "Cadetería".
- [ ] **Reclamos de clientes**: minutos hasta escribirle al cliente (10), minutos para cerrar sin
      respuesta (10) y **WhatsApp de atención al cliente** (el número al que escribe si sigue el
      problema; puede ser el celular del dueño). Sin gateway de WhatsApp ni SMS, el mensaje de
      seguimiento no sale y el reclamo igual se cierra solo.
- [ ] **Plantillas de SMS / vencimiento del link de seguimiento** (default 2 horas después de
      terminado el pedido): revisar los textos y las horas.
- [ ] **Control en el lugar (2026-09-28)**: Configuración → Pedidos, "Controlar que Retirado y
      Entregado se marquen en el lugar" (`en_lugar_control_activo`, default **prendido**). Si en la
      calle frena de más a los cadetes, apagarlo desde ahí (no hace falta redeploy): igual queda
      anotada la distancia y el GPS falso.
- [ ] **Avisos de la calle (2026-09-28/29)**, claves sin pantalla (si no están, valen los defaults):
      `avisos_calle_radio_m` (**1000**, a quién le llega el aviso), `avisos_calle_duracion_min`
      (**60**), `avisos_calle_extension_min` (**30**, cuánto lo estira un "Sigue") y
      `avisos_calle_ya_no_esta_para_bajar` (**2** cadetes distintos). Tablas nuevas `aviso_calle` y
      `aviso_calle_voto` y columna `aviso_calle.bajado_en`: las crea Hibernate. Explicarles a los
      cadetes para qué es y que queda registrado quién avisa.
- [ ] **Recordatorios al entrar a la app (2026-09-29)**: Configuración → App de cadetes → "Cartel de
      recordatorios al entrar". Revisar título y renglones (hasta 6 de 150 caracteres; sin tocar valen
      los 3 de siempre). Claves `recordatorios_entrar_activo`, `recordatorios_entrar_titulo` y
      `recordatorio_entrar_1` … `_6`. Tabla nueva `recordatorio_confirmacion` (cada "Entendido", la
      crea Hibernate); se ve en la ficha del cadete.
- [ ] **Avisos generales (2026-09-29)**: en la app salen como cartel y cuentan como leídos recién al
      tocar "Entendido". A un cadete que estaba desconectado le salen al abrir la app solo si tienen
      menos de **3 días** y son posteriores a su alta (los demás quedan en su historial).
- [ ] Revisar **Salud del sistema** (al final de Configuración): todo en 🟢 o 🟡 a propósito.

---

## 4. Cosas "solo para desarrollo o pruebas" que hay que apagar o revisar

| Qué | Dónde | En producción |
| --- | --- | --- |
| Datos de demo (incluye pedidos con reclamos e incidentes de ejemplo y los 3 cadetes `30111222`, `30222333`, `30333444` con clave `cadete123`) | `DEMO_ENABLED` | `false`; si la base de producción arrancó con demo, dar de baja esos cadetes |
| Scripts de prueba local (`scripts/prueba-local/`: base nueva con demo y escenario de avisos; cargan solos el Cloudinary de prueba `jitutkbc`) | solo PCs de desarrollo | no se usan en el servidor |
| `cadeteria-apk/app/google-services.json` **commiteado** (2026-09-29, repo público) del proyecto Firebase de prueba `cadeteria-6a388` | repo de la APK | producción usa **otro proyecto Firebase**: reemplazar ese archivo por el de producción al compilar la APK para repartir (no hace falta commitearlo) |

**Credenciales de producción (Cloudinary, Firebase, SMTP, keys, etc.): pedírselas al usuario.**
Ninguna está en los repos (son públicos) ni se pega en chats; las de prueba que figuran acá no se
usan en producción.
| Códigos simulados de WhatsApp | `WHATSAPP_MODO_SIMULADO` | `false` |
| Admin inicial por defecto | `ADMIN_USER` / `ADMIN_PASSWORD` | propios |
| "No borrar (solo para pruebas)" de las direcciones de Google (incluye lo del Geocoder del teléfono, `android_geocoder`) | Configuración → Integraciones (`google_cache_pausar_borrado`) | **destildado**; sacar la opción o dejarla solo para superadmin (pendientes 3c) |
| Código de verificación del teléfono en `/pedir` | Configuración (`verificacion_telefono_activa`) | apagado a propósito hasta probarlo con WhatsApp/SMS real (pendientes 3) |
| Keys de Geoapify/LocationIQ commiteadas | `application.yml` | keys propias, dar de baja las viejas |
| `application-local.yml` de esta PC | raíz del backend (gitignoreado) | no se sube; en el servidor, variables de entorno propias |
| Cartel "✅ encontrada en servicio propio" / nombre del proveedor en los resultados del buscador de direcciones (2026-09-26, para ver si la cache aprendió la calle) | front, `address-picker.component.ts` (bloque `TEMPORAL`) | **sacarlo**: también lo ven los clientes en `/pedir` |
| `FCM_CREDENTIALS_PATH` de esta PC (`C:\Users\august0\secretos\firebase-cadeteria.json`, variable de usuario de Windows) y `google-services.json` en `cadeteria-apk/app/` (proyecto Firebase `cadeteria-6a388`, apps `com.cadeteria.cadete` y `.debug`) | PC de desarrollo | en el servidor, la variable apuntando a su propia copia del JSON (nunca al repo) |
| `TRUSTED_PROXIES='192\.0\.2\.1'` (solo para probar en una PC que un `X-Forwarded-For` inventado no saltea el límite) | variable de entorno | **no** usarla: poner las IPs reales del proxy (§1) |

---

## 5. Cuentas externas a tener creadas

| Servicio | Para qué | Dónde se carga |
| --- | --- | --- |
| Gmail (u otro SMTP) | Mails a cadetes | variables `MAIL_*` |
| Cloudinary | Fotos (entregas, cadetes, documentos) | Panel → Configuración |
| Geoapify / LocationIQ | Buscador de direcciones | Panel → Configuración |
| Google Cloud (opcional) | "Buscar de nuevo" con Google | Panel → Configuración |
| GraphHopper / OpenRouteService (opcional) | Distancia real por calle | Panel → Configuración |
| Firebase (opcional) | Push a la app | variable `FCM_CREDENTIALS_PATH` |
| Chip de WhatsApp / celular con SMS gateway | Códigos y avisos a clientes | gateway + variables |

---

## 6. Probar en la calle sin pagar servidor (2026-09-26)

Para pruebas generales (que se carguen los datos, que se registren lat/lng y el recorrido, salir
con la APK en el teléfono) todavía no hace falta un servidor pago.

### Opciones analizadas

| Opción | Costo | A favor | En contra |
| --- | --- | --- | --- |
| **A. Tu PC + túnel (Cloudflare Tunnel)** ← la recomendada para empezar | $0 | 30 min de armado; se usa el mismo backend y la misma base de siempre; HTTPS, así que Android no pone problemas; acepta WebSocket | la PC tiene que quedar prendida; en el modo rápido la dirección cambia cada vez que se reinicia el túnel (una fija requiere un dominio en Cloudflare, unos pocos US$/año) |
| **B. Máquina virtual Oracle Cloud "Always Free"** (ARM, hasta 4 núcleos / 24 GB) | $0 | anda con la PC apagada; sobra para MySQL + backend + nginx con el panel; es lo más parecido a producción | pide tarjeta para verificar (no cobra) y a veces rechaza la cuenta o no hay lugar en la región; hay que instalar Java 21, MySQL, nginx y el certificado a mano (el repo no tiene Docker todavía) |
| C. Google Cloud e2-micro gratis | $0 | idem B | 1 GB de RAM: muy justo para Spring + MySQL juntos |
| D. Render / Koyeb + base gratis (Aiven, TiDB) + panel en Cloudflare Pages / Vercel | $0 | cero servidores que mantener | **no sirve para esta app**: el plan gratis duerme el backend a los ~15 min sin uso → se frenan los jobs (ofertas que vencen, reclamos, mapeo de calles) y se corta el tiempo real; tarda ~1 min en despertar; 512 MB es justo para Spring |
| ngrok (alternativa a A) | $0 | dirección fija gratis | le muestra una página de aviso al panel en el navegador |

Plan: **A mañana**; si anda bien y se quiere dejar fijo, **B**.

### Paso a paso con el túnel (opción A)

Solo el **backend** necesita el túnel: el panel se mira en la PC (`localhost:4200`) al volver.

1. Instalar: `winget install Cloudflare.cloudflared`.
2. Cambiar la contraseña del admin (`cambiar123`) antes de abrir el túnel: el backend queda
   accesible desde internet mientras esté abierto.
3. Backend prendido en 8080 con la base de siempre.
4. En otra terminal, y **dejarla abierta**: `cloudflared tunnel --url http://localhost:8080` →
   devuelve `https://<algo-al-azar>.trycloudflare.com`.
5. Compilar e instalar la **APK nueva** (el `app-debug.apk` del repo es viejo; compilar con JDK 17,
   ver pendientes §6). En el login → **"Cambiar servidor"** → la dirección del túnel (mismo
   formato que cuando se pone la IP de la PC).
6. PC: Configuración → Energía → **Suspender: nunca** (que se apague la pantalla está bien) y
   pausar Windows Update para ese rato.
7. Teléfono: instalar y configurar como en §7 (permiso de ubicación **"Mientras la app está en
   uso"** — la app no pide "todo el tiempo" ni le hace falta —, batería **sin restricciones**),
   datos móviles.
8. Panel → Configuración, solo mientras dure la prueba:
   - `frecuencia_ubicacion_seg` más bajo si se quieren más puntos (ej. 15–20).
   - `mapeo_calles_cadetes_intervalo_seg`: **no hace falta tocarlo**. Desde el 2026-09-26 el
     teléfono manda calle/altura/localidad en cada ping y eso se guarda directo (`android_geocoder`,
     sin límite, solo con precisión ≤ 30 m). Este intervalo es solo el respaldo por Nominatim
     (1 consulta/seg) para cuando el teléfono no resolvió la calle. Si se baja a 30–60 para la
     prueba, **volverlo a 1200 antes de tener muchos cadetes reales**.
   - Lo que sí suma cuadras es `frecuencia_ubicacion_seg`: a 40 km/h, cada 15 seg ≈ una cuadra y
     media entre ping y ping.
9. **Antes de salir, un pedido asignado a vos y aceptado (EN CURSO)**: el recorrido
   (`pedido_ubicacion`) se guarda solo con un pedido en curso. Sin pedido ("libre") se guarda la
   última posición y lo que aprenda el mapeo de calles, pero no el trayecto. Ideal: un rato con
   pedido y otro sin.
10. En la calle: marcar Retirado y Entregado (con foto y firma) para probar también eso.
12. **Aprendizaje de direcciones**: al marcar Retirado/Entregado parado en la puerta, la dirección
    del pedido queda en la cache (`cuadra_coords`, proveedor `cadete_gps`). Para ver cuánto crece:
    `select proveedor, count(*) from cuadra_coords group by proveedor;`
11. **Aviso de llegada** (APK, 2026-09-26): quedarse ~1 minuto en el origen **sin** marcar
    Retirado → tiene que llegar "Llegaste al retiro… no te olvides de marcar Retirado"; idem en el
    destino. Probar también pasar por al lado sin frenar (no tiene que avisar) y con la pantalla
    apagada.

### Panel desde otra PC (notebook) por un segundo túnel

Para cargar pedidos desde la calle con una notebook. Probado el 2026-09-26 (sin túnel real):
el servidor del panel rechaza direcciones de afuera ("Blocked request. This host is not
allowed") y el backend rechaza el Origin del túnel (CORS). Se resuelve así, sin tocar código:

1. PC: `npm start` en `cadeteria-frontend` (panel en 4200, con el proxy a 8080).
2. Otra terminal abierta: `cloudflared tunnel --url http://localhost:4200 --http-host-header localhost`
   → anotar la dirección (`https://<otra>.trycloudflare.com`).
3. Levantar (o reiniciar) el backend con
   `CORS_ORIGINS=http://localhost:4200,https://<otra>.trycloudflare.com`. Si el túnel del panel
   se reinicia, cambia la dirección: reiniciar el backend con la nueva.
4. `FRONT_BASE_URL=https://<otra>.trycloudflare.com` si también se quieren abrir el link de
   seguimiento y el QR desde otro celular.
5. Notebook: abrir esa dirección y entrar con el admin.

Quedan dos túneles abiertos (8080 para la app, 4200 para el panel) y el backend + `npm start`
corriendo en la PC.

### Al volver: qué mirar

- `pedido_ubicacion` del pedido, ordenado por fecha: ¿hay huecos? (túnel caído o el teléfono
  mató la app), ¿se registró con la pantalla apagada?, ¿cada cuánto llegó cada punto?
- Mapa del panel y **km reales** en Métricas.
- `retiro_lat/lng` y `entrega_lat/lng` del pedido, foto, firma y comprobante.
- Si creció la cache de direcciones con lo que aprendió el mapeo.

### Qué puede fallar

- Si se cierra la terminal del túnel o se reinicia la PC, **la dirección cambia** y la APK queda
  apuntando a la vieja: dejan de llegar puntos desde esa hora.
- El teléfono mata la app en segundo plano: se ve como huecos largos entre puntos.
- El link de seguimiento y el QR **no abren desde otro celular** (el panel no está en el túnel).
  Si se quiere probar eso: un segundo túnel para el panel (`--url http://localhost:4200`) y
  `FRONT_BASE_URL` con esa dirección.
- Sin Firebase configurado no llegan push: los viajes nuevos se ven con la app abierta.
- Precisión: la APK pide ubicación en modo "balanceado" (ahorra batería): error de decenas de
  metros; mirar si el recorrido sale escalonado.
- `TRUSTED_PROXIES` no hace falta: el túnel llega por localhost, que ya es de confianza.

---

## 7. Instalar la app en el celular del cadete (lo que hay que explicarle)

Aprendido instalando en dos celulares (Moto G32 incluido) el 2026-09-26. La app se llama
**"CADEM"** en el celular (no "cadete"); el paquete es `com.cadeteria.cadete` (release) o
`com.cadeteria.cadete.debug` (prueba).

### Paso a paso para el cadete

1. Recibir la APK (WhatsApp como **documento**, Drive o cable) y abrirla. Si el visor de WhatsApp
   falla, ⋮ → Guardar y abrirla desde la app **Archivos**.
2. **"Instalar apps de fuentes desconocidas"** → permitir.
3. **Play Protect** ("Blocked to protect your device" / "Bloqueada para proteger tu dispositivo"):
   tocar **More details / Más detalles → Install anyway / Instalar de todas formas**. **"Got it" /
   "Entendido" cancela la instalación.** Si igual no instala: Play Store → foto de perfil → Play
   Protect → ⚙️ → apagar "Scan apps with Play Protect" / "Analizar apps", instalar y volver a
   prenderlo. En Samsung, además: Ajustes → Seguridad y privacidad → **Bloqueador automático**.
   Pasa con toda APK que no viene del Play Store; no es un problema de la app.
4. Permisos al abrirla por primera vez:
   - Ubicación: **"While using the app" / "Mientras la app está en uso"**. Alcanza: la posición la
     manda un servicio con notificación fija que sigue con la pantalla apagada.
   - **"Only this time" / "Solo esta vez"**: anda, pero al cerrar la app Android borra el permiso y
     vuelve a preguntar.
   - **"Don't allow" / "No permitir" dos veces → Android no vuelve a preguntar nunca** y la app
     queda sin ubicación sin avisar (ver pendiente en §2). Se arregla en Ajustes → Apps → CADEM →
     Permisos → Ubicación.
   - Notificaciones: **Permitir** (sin esto no llegan los viajes nuevos con la app cerrada).
   - Micrófono: lo pide al mandar un audio en el chat.
5. **Batería sin restricciones**, si no el teléfono corta la app en segundo plano (huecos en el
   recorrido): Ajustes → Apps → CADEM → **Batería / App battery usage → Sin restricciones /
   Unrestricted**. En Xiaomi además "Inicio automático" activado; en Samsung sacarla de "Apps en
   suspensión".
6. Ubicación del teléfono prendida (si está apagada la app avisa sola).
7. Login: **usuario = DNI** (solo números) y la contraseña que le da el admin.
8. **Explicarlo en la charla (2026-09-29): mientras tenga la sesión abierta, la app comparte su
   ubicación en cualquier estado, también Desconectado** (sirve para aprender las calles; el panel
   lo ve en gris en el Mapa). Para cortarla: ☰ → **Salir**. No hay aviso dentro de la app: se
   explica en persona.

### "App not installed" / "La app no se instaló"

- **"package conflicts with an existing package"**: ya hay una versión instalada **firmada en otra
  PC** (cada PC firma distinto los builds de prueba) y Android no deja instalar encima. Hay que
  desinstalar la vieja: Ajustes → Apps → buscar **"CADEM"** → Desinstalar.
- **Si "CADEM" no aparece**, está en un perfil escondido:
  - **Motorola: bóveda de Moto Secure ("Vault Profile")** — fue el caso del Moto G32: no se ve en
    Ajustes → Apps del perfil normal.
  - Samsung: **Carpeta segura**. Xiaomi: **Apps duales** / **Segundo espacio**.
  - Perfil de trabajo (pestaña "Trabajo" en el cajón de apps) u otro usuario (Ajustes → Sistema →
    Varios usuarios).
- Último recurso, por cable desde la PC (depuración USB: Ajustes → Acerca del teléfono → 7 toques
  en "Número de compilación"; Ajustes → Sistema → Opciones de desarrollador → Depuración USB):
  ```
  adb shell pm list users                                   # ¿hay otro perfil?
  adb shell dumpsys package com.cadeteria.cadete.debug | grep "User "   # en cuál está instalada
  adb uninstall com.cadeteria.cadete.debug                  # la saca de todos los perfiles
  ```
  Después apagar la depuración USB.
- Para evitar todo esto en producción: **un solo keystore de release** (§2) — con la misma firma
  siempre, las actualizaciones se instalan encima sin desinstalar.
