# Despliegue — checklist para producción

Última actualización: 2026-09-25.

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

---

## 2. Front (panel + `/pedir`) y app del cadete

- [ ] **Panel**: `src/app/core/config/site-config.ts` → `apiBaseUrl` y `wsBaseUrl` con la URL
      del backend (vacíos = usan el proxy de `ng serve`, solo sirve en desarrollo). Build con
      `npx ng build`.
- [ ] **App del cadete**: en `cadeteria-apk/gradle.properties`,
      `CADETE_APP_DEFAULT_BASE_URL` = URL del backend (hoy `http://10.0.2.2:8080`, que es el
      emulador). Compilar con JDK 17.
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
- [ ] **Google (opcional)**: key de Geocoding de **una sola cuenta** (rotar cuentas para
      multiplicar el cupo gratis va contra sus condiciones). En Google Cloud: facturación
      activada, **cuota diaria** en la API y **alerta de presupuesto**. Revisar antes las
      condiciones de Google Maps Platform (pendientes 3b: no permiten mostrar sus resultados sobre
      un mapa que no sea de Google).
- [ ] **Direcciones encontradas con Google**: días de borrado = lo que permitan las condiciones
      de Google en ese momento (hoy 30).
- [ ] **Marca**: nombre de la cadetería y teléfono de soporte.
- [ ] Revisar **Salud del sistema** (al final de Configuración): todo en 🟢 o 🟡 a propósito.

---

## 4. Cosas "solo para desarrollo o pruebas" que hay que apagar o revisar

| Qué | Dónde | En producción |
| --- | --- | --- |
| Datos de demo | `DEMO_ENABLED` | `false` |
| Códigos simulados de WhatsApp | `WHATSAPP_MODO_SIMULADO` | `false` |
| Admin inicial por defecto | `ADMIN_USER` / `ADMIN_PASSWORD` | propios |
| "No borrar (solo para pruebas)" de las direcciones de Google | Configuración → Integraciones (`google_cache_pausar_borrado`) | **destildado**; sacar la opción o dejarla solo para superadmin (pendientes 3c) |
| Código de verificación del teléfono en `/pedir` | Configuración (`verificacion_telefono_activa`) | apagado a propósito hasta probarlo con WhatsApp/SMS real (pendientes 3) |
| Keys de Geoapify/LocationIQ commiteadas | `application.yml` | keys propias, dar de baja las viejas |
| `application-local.yml` de esta PC | raíz del backend (gitignoreado) | no se sube; en el servidor, variables de entorno propias |

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
