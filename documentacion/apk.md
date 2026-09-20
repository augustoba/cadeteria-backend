# App de cadetes — cadeteria-apk

App Android nativa (Kotlin + Jetpack Compose) para el cadete: ver pedidos asignados, aceptar/
rechazar, marcar retirado/finalizado (con foto y firma), chat con el admin, ubicación en vivo.
Se distribuye **por fuera de Play Store** (Bluetooth/APK directo), por eso tiene su propia
pantalla para configurar a qué servidor apunta.

> Repo separado (`cadeteria-apk`), documentado acá junto con el resto a pedido.

`applicationId`: `com.cadeteria.cadete` (`.debug` en la variante debug). `minSdk` 24,
`targetSdk` 34.

## Cómo compilar e instalar

```bash
./gradlew installDebug
```

Instala en el dispositivo/emulador conectado por `adb`. Si ya había una versión instalada
firmada con otra clave (ej. compilada en otra PC), la instalación falla con
`INSTALL_FAILED_UPDATE_INCOMPATIBLE` — hay que desinstalar la vieja primero:
`adb uninstall com.cadeteria.cadete.debug`.

## A qué servidor apunta

La URL del backend **no está hardcodeada** — se guarda en el dispositivo (DataStore,
`SessionManager.kt`) y se edita desde la propia app (pantalla "Servidor",
`ui/servidor/ServerConfigScreen.kt`), sin recompilar. El default de fábrica (antes del primer
cambio) sale de `gradle.properties` → `CADETE_APP_DEFAULT_BASE_URL` (hoy `http://10.0.2.2:8080`,
el alias que usa el **emulador** para llegar al `localhost` de la PC host).

**Probando contra un backend local:**

- **Emulador**: dejar `http://10.0.2.2:8080` (default) — no hace falta tocar nada.
- **Celular físico por USB**: `10.0.2.2` no existe fuera del emulador. Hacer
  `adb reverse tcp:8080 tcp:8080` (se pierde cada vez que se desconecta el cable, hay que
  repetirlo) y configurar en la app `http://localhost:8080` o `http://127.0.0.1:8080`.
- **Celular físico por WiFi**: usar la IP LAN de la PC (`ipconfig`/`ifconfig`), ej.
  `http://192.168.x.x:8080` — el celular y la PC tienen que estar en la misma red.

## Ubicación (GPS)

Usa `FusedLocationProviderClient` (Google Play Services) en `location/LocationTrackingService.kt`
— corre como foreground service mientras el cadete está activo, mandando la posición cada
`frecuencia_ubicacion_seg` (configurable desde el panel).

**Importante para emuladores**: `FusedLocationProviderClient` necesita Google Play Services
instalado. Un AVD creado con una imagen de sistema **"Google APIs"** a secas (o sin ninguna)
**no lo tiene** — la notificación de "compartiendo ubicación" aparece igual (se muestra apenas
arranca el servicio, no cuando realmente hay una posición), pero nunca llega nada al backend.
Se ve en logcat como:

```
W GooglePlayServicesUtil: ... requires the Google Play Store, but it is missing.
W GoogleApiManager: ... SERVICE_INVALID
```

Solución: crear el AVD con una imagen que en Android Studio muestre el ícono de **Play Store**
en la columna "Target" (no alcanza con "Google APIs"). Para simular una posición ahí:
**Extended Controls → Location** (los 3 puntos de la ventana del emulador) → cargar lat/lng →
"Send" — no hace falta ninguna app de fake GPS ni tocar Opciones de desarrollador.

## Estructura

```
app/src/main/java/com/cadeteria/cadete/
├── data/
│   ├── local/       SessionManager (DataStore: URL del backend, token JWT, sesión)
│   ├── remote/      Retrofit/ApiService, DTOs de red
│   └── repository/  Une remote+local para cada pantalla (CadeteRepository, CloudinaryUploader…)
├── location/        Foreground service de ubicación (ver arriba)
├── push/            Notificaciones (FCM + notificaciones locales)
├── realtime/        Cliente WebSocket/STOMP (chat, avisos, cambios de pedido en vivo).
│                    `StompClient.kt` está **escrito a mano** sobre el WebSocket de OkHttp
│                    (CONNECT/SUBSCRIBE/MESSAGE/heart-beat, sin SEND) en vez de usar una
│                    librería STOMP de terceros
├── widget/          Widget de pantalla de inicio (toggle Libre/Ocupado)
└── ui/              Una carpeta por pantalla (home, viaje, chat, historial, perfil, avisos,
                     ayuda, onboarding, login, recuperarpassword, servidor, navigation) +
                     common/theme compartidos
```

## Notas

- Sube fotos (entrega, documentación, vehículo) directo a Cloudinary desde la app
  (`CloudinaryUploader`) — necesita las mismas credenciales que carga el admin en el panel
  (ver `configuracion.md`, sección Cloudinary). Sin eso, **no se puede finalizar un pedido**
  (la app exige foto + nombre de quien recibió).
- "Versión mínima requerida" (Configuración del panel) bloquea el login de una APK vieja — como
  se distribuye por Bluetooth, un cadete puede quedar desactualizado sin darse cuenta.

## Documentación relacionada

- [`configuracion.md`](./configuracion.md) — Cloudinary y demás variables que necesita esta app.
- [`backend.md`](./backend.md) — API que consume la app.
