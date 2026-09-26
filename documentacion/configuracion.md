# Configuración — variables y de dónde sacarlas

Guía de todo lo que hay que configurar para levantar una instalación de Cadetería de punta
a punta: qué se define una sola vez al desplegar el servidor (variables de entorno) y qué se
carga después desde el panel admin (pantalla **Configuración**), incluyendo dónde crear cuenta
y sacar la API key de cada servicio externo.

> Para subir a producción, usar el checklist de [`despliegue.md`](./despliegue.md).

## Checklist rápido

Para una instalación nueva, en este orden:

1. **Base de datos y credenciales de admin** — variables de servidor (sección de abajo), antes
   de arrancar el backend.
2. **Cloudinary** (subida de fotos) — sin esto, un cadete no puede finalizar un pedido desde la
   app (exige foto de entrega).
3. **Zonas con tarifa sugerida, o Tarifas por km** (panel → Configuración) — si no, "Nuevo
   pedido" no sugiere ningún precio.
4. **Rutas**: OSRM ya funciona solo, sin cargar nada. GraphHopper/OpenRouteService son
   opcionales, solo mejoran la precisión de la distancia real.
5. **Código de verificación de `/pedir`** (desde 2026-09-24): sale por **WhatsApp** si el
   gateway está conectado, si no por **SMS**, y si no hay ninguno de los dos la solicitud entra
   igual marcada **"sin verificar"** para que el admin valide el teléfono a mano (bandeja de
   Pedidos web). Ya no queda inutilizable sin SMS, pero conviene tener al menos uno de los dos.
6. Opcional: WhatsApp gateway, Firebase (push), SMTP de mail — estos sí se degradan solos
   si se dejan sin configurar, no rompen nada.

## Variables de servidor (`.env` / `application.yml`)

Se definen una sola vez al desplegar el backend (variables de entorno, o copiando
`src/main/resources/application-local.yml.example` a `application-local.yml`, gitignoreado) —
**no se editan desde el panel**.

| Variable | Para qué | Default |
| --- | --- | --- |
| `DB_USER` / `DB_PASSWORD` | Usuario/contraseña de MySQL | `root` / `root` |
| `JWT_SECRET` | Firma de los tokens de login (≥32 caracteres) | valor de desarrollo — **cambiar en producción** |
| `JWT_EXPIRATION_MINUTES` | Duración de la sesión | 720 (12 hs) |
| `ADMIN_USER` / `ADMIN_PASSWORD` | Usuario/contraseña del admin inicial (solo si la tabla `admin` está vacía) | `admin` / `cambiar123` |
| `CORS_ORIGINS` | URL del front permitida a llamar al backend | `http://localhost:4200` |
| `SEED_ENABLED` | Si carga los datos base (estados, tipos de vehículo, admin) la primera vez | `true` |
| `DEMO_ENABLED` | Si siembra pedidos/cadetes de ejemplo para mostrar el panel — **apagar en producción** | `true` |
| `FRONT_BASE_URL` | URL pública del front, para armar los links de seguimiento que se mandan por SMS | `http://localhost:4200` |
| `SERVER_PORT` | Puerto del backend | 8080 |
| `WHATSAPP_GATEWAY_TOKEN` | Token que valida al gateway de WhatsApp (Node/Baileys) al conectarse por WebSocket | valor de desarrollo — cambiar en producción |
| `FCM_CREDENTIALS_PATH` | Ruta al JSON de la cuenta de servicio de Firebase, para push a la app del cadete | vacío = push deshabilitado |
| `MAIL_HOST` / `MAIL_PORT` / `MAIL_USERNAME` / `MAIL_PASSWORD` / `MAIL_FROM` | SMTP para los mails a cadetes: alta (usuario y contraseña), pedido de corrección y rechazo de la solicitud | vacío = deshabilitado, no rompe nada (el panel muestra la contraseña y el link para pasarlos a mano) |
| `CLOUDINARY_API_KEY` / `CLOUDINARY_API_SECRET` | Borrar fotos de verdad al purgar datos viejos | vacío = solo se limpia la referencia en la base |
| `GEOAPIFY_KEY` / `LOCATIONIQ_KEY` | Key "legado" del buscador de direcciones si no hay ninguna cargada en el panel | ⚠️ el default trae keys reales commiteadas — ver `despliegue.md` |
| `SMS_ENABLED` / `SMS_GATEWAY_URL` / `SMS_GATEWAY_USER` / `SMS_GATEWAY_PASSWORD` | Gateway de SMS (proyecto android-sms-gateway) | deshabilitado por defecto |
| `VERIFICACION_TRANSPORTE` | Por dónde sale el código de `/pedir`: `AUTO` (WhatsApp → SMS → sin verificar), `WHATSAPP` o `SMS` | `AUTO` |
| `WHATSAPP_MODO_SIMULADO` | Solo desarrollo: los códigos no se mandan, quedan en "Mensajes enviados" del panel de WhatsApp con chip `SIMULADO` — **apagar en producción** (se ve en Salud del sistema) | `false` |
| `NOMINATIM_URL` / `ORS_URL` | URLs de geocodificación/ruteo — genéricas, casi nunca hace falta tocarlas | ver `application.yml` |

## Panel → Configuración: pedidos, asignación y avisos

**Pedidos y ubicación**
- Tiempo límite para aceptar un pedido (seg) — cuánto tiene el cadete para responder una
  oferta antes de que expire.
- Frecuencia de envío de ubicación (seg) — cada cuánto manda su GPS la app del cadete.
- Asignación automática — apagada por defecto (asignar es 100% manual, botón "Asignar" del
  dashboard).

**Reglas de asignación**
- Máx. viajes sin terminar por cadete: tope que usa la asignación automática/sugerida, sin
  importar el tope propio del cadete (default 1).
- Rechazos antes de excluir al cadete de ese pedido: a los N rechazos de un mismo pedido
  puntual, deja de ofrecérselo (default 3).
- Reintentar con cualquiera igual pasados (minutos): si el pedido lleva tanto tiempo sin poder
  asignarse, ignora el límite de rechazos (default 30).
- Priorizar por ranking de aceptación: apagado por defecto; prendido, entre cadetes libres se
  prioriza al que históricamente rechaza menos, en vez de FIFO puro.

**Avisos de demora al cadete**: umbrales en minutos para recordatorios automáticos (no retiró /
no finalizó / no manda ubicación estando en curso) — solo generan avisos, no bloquean nada.

## Panel → Configuración: tarifas y cotización automática

Sugiere un precio al cargar un pedido nuevo (panel o página pública "/pedir"), siempre
editable a mano.

- **Método de cotización**: Automático (zona si tiene precio, si no por km) / Siempre por zona
  / Siempre por km.
- **Monto mínimo del viaje ($)** y **ese mínimo cubre hasta (km)**: tarifa plana para los
  primeros km.
- **Precio por km adicional ($)**: se cobra solo por los km que superan ese mínimo. Ejemplo:
  mínimo $2000 hasta 2 km, $150/km → un viaje de 5 km cobra 2000 + 3×150 = $2450.
- **Recargo cada ($ de dinero declarado)** y **monto del recargo ($)**: por cada tramo completo
  de dinero/valores que declara el cliente, se suma un recargo fijo (riesgo del cadete).
  Ejemplo: cada $10.000 suma $100 → declarar $25.000 suma $200 (tramo incompleto no cuenta).
- El precio de una **Zona** (pantalla "Zonas", campo "tarifa sugerida") siempre tiene prioridad
  sobre el cálculo por km, salvo que el método esté forzado a "Siempre por km".

## Panel → Configuración: rutas (distancia real por calle)

Para que "precio por km" cotice con distancia real (no en línea recta), se prueban en este
orden:

1. **OSRM** — gratis, sin cuenta ni API key, siempre activo. No hay nada que cargar.
2. **GraphHopper** — gratis con key propia (500 consultas/día).
3. **OpenRouteService** — gratis con key propia (2500 consultas/día); también se usa para la
   ruta sugerida al cadete al aceptar un viaje.

Si ninguna responde, se usa distancia en línea recta como respaldo (nunca rompe el flujo de
cargar un pedido).

### Cómo sacar la key de GraphHopper

1. Crear una cuenta gratis en [graphhopper.com](https://www.graphhopper.com/).
2. En el dashboard, generar una API key del plan gratuito.
3. Pegarla en Configuración → "Rutas — distancia real por calle" → "GraphHopper — API key".

### Cómo sacar la key de OpenRouteService
1. Registrarse en [openrouteservice.org](https://openrouteservice.org/).
2. **Verificar el mail** — sin este paso el dashboard no deja ver la key (pantalla "Verify
   Account"). Si no llega el mail, hay un botón para reenviarlo.
3. Entrar al dashboard (`openrouteservice.org/dev/#/home`) y generar un token del plan
   gratuito.
4. La key tiene forma de texto largo en base64 (empieza algo así como `eyJvcmc...`).
5. Pegarla en "OpenRouteService — API key". Dejar el campo de URL en blanco (usa
   `https://api.heigit.org/openrouteservice` por default) salvo que la documentación de la
   cuenta muestre otro host distinto.

## Panel → Configuración: Cloudinary (subida de imágenes)

Necesario para que la app del cadete pueda subir la foto de entrega — sin esto, no se puede
finalizar un pedido desde la app (exige foto + nombre de quién recibió).

1. Crear cuenta gratis en [cloudinary.com](https://cloudinary.com/).
2. En el dashboard, copiar el **Cloud name** (aparece arriba de todo, apenas entrás).
3. Crear un **upload preset "unsigned"**: Settings → Upload → "Upload presets" → "Add upload
   preset" → Signing Mode: **Unsigned** → guardar y copiar su nombre.
4. Cargar ambos valores en Configuración → "Cloudinary" → "Cloud name" y "Upload preset
   (unsigned)". No son datos secretos, se usan directo desde el navegador para subir las
   fotos.

## Panel → Configuración: resto de secciones

- **App de cadetes**: versión mínima requerida (bloquea logins con APK vieja, ya que se
  distribuye por Bluetooth sin Play Store), exigir firma digital del receptor, exigir
  documentación completa para activarse.
- **Seguridad de inicio de sesión**: intentos fallidos antes de bloquear, minutos de bloqueo
  (aplica a admins y cadetes).
- **Cobro a cadetes**: cuota semanal, comisión %, umbral de alerta de crédito bajo (según el
  modelo de cobro que elija cada cadete en su ficha: Semanal o Porcentaje).
- **Marca**: nombre de la cadetería (aparece en la página pública de seguimiento) y teléfono
  de soporte (pantalla de Ayuda de la app del cadete).
- **Horario de atención**: solo avisa si se carga un pedido fuera de horario, no bloquea la
  carga.
- **Metas**: meta mensual de facturación, para la barra de progreso en Métricas.
- **Plantillas de SMS**: texto de los SMS al aceptar/finalizar/reenviar, con `{link}` como
  placeholder del link de seguimiento.

## Servicios de servidor opcionales

Estos NO se cargan desde el panel — son variables de servidor (ver tabla más arriba). Salvo
el SMS, todos se degradan solos si quedan sin configurar, no rompen nada:

- **SMS gateway** (`SMS_GATEWAY_URL`/`_USER`/`_PASSWORD`, `SMS_ENABLED`): usa el proyecto
  android-sms-gateway corriendo en un celular con chip. **Es el único de esta lista que no
  es realmente opcional**: `VerificacionTelefonoService` lo usa para el código de 6 dígitos
  que exige la página pública `/pedir`, y `SmsGatewayService` trata "deshabilitado" como
  envío exitoso (loguea y devuelve `true` a propósito, para no ensuciar las alertas de
  fallos) — o sea que apagado, el cliente nunca recibe el código y no hay forma de cargar
  un pedido desde la web. También se usa para los SMS de confirmación de pedido y los links
  de seguimiento.
- **WhatsApp gateway** (`WHATSAPP_GATEWAY_TOKEN`): gateway propio (Baileys + chip descartable)
  corriendo en una PC, se conecta al backend por WebSocket con este token — ver
  [`whatsapp-gateway.md`](./whatsapp-gateway.md) para instalarlo y emparejar un chip.
- **Firebase / push a la app** (`FCM_CREDENTIALS_PATH`): ruta al JSON de una cuenta de
  servicio de Firebase, se genera desde la consola de Firebase del proyecto de la app de
  cadetes.
- **SMTP de mail** (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`):
  para el mail de alta de un cadete nuevo con usuario/contraseña temporal.

El panel tiene una sección **"Salud del sistema"** (Configuración, al final) que muestra en
vivo cuáles de estos ya están configurados (🟢/🟡), sin tener que ir a revisar cada variable a
mano.
