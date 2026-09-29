# Prueba local en cualquier PC

Para levantar backend, panel y APK con datos de prueba sin cargar nada a mano (2026-09-29), y
probar con el emulador o con un celular de verdad. No usa ninguna base real: arma una base nueva y
la carga demo del backend la llena sola.

## Qué hace falta en la PC
- MySQL 8 prendido (por defecto usuario `root`, clave `root`; si es otra, pasala con `-MysqlClave`).
- JDK 21 para el backend (`JAVA_HOME`).
- Node para el panel (`cadeteria-frontend`).
- Para la APK: Android SDK (`adb`) y JDK 17 para compilar. Emulador **con servicios de Google**
  (sin ellos la app nunca obtiene ubicación) o un celular con depuración USB.
- **Para las notificaciones push**: el archivo de la cuenta de servicio de Firebase (ver abajo).

## 1. Backend + base
Desde la carpeta `cadeteria-backend`, en PowerShell:

```powershell
.\scripts\prueba-local\levantar.ps1 -Limpia
```

- `-Limpia` borra la base `cadeteria_prueba` y la arma de cero. La carga demo **solo siembra en una
  base vacía**: la primera vez, o si faltan usuarios, usalo.
- Sin `-Limpia` usa lo que ya tenga la base.
- Otras opciones: `-Puerto 8081`, `-Base otro_nombre_prueba`, `-SinCompilar`, `-MysqlClave xxx`,
  `-FirebaseJson <ruta>`.
- Si PowerShell no deja correr scripts: `Set-ExecutionPolicy -Scope Process Bypass` en esa ventana.

Qué deja listo:
- WhatsApp simulado y el panel permitido en los puertos 4200 y 4201.
- **Cloudinary (fotos) de prueba** cargado solo en Configuración si la base no tenía uno: cloud
  `jitutkbc`, preset `estilospequenos` (cuenta de otro proyecto, la misma de `despliegue.md`). Sin
  esto el cadete no puede finalizar pedidos: la foto de entrega es obligatoria.
- **Push (Firebase)** si encuentra el archivo de la cuenta de servicio (ver abajo); si no, avisa en
  amarillo y arranca igual.
- Al terminar de arrancar muestra los usuarios y la dirección para el celular.

## Usuarios
| Dónde | Usuario | Contraseña |
| --- | --- | --- |
| Panel (superadmin) | `admin` | `cambiar123` |
| APK — Juan Pérez, moto, arranca Libre, tiene los pedidos de ejemplo | `30111222` | `cadete123` |
| APK — Marcos Gómez, bici | `30222333` | `cadete123` |
| APK — Ana Díaz, moto | `30333444` | `cadete123` |

Además siembra la zona "Centro", pedidos de ejemplo en todos los estados, chat, solicitudes,
incidencia, pagos semanales y mensajes de WhatsApp de ejemplo.

## Firebase (push a la app)
Son dos archivos distintos:

| Archivo | Para qué | Dónde está |
| --- | --- | --- |
| `cadeteria-apk/app/google-services.json` | Que la APK se registre para recibir push | **en el repo** (configuración de cliente, va adentro de cada APK) |
| Cuenta de servicio (`firebase-cadeteria.json`) | Que el backend **mande** los push | **NO está en el repo**: tiene una clave privada y el repo es público |

**La cuenta de servicio hay que pedírsela al usuario** (dueño del proyecto Firebase
`cadeteria-6a388`). Él la copia a mano (pendrive, Drive, etc.) a `%USERPROFILE%\secretos\firebase-cadeteria.json`
de la otra PC, o la deja en otra ruta y la pasa con `-FirebaseJson <ruta>` o con la variable
`FCM_CREDENTIALS_PATH`. Nunca commitearla ni pegarla en un chat.

Sin ella todo anda igual con la app abierta (viajes, chat y avisos llegan por WebSocket); lo que no
llega es "Nuevo viaje" con la app cerrada.

## 2. Panel
```powershell
cd ..\cadeteria-frontend
npx ng serve            # http://localhost:4200
```

## 3. APK
Compilar (JDK 17) e instalar:
```powershell
cd ..\cadeteria-apk
.\gradlew assembleDebug "-Dorg.gradle.java.home=<ruta al JDK 17>"
adb install -r app\build\outputs\apk\debug\app-debug.apk
```
El APK de `cadeteria-apk/distribucion/` es viejo: no usarlo para probar.

### En el emulador
La versión debug apunta a `http://10.0.2.2:8080` (el backend de la PC visto desde el emulador).
Para mandarle una posición: `adb emu geo fix <lng> <lat>` (primero la longitud).

### En un celular de verdad
1. Celular con **depuración USB** (Ajustes → Acerca del teléfono → tocar 7 veces "Número de
   compilación" → Opciones de desarrollador → Depuración USB), enchufado a la PC, y `adb install`
   como arriba. También se puede pasar el `.apk` por WhatsApp/Drive e instalarlo a mano.
2. Conectarlo al backend, una de dos:
   - **Misma WiFi que la PC**: en el login → **"Cambiar servidor"** → la dirección que muestra
     `levantar.ps1` (`http://192.168.x.x:8080`). La primera vez Windows pregunta si deja pasar a
     Java por el firewall: aceptar en **redes privadas**. Si no preguntó, abrir el puerto:
     `New-NetFirewallRule -DisplayName "Cadeteria 8080" -Direction Inbound -Protocol TCP -LocalPort 8080 -Action Allow`
     (PowerShell como administrador).
   - **En la calle con datos móviles**: túnel de Cloudflare, paso a paso en
     `documentacion/despliegue.md` §6 (opción A). En "Cambiar servidor" va la dirección
     `https://….trycloudflare.com`.
3. Permisos: ubicación "Mientras la app está en uso", notificaciones y batería sin restricciones
   (igual que `despliegue.md` §7).

## 4. Escenario de avisos de la calle
Con el backend levantado:

```powershell
.\scripts\prueba-local\escenario-avisos.ps1          # los 3 cadetes Libres cerca y Juan avisa un control
.\scripts\prueba-local\escenario-avisos.ps1 -Votar   # además prueba "¿Sigue ahí?" completo e informa cada paso
.\scripts\prueba-local\escenario-avisos.ps1 -Lat -26.8120 -Lng -65.2890   # el aviso donde vos digas
```

- Por defecto todo pasa en la plaza Independencia.
- **"¿Sigue ahí?" con el celular**: correrlo con `-Lat/-Lng` de un lugar por donde vas a pasar
  (Google Maps: mantener apretado un punto y copiar las coordenadas), entrar en el celular con
  `30222333` o `30333444` (no con Juan, que es el que avisó), ponerse Libre y pasar a menos de
  100 m. Tiene que salir la notificación con **Sigue** / **Ya no está**.
- SIGUE sobre un aviso recién creado no cambia el vencimiento (dura 60 min y SIGUE lo lleva a 30
  desde ahora; nunca acorta).
- Cada cadete puede avisar 5 veces por hora: si se corre más seguido da 429.

## Lo que no trae
- **Direcciones aprendidas**: la base arranca sin la cache de direcciones de la PC principal; el
  buscador consulta afuera la primera vez.
- Ningún dato real (teléfonos, clientes, mensajes): a propósito, este repo es público.
- **Credenciales de producción**: Cloudinary, Firebase y el resto de producción serán otras cuentas
  (`documentacion/despliegue.md`). Para desplegar, **pedírselas al usuario**; nunca van al repo.
