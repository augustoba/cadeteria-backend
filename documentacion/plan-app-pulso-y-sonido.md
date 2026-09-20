# Pulso cuando está LIBRE y sonido en el contador — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Que el cadete se entere sin mirar el teléfono: que la pantalla de Inicio pulse cuando está LIBRE, y que el contador para aceptar un viaje suene además de vibrar.

**Architecture:** Dos cambios chicos y autocontenidos en `cadete-app`, ninguno toca el backend ni otros repos. El pulso es una animación Compose alrededor del icono de estado (`HomeScreen.kt`). El sonido se suma al lado de la vibración que ya existe en el contador (`Composables.kt`).

**Tech Stack:** Kotlin + Jetpack Compose (Material 3), minSdk 24. Tests JVM con JUnit 4.

**Spec:** [`spec-app-mejoras-visuales.md`](./spec-app-mejoras-visuales.md) — secciones 2 (Inicio) y 3 (sonido). El mockup de referencia está en `propuestas mejoras visuales/App cadete — Inicio-html.zip`.

## Global Constraints

- **Repo:** `cadete-app`, rama `develop`. No tocar otros repos.
- **Los colores no se cambian.** El `#F26B1D` del mockup ya es `CademOrange` y ya es el `primary` (`Theme.kt:14`/`:38`). Se usan los tokens que ya existen.
- **El repo versiona `app-debug.apk` a propósito.** Si corrés `assembleDebug`, restaurá el APK con `git restore app/build/outputs/apk/debug/app-debug.apk` antes de stagear. **El APK no va en el commit.**
- **No hay `gradlew` documentado que funcione según el README, pero sí funciona**: usá `./gradlew` con `ANDROID_HOME=%LOCALAPPDATA%\Android\Sdk`.
- **JUnit 4**, no 5. El source set `app/src/test/` ya existe (se creó para el helper de imágenes).
- **El pulso es solo para LIBRE.** En OCUPADO y DESCONECTADO no va — el mockup lo muestra solo en verde, y pulsar "estás desconectado" no aporta nada.
- **El sonido usa el stream de ALARMA**, no el de notificación: el cadete va arriba de una moto y el volumen de alarma es el único que garantiza que se escuche. Es el mismo criterio que ya usa `NotificationHelper`.

---

### Task 1: El pulso cuando el cadete está LIBRE

**Files:**
- Modify: `cadete-app/app/src/main/java/com/cadeteria/cadete/ui/home/HomeScreen.kt`

**Interfaces:**
- Consumes: nada.
- Produces: nada (composable privado).

**Contexto — cómo es hoy.** `EstadoCard` (`HomeScreen.kt:259-324`) dibuja un `Box` de 44dp con fondo `color.copy(alpha = 0.14f)` y el icono adentro (`:273-280`). El mockup le agrega, **solo cuando está LIBRE**, un anillo de 2px a 6px de distancia que se expande y se desvanece en loop:

```css
.status-ic .pulse { position: absolute; inset: -6px; border-radius: 999px; border: 2px solid #059669;
                    animation: ping 2.4s cubic-bezier(0,0,.2,1) infinite; }
@keyframes ping { 0% { transform: scale(1); opacity: .8; } 100% { transform: scale(1.35); opacity: 0; } }
```

Esto es **puramente visual y no tiene test unitario** — Compose no se testea sin infraestructura de UI, que este repo no tiene. La verificación es por observación (Step 4).

- [ ] **Step 1: Agregar el composable del pulso**

En `HomeScreen.kt`, junto a `EstadoCard`, agregar un composable privado. Los imports nuevos que hacen falta: `androidx.compose.animation.core.CubicBezierEasing`, `rememberInfiniteTransition`, `animateFloat`, `infiniteRepeatable`, `tween`, `RepeatMode`, `androidx.compose.foundation.border`, `androidx.compose.ui.graphics.graphicsLayer`, `androidx.compose.ui.graphics.Color`.

```kotlin
/**
 * Anillo que se expande y se desvanece en loop alrededor del icono de estado, solo cuando el
 * cadete está LIBRE (auditoría visual 2026-09-20, del mockup del dueño). La idea es que se
 * entienda de un vistazo que está activo y puede recibir viajes, sin tener que leer el texto.
 *
 * Se escala por `graphicsLayer` y no por tamaño: escalar no recompone el layout en cada frame,
 * solo el dibujo — con el celular arriba de la moto eso importa.
 */
@Composable
private fun PulsoLibre(color: Color, modifier: Modifier = Modifier) {
    val transicion = rememberInfiniteTransition(label = "pulsoLibre")
    val progreso by transicion.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2400, easing = CubicBezierEasing(0f, 0f, 0.2f, 1f)),
            repeatMode = RepeatMode.Restart,
        ),
        label = "progresoPulso",
    )
    Box(
        modifier
            .graphicsLayer {
                val escala = 1f + 0.35f * progreso   // 1 → 1.35, como el keyframe del mockup
                scaleX = escala
                scaleY = escala
                alpha = 0.8f * (1f - progreso)       // .8 → 0
            }
            .border(2.dp, color, CircleShape),
    )
}
```

- [ ] **Step 2: Engancharlo en `EstadoCard`, solo para LIBRE**

Reemplazar el `Box` del icono (`:273-280`) por una versión envuelta. El `Box` exterior centra los dos; el pulso va **detrás** del círculo con fondo, y ocupa el mismo tamaño base (44dp) para que el anillo arranque pegado al borde del círculo y crezca hacia afuera.

```kotlin
Box(contentAlignment = Alignment.Center) {
    if (estadoId == EstadoCadete.LIBRE) {
        PulsoLibre(color, Modifier.size(44.dp))
    }
    Box(
        Modifier
            .size(44.dp)
            .background(color.copy(alpha = 0.14f), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(Icons.Filled.PowerSettingsNew, contentDescription = null, tint = color)
    }
}
```

- [ ] **Step 3: Compilar**

Run: `cd cadete-app && ./gradlew :app:compileDebugKotlin`
(Con `ANDROID_HOME` seteado — ver Global Constraints.)
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 4: Verificar que se ve (esto es lo que cierra la tarea)**

**No hay test unitario posible acá.** La verificación es observar el resultado:

1. `./gradlew :app:assembleDebug` e instalar en un dispositivo o emulador.
2. Abrir la app y **mirar la card de estado**.
3. Confirmar las cuatro cosas:
   - Con el cadete **LIBRE**, el anillo pulsa (se expande y se desvanece) en loop.
   - Con el cadete **OCUPADO** o **DESCONECTADO**, **no** hay pulso.
   - El pulso **no** mueve el resto del layout (el texto y los botones de abajo no se corren).
   - Se ve suave, no a los saltos.
4. Si podés, sacá una captura con el cadete LIBRE y otra OCUPADO.

**Si no tenés dispositivo ni emulador a mano, decilo explícitamente en el reporte y marcá la tarea como `DONE_WITH_CONCERNS`** — el código compila pero la animación queda sin ver.

- [ ] **Step 5: Restaurar el APK y commitear**

```bash
cd cadete-app
git restore app/build/outputs/apk/debug/app-debug.apk
git add app/src/main/java/com/cadeteria/cadete/ui/home/HomeScreen.kt
git commit -m "La card de estado pulsa cuando el cadete esta LIBRE"
```

---

### Task 2: Que el contador suene, además de vibrar

**Files:**
- Modify: `cadete-app/app/src/main/java/com/cadeteria/cadete/ui/common/Composables.kt`
- Test: `cadete-app/app/src/test/java/com/cadeteria/cadete/ui/common/AlertasContadorTest.kt`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `internal fun tocaAlertar(restante: Int, yaAvisados: Set<Int>): Boolean`
  - `internal val UMBRALES_ALERTA_SEG: Set<Int>`

**Contexto — cómo es hoy.** `ContadorAceptacion` (`Composables.kt:154`) cuenta los segundos y en `:165-166` vibra en los umbrales:

```kotlin
private val UMBRALES_VIBRACION_SEG = setOf(30, 15, 5)   // :127

if (restante in UMBRALES_VIBRACION_SEG && umbralesYaVibrados.add(restante)) {
    runCatching { vibrarAlerta(context) }
}
```

Dos cambios: **suena** además de vibrar, y **la decisión de alertar se extrae a una función pura** para poder testearla (hoy está enterrada dentro del `LaunchedEffect` y no se puede probar; el source set `app/src/test/` ya existe desde el 2026-09-20).

El criterio de sonido es el que ya usa `NotificationHelper`: **tono de llamada** (`TYPE_RINGTONE`), no el chime corto de notificación — hay un comentario en ese archivo explicando que en varios Motorola el tono de notificación es *"casi un solo tin"*.

- [ ] **Step 1: Escribir el test que falla**

Crear `cadete-app/app/src/test/java/com/cadeteria/cadete/ui/common/AlertasContadorTest.kt`:

```kotlin
package com.cadeteria.cadete.ui.common

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * La decisión de alertar estaba enterrada dentro del LaunchedEffect del contador, donde no se
 * podía probar. Extraída acá, el caso que importa — "que NO vuelva a sonar en el mismo umbral" —
 * queda cubierto: sin ese chequeo el teléfono sonaría en cada tick de cada segundo del umbral.
 */
class AlertasContadorTest {

    @Test
    fun `alerta en los umbrales`() {
        assertTrue(tocaAlertar(30, emptySet()))
        assertTrue(tocaAlertar(15, emptySet()))
        assertTrue(tocaAlertar(5, emptySet()))
    }

    @Test
    fun `no alerta fuera de los umbrales`() {
        assertFalse(tocaAlertar(29, emptySet()))
        assertFalse(tocaAlertar(16, emptySet()))
        assertFalse(tocaAlertar(0, emptySet()))
    }

    @Test
    fun `no vuelve a alertar en un umbral ya avisado`() {
        assertFalse(tocaAlertar(30, setOf(30)))
        assertTrue(tocaAlertar(15, setOf(30)))
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd cadete-app && ./gradlew :app:testDebugUnitTest --tests "*AlertasContadorTest*"`
Expected: FAIL — no compila, `tocaAlertar` no existe.

- [ ] **Step 3: Extraer la decisión y agregar el sonido**

En `Composables.kt`. Renombrar `UMBRALES_VIBRACION_SEG` a `UMBRALES_ALERTA_SEG` (ahora alerta vibra *y* suena) y agregar la función pura. `internal` para que el test la alcance:

```kotlin
/** Segundos restantes en los que se alerta (vibra + suena), una sola vez cada uno. */
internal val UMBRALES_ALERTA_SEG = setOf(30, 15, 5)

/** Si corresponde alertar en este segundo: está en un umbral y no se avisó todavía. */
internal fun tocaAlertar(restante: Int, yaAvisados: Set<Int>): Boolean =
    restante in UMBRALES_ALERTA_SEG && restante !in yaAvisados
```

Agregar el sonido al lado de `vibrarAlerta` (`:129`):

```kotlin
/**
 * Suena además de vibrar: el cadete va arriba de la moto y la vibración del bolsillo no alcanza.
 *
 * Usa el stream de ALARMA a propósito (`USAGE_ALARM`): es el único que suena fuerte aunque el
 * teléfono esté en silencio o con el volumen multimedia bajo. Mismo criterio que NotificationHelper,
 * que por eso usa TYPE_RINGTONE en vez del chime corto de notificación.
 *
 * El tono se corta solo a los 2 segundos: un tono de llamada entero suena 30s, y si el cadete ya
 * aceptó el viaje no tiene por qué seguir sonando.
 */
private fun sonarAlerta(context: Context) {
    runCatching {
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val tono = RingtoneManager.getRingtone(
            context,
            RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE),
        )
        tono?.audioAttributes = attrs
        tono?.play()
        Handler(Looper.getMainLooper()).postDelayed({ runCatching { tono?.stop() } }, 2_000)
    }
}
```

Y en el `LaunchedEffect` (`:165-166`), reemplazar la condición + la llamada:

```kotlin
if (tocaAlertar(restante, umbralesYaVibrados)) {
    umbralesYaVibrados.add(restante)
    runCatching { vibrarAlerta(context) }
    sonarAlerta(context)
}
```

Imports nuevos: `android.media.AudioAttributes`, `android.media.RingtoneManager`, `android.os.Handler`, `android.os.Looper`.

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd cadete-app && ./gradlew :app:testDebugUnitTest --tests "*AlertasContadorTest*"`
Expected: PASS — 3 tests.

- [ ] **Step 5: Verificar que suena (esto es lo que cierra la tarea)**

1. `./gradlew :app:assembleDebug`, instalar, y que le llegue un viaje al cadete.
2. Confirmar: a los 30, 15 y 5 segundos restantes **vibra y suena**, una vez cada uno (no en loop).
3. **Probar con el teléfono en silencio**: tiene que sonar igual (por eso el stream de alarma).
4. Confirmar que **el sonido se corta** a los ~2 segundos y no sigue.

**Si no tenés dispositivo, decilo en el reporte y marcá `DONE_WITH_CONCERNS`.**

- [ ] **Step 6: Restaurar el APK y commitear**

```bash
cd cadete-app
git restore app/build/outputs/apk/debug/app-debug.apk
git add app/src/main/java/com/cadeteria/cadete/ui/common/Composables.kt \
        app/src/test/java/com/cadeteria/cadete/ui/common/AlertasContadorTest.kt
git commit -m "El contador para aceptar suena ademas de vibrar"
```

---

## Lo que este plan NO hace

Es el primer chunk del spec de mejoras visuales, a propósito: las dos cosas chicas y autocontenidas. Lo demás va en planes siguientes, con el código fresco delante:

- **Rediseño de Inicio** (barra superior con el saludo y la fecha, hamburguesa con menú de cuenta, card de viaje con pill y puntos de ruta, Chat como tab, "Dashboard" → "Inicio").
- **La oferta como pantalla completa** con el anillo de 200 px.
- **Las estadísticas de Inicio** (necesita un endpoint chico para "Conectado").
- **El recordatorio cada 30 minutos.**
- **Las tipografías del mockup** (Plus Jakarta Sans + Space Grotesk): necesitan los archivos `.ttf`, que hay que conseguir. **Es una decisión abierta** — ver el spec, sección 1.

## Lo que ya está hecho y NO hay que rehacer

- **El botón "Salir"**: existe y ya hace todo (`CadeteNavGraph.kt:101-115`) — bloquea si hay viajes activos, baja el service de ubicación, cierra el WebSocket, borra la sesión y va al login. Lo único que le falta es frenar los recordatorios, y eso es porque los recordatorios todavía no existen.
- **La cuenta regresiva**: existe (`ContadorAceptacion`), con anillo, rojo a ≤15s y vibración. Acá solo se le agrega el sonido.
- **La barra de navegación inferior**: existe (`AppScaffold`), con 3 tabs.
