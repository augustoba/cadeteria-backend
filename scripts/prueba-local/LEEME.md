# Prueba local en cualquier PC

Para levantar backend, panel y APK con datos de prueba sin cargar nada a mano (2026-09-29).
No usa ninguna base real: arma una base nueva y la carga demo del backend la llena sola.

## Qué hace falta en la PC
- MySQL 8 prendido (por defecto usuario `root`, clave `root`; si es otra, pasala con `-MysqlClave`).
- JDK 21 para el backend (`JAVA_HOME`).
- Node para el panel (`cadeteria-frontend`).
- Para la APK: Android Studio o el SDK, JDK 17 para compilar y un emulador **con servicios de Google**
  (sin ellos la app nunca obtiene ubicación).

## 1. Backend + base
Desde la carpeta `cadeteria-backend`, en PowerShell:

```powershell
.\scripts\prueba-local\levantar.ps1 -Limpia
```

- `-Limpia` borra la base `cadeteria_prueba` y la arma de cero. La carga demo **solo siembra en una
  base vacía**: la primera vez, o si faltan usuarios, usalo.
- Sin `-Limpia` usa lo que ya tenga la base.
- Otras opciones: `-Puerto 8081`, `-Base otro_nombre_prueba`, `-SinCompilar`, `-MysqlClave xxx`.
- Si PowerShell no deja correr scripts: `Set-ExecutionPolicy -Scope Process Bypass` en esa ventana.

Deja WhatsApp simulado (`WHATSAPP_MODO_SIMULADO`) y permite el panel en los puertos 4200 y 4201.

## Usuarios
| Dónde | Usuario | Contraseña |
| --- | --- | --- |
| Panel (superadmin) | `admin` | `cambiar123` |
| APK — Juan Pérez, moto, arranca Libre, tiene los pedidos de ejemplo | `30111222` | `cadete123` |
| APK — Marcos Gómez, bici | `30222333` | `cadete123` |
| APK — Ana Díaz, moto | `30333444` | `cadete123` |

Además siembra la zona "Centro", pedidos de ejemplo en todos los estados, chat, solicitudes,
incidencia, pagos semanales y mensajes de WhatsApp de ejemplo.

## 2. Panel
```powershell
cd ..\cadeteria-frontend
npx ng serve            # http://localhost:4200
```

## 3. APK
La versión debug apunta a `http://10.0.2.2:8080` (el backend de la PC visto desde el emulador). Si
el backend está en otro puerto, en el login está "Cambiar servidor".

Para mandar una posición al emulador: `adb emu geo fix <lng> <lat>` (primero la longitud).

## 4. Escenario de avisos de la calle
Con el backend levantado:

```powershell
.\scripts\prueba-local\escenario-avisos.ps1          # los 3 cadetes Libres cerca y Juan avisa un control
.\scripts\prueba-local\escenario-avisos.ps1 -Votar   # además prueba "¿Sigue ahí?" completo e informa cada paso
```

Cada cadete puede avisar 5 veces por hora: si se corre más seguido da 429.

## Lo que no trae
- **Fotos (Cloudinary)**: no se copian claves. Si una prueba necesita subir fotos, cargá el cloud
  name y el preset en Configuración del panel, o apagá las fotos obligatorias ahí mismo.
- **Direcciones aprendidas**: la base arranca sin la cache de direcciones de la PC principal; el
  buscador consulta afuera la primera vez.
- Ningún dato real (teléfonos, clientes, mensajes): a propósito, este repo está en GitHub.
