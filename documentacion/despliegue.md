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
- [ ] Panel → Configuración → **Versión mínima de la app** = la versión del APK que se reparte.
- [ ] `/pedir` (pedidos de clientes): **todavía no se libera** (decisión 2026-09-25). Antes de
      liberarla, revisar los textos del link de Google Maps para el celular (pendientes 3d).

---

## 3. Panel → Configuración (después del primer arranque)

- [ ] **Cloudinary**: cloud name + upload preset unsigned. Sin esto el cadete no puede
      finalizar pedidos (la foto de entrega es obligatoria).
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
- [ ] Revisar **Salud del sistema** (al final de Configuración): todo en 🟢 o 🟡 a propósito.

---

## 4. Cosas "solo para desarrollo o pruebas" que hay que apagar o revisar

| Qué | Dónde | En producción |
| --- | --- | --- |
| Datos de demo (incluye pedidos con reclamos e incidentes de ejemplo) | `DEMO_ENABLED` | `false` |
| Códigos simulados de WhatsApp | `WHATSAPP_MODO_SIMULADO` | `false` |
| Admin inicial por defecto | `ADMIN_USER` / `ADMIN_PASSWORD` | propios |
| "No borrar (solo para pruebas)" de las direcciones de Google | Configuración → Integraciones (`google_cache_pausar_borrado`) | **destildado**; sacar la opción o dejarla solo para superadmin (pendientes 3c) |
| Código de verificación del teléfono en `/pedir` | Configuración (`verificacion_telefono_activa`) | apagado a propósito hasta probarlo con WhatsApp/SMS real (pendientes 3) |
| Keys de Geoapify/LocationIQ commiteadas | `application.yml` | keys propias, dar de baja las viejas |
| `application-local.yml` de esta PC | raíz del backend (gitignoreado) | no se sube; en el servidor, variables de entorno propias |
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
7. Teléfono: ubicación **"Permitir todo el tiempo"**, sacar la app de la **optimización de
   batería** (Xiaomi/Samsung matan el servicio en segundo plano), datos móviles.
8. Panel → Configuración, solo mientras dure la prueba:
   - `frecuencia_ubicacion_seg` más bajo si se quieren más puntos (ej. 15–20).
   - `mapeo_calles_cadetes_intervalo_seg` en 30–60 para que la cache de direcciones aprenda de
     la posición del cadete. **Volverlo a 1200 antes de tener muchos cadetes reales** (límite de
     Nominatim, ver `MapeoCallesCadetesService`).
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
