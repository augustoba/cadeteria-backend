# Spec — Mejoras visuales y de operación de la app del cadete

Fecha: 2026-09-20. Estado: **implementado el 2026-09-24** (fases A–G; F = el botón "Reportar
al cliente" en la pantalla del viaje, sin rediseñarla entera). Cambios al implementar: en la
oferta, "Zona" se reemplazó por "Dinero" (la asignación ya no usa zona) y la distancia es en
línea recta (la ruta que pide la app va del cadete al destino); el tab de Configuración se
llama "Ajustes" porque "Configuración" no entraba en 4 pestañas.

Alcance: `cadete-app` sobre todo; toca `cadeteria` (backend) en dos puntos concretos, y
`admin-front` (panel) en uno.

Spec hermano: [`spec-antiabuso-pedidos-publicos.md`](./spec-antiabuso-pedidos-publicos.md).
El botón de **reportar al cliente** vive allá (Fase 3) — acá solo se define **dónde y cómo
se ve**, para no rediseñar la pantalla del viaje dos veces.

Referencia visual: `propuestas mejoras visuales/App cadete — Inicio-html.zip` y
`... — Oferta de viaje (countdown real)-html.zip`. Son mockups, no código: los valores
(medidas, colores, tipografías) se replican en componentes Compose, no se copia el markup.

---

## 0. Antes de empezar: dos cosas que ya existen

Van marcadas porque es fácil construir de nuevo algo que ya está:

- **La cuenta regresiva ya existe.** `ContadorAceptacion`
  (`ui/common/Composables.kt:154`) es un anillo circular con el número adentro, que se pone
  rojo a los ≤15 s y **ya vibra** en umbrales. El mockup no la agrega: la agranda y la
  rediseña.
- **La barra de navegación inferior ya existe** (`ui/common/AppScaffold.kt`). Se hizo el
  2026-09-15 reemplazando al menú lateral, con un motivo anotado en el código: *"con el
  celular en una mano arriba de la moto/bici, una barra fija abajo queda al alcance del
  pulgar"*.

## 1. Sistema de diseño

Estos valores son de **toda la app**, no de dos pantallas — van a `ui/theme/`.

### Tipografías

- **Plus Jakarta Sans** (400/500/600/700/800) — texto.
- **Space Grotesk** (600/700) — **solo números**: montos, contadores, estadísticas. Es lo
  que hace que `$3.600` y `45` se lean como datos y no como texto.

Costo a tener en cuenta: suman peso al APK, que se reparte por Bluetooth. Son dos familias
y se pueden recortar los pesos que no se usen.

### Paleta

| Token | Valor | Uso |
| --- | --- | --- |
| Naranja de marca | `#F26B1D` | Acción principal, punto de retiro, tab activo |
| Verde | `#059669` | En curso / libre, punto de entrega, botón Aceptar |
| Rojo | `#DC2626` | Desconectado, urgente, badge de no leídos |
| Ámbar | `#FFFBEB` / `#FDBA74` | Ocupado, precio sobre fondo oscuro |
| Fondo claro | `#FAFAFA` | Fondo de pantalla en tema claro |
| Texto | `#17181A` | Texto principal |
| Muted | `#8A8D93` | Subtítulos, etiquetas |

**Resuelto (2026-09-20) — y no hay nada que cambiar.** El `#F26B1D` del mockup **ya es el
color principal de la app**: `CademOrange` (`Color.kt:8`) es exactamente ese valor, y
`Theme.kt:14`/`:38` lo asignan a `primary` en los dos esquemas. El mockup no proponía un
naranja nuevo, estaba usando el de la marca.

`CademOrangeDark` (`#D65A12`) **no es "el naranja de la app"**: es un tono de contenedor
(`primaryContainer` en oscuro, `onPrimaryContainer` en claro). Los dos conviven a propósito y
los dos se mantienen.

Consecuencia: **cero cambios de color.** El resto de los valores de color del mockup se adaptan
a los tokens que ya existen en `ui/theme/Color.kt` (`Emerald600`, `Amber500`, `Red600`,
`Gray500`) — no se agregan colores nuevos si ya hay uno equivalente.

## 2. Pantalla de Inicio

Referencia: `APK-Home.dc.html`.

### Lo que se mantiene

La card de estado con el icono en círculo, el botón grande + el outline "Ponerme ocupado",
la sección "Asignados y en curso" con sus cards y el botón "Ver viaje", el badge de chat,
el icono de refresh y la barra de navegación inferior.

### Lo que cambia

| # | Cambio |
| --- | --- |
| 1 | **Card de estado con fondo de color** según el estado: verde `#ECFDF5` (LIBRE), ámbar `#FFFBEB` (OCUPADO), rojo suave (DESCONECTADO) |
| 2 | **Anillo pulsante** alrededor del icono de la card — animación `ping`: se expande y se desvanece en loop, 2.4 s, solo cuando está LIBRE |
| 3 | **Fila de 3 estadísticas**: Viajes hoy / Facturado / Conectado |
| 4 | **Card de viaje rediseñada**: pill "En curso" con punto parpadeante, y la ruta con punto naranja (retiro) / verde (entrega) unidos por una línea conectora, en vez de la barra lateral de color actual |
| 5 | **Barra superior nueva**: ☰ + "Hola, \<nombre\> 👋" + fecha, en vez del `TopAppBar` "Dashboard" |
| 6 | El tab pasa de llamarse **"Dashboard" a "Inicio"**, y **Chat pasa a ser un tab** de la barra inferior (hoy es un icono arriba) |
| 7 | Animación de entrada de las cards (fade + desplazamiento) y micro-interacción al tocar botones |

### Las estadísticas: dos son gratis, una necesita backend

**Corrección importante** (verificado contra el código, no contra la memoria): di por hecho
que las tres necesitaban backend y **dos no**.

- **Viajes hoy / Facturado hoy — NO necesita backend nuevo.** El endpoint
  `GET /api/pedidos/me/historial` **ya acepta `desde`/`hasta`** (`yyyy-MM-dd`, ambos
  inclusive, `PedidoCadeteController.java:64-80`) y ya devuelve `cantidadViajes` y
  `montoTotal`. Con `desde = hasta = hoy` la app tiene los dos números sin tocar nada.
  - Costo: devuelve también la lista de pedidos del día, que para estos dos números no hace
    falta. Con ~8-30 viajes diarios es irrelevante; si algún día molesta, se agrega un
    parámetro para pedir solo el resumen.
- **Conectado — sí necesita backend.** Sale de la tabla `cadete_sesion`, que hoy alimenta
  **Métricas del admin**. No hay endpoint que lo exponga al cadete.

Así que de las tres estadísticas, **solo "Conectado" es trabajo nuevo de backend** — y es un
endpoint chico. Las otras dos son una llamada que la app ya sabe hacer.

### La hamburguesa ☰

El mockup la agrega, pero la app la había quitado a propósito al pasar a barra inferior
(sección 0). **Propuesta: que abra las opciones que no son navegación** — y ahí va el botón
**"Salir"** de la sección 5. Así la hamburguesa no contradice la decisión anterior (no
navega, es el menú de la cuenta) y el botón Salir tiene un lugar natural.

## 3. Pantalla de oferta de viaje

Referencia: `APK-Oferta.dc.html`.

### Lo que cambia

| # | Cambio |
| --- | --- |
| 1 | **Pasa a ser una pantalla completa propia**, sin mapa ni barra superior: el cadete ve la oferta y decide, nada más. Hoy es la misma pantalla del viaje con el Aceptar/Rechazar pegado abajo |
| 2 | **Anillo de 200 px** (hoy 56 dp), número a 46 px, con la etiqueta "SEGUNDOS" |
| 3 | **Fila de datos**: Distancia / Zona / Pago |
| 4 | **Aceptar más grande que Rechazar** (proporción ~70/30). Hoy son iguales (50/50) |
| 5 | Nota al pie: *"Si no respondés a tiempo, se le ofrece a otro cadete."* |
| 6 | Usa el **modo oscuro que la app ya tiene** (`Theme.kt`), no un tema propio. Ver abajo |

Todo lo que muestra ya está disponible: `PedidoDto` trae `zona` y `precio`, y la distancia
sale de `state.ruta`, que la app ya pide. **Cero backend nuevo.**

### Sonido además de vibración

Hoy `ContadorAceptacion` llama a `vibrarAlerta()` en umbrales — y un cadete arriba de una
moto no siente la vibración del bolsillo. Se agrega **reproducción de sonido** en los mismos
umbrales.

Criterio: el canal de notificaciones ya usa **tono de llamada** (`TYPE_RINGTONE`) en vez de
chime corto, con un comentario en el código explicando que en varios Motorola el tono de
notificación es *"casi un solo tin"*. El sonido del countdown sigue el mismo criterio.

### Tema oscuro: resuelto — sigue el tema del sistema

**Decidido el 2026-09-20.** La oferta **no** va fija en oscuro: usa el modo oscuro que la app
ya tiene (`Theme.kt`, `isSystemInDarkTheme`), igual que el resto de las pantallas. En un
celular en modo claro la oferta se ve clara.

Consecuencia para la implementación: **el mockup sirve como referencia de layout, jerarquía y
medidas — no de colores.** Los valores `#17181A` / `#232426` del mockup **no se copian**; se
usan los tokens del tema (`MaterialTheme.colorScheme.surface`, `onSurface`, etc.), que ya
resuelven claro y oscuro. El anillo del contador sí mantiene su acento (naranja de marca, rojo
cuando queda poco tiempo) porque ese color es del dato, no del tema.

## 4. Pantalla del viaje

Referencia: el lenguaje visual de las otras dos (misma tipografía, mismos radios, misma
paleta). Se rediseña **junto con** el botón de reportar al cliente del spec de anti-abuso
(Fase 3), para no tocarla dos veces:

- Botón **"Reportar"** entre las acciones del viaje (hoy: Llamar, WhatsApp, Maps, Waze).
  Solo visible con el viaje **EN_CURSO** — antes de aceptar el cliente todavía no es suyo,
  y después de finalizar ya no tiene sentido. Mismo criterio que ya usa
  `puedeContactarCliente`.
- Los 4 tipos del reporte (demoró / no declaró valores / pedido falso / otro) con nota.
- Confirmación visual de que quedó registrado — es un dato que se acumula contra el cliente,
  el cadete tiene que saber que se guardó.

## 5. Recordatorio "no podés recibir pedidos"

### Comportamiento

Cada 30 minutos, mientras el cadete está **DESCONECTADO u OCUPADO**, un aviso recordándole
que en ese estado no le van a llegar pedidos.

- Llega mientras la app esté viva: **abierta o minimizada**.
- **Deja de llegar cuando el cadete toca "Salir"** (decisión 2026-09-20).

### El botón "Salir"

Va en el menú de la hamburguesa (sección 2). Al tocarlo:

1. Cierra la sesión (token + `SessionManager`).
2. Detiene el service de ubicación.
3. Cierra el WebSocket/STOMP.
4. Detiene los recordatorios.
5. Navega al login.

### El límite técnico que hay que aceptar

Cuando el cadete está **DESCONECTADO no hay ningún servicio corriendo** — el service de
ubicación se apaga justo al desconectarse. Sin servicio en primer plano, Android puede matar
el proceso cuando la app queda minimizada. En ese caso el recordatorio se corta solo.

| Estado | ¿Hay servicio? | Recordatorio |
| --- | --- | --- |
| LIBRE | Sí (ubicación) | Confiable |
| OCUPADO | Sí (ubicación) | Confiable |
| DESCONECTADO | No | **Best-effort** — anda hasta que Android mate el proceso |

Se acepta el best-effort: al cadete que está desconectado con la app minimizada hace horas,
que Android la cierre es razonable. La alternativa (un service propio) muestra una
notificación permanente, que es contradictoria con un recordatorio.

**Si el cadete cierra desde Recientes en vez de tocar "Salir"**, el efecto es el mismo que
la opción best-effort — no es un agujero, es el mismo comportamiento.

## 6. Fotos y firma configurables desde el panel

Hoy la validación está **partida en dos**: la firma es configurable y la foto está clavada
en código. `PedidoService.java:968-987`:

```java
// línea 979 — HARDCODEADO: siempre obligatorio
if (exigirComprobante && (sinReceptor || sinFoto))
    throw new BadRequestException("Hace falta el nombre y apellido de quien recibió y la foto de la entrega.");

// línea 982 — CONFIGURABLE: clave firma_receptor_obligatoria, default false
if (exigirComprobante && sinFirma && configuracionService.getBoolean("firma_receptor_obligatoria", false))
    throw new BadRequestException("Hace falta la firma digital de quien recibió.");
```

Se llevan las fotos al mismo mecanismo, con el nombre que ya usa el resto de la tabla
`configuracion` (snake_case):

| Clave | Default | Qué controla | Estado |
| --- | --- | --- | --- |
| `foto_retiro_obligatoria` | `false` | Que "Retirado" exija sacar la foto | **Nueva** |
| `foto_entrega_obligatoria` | `true` | Desclava la línea 979 | **Nueva** |
| `firma_receptor_obligatoria` | `false` | Firma digital del receptor | Ya existe |

Van en el panel: **Configuración → "App de cadetes"**, al lado del toggle de firma que ya
está.

### La app tiene que enterarse antes, no después

Validar solo en el backend hace que el cadete toque "Retirado", espere, y reciba un error —
encima sin señal. El endpoint `GET /api/cadetes/me/configuracion` **ya existe** justo para
esto (hoy devuelve `frecuenciaUbicacionSeg`, `cloudinaryCloudName` y
`cloudinaryUploadPreset`). Se suman los tres flags y la app **pide la foto antes de
intentar**.

El backend los valida igual — nunca confiar en el cliente — pero el cadete nunca ve el error.

### La válvula de escape que hay que conservar

`PendingActionsRepository.kt:58` dice, textual:

> *"si el archivo ya no existe (se limpió la cache), se finaliza igual sin foto antes que
> perder el viaje."*

Eso se mantiene. La regla es **obligatoria al sacarla**, pero si el archivo local
desapareció, no se pierde un viaje por eso.

**Y no rompe el modo offline**: la cola ya guarda el archivo local
(`RetiradoPendiente.fotoPathLocal`) y lo sube a Cloudinary cuando vuelve la señal
(`PendingActionsRepository.kt:51-57`). El cadete sin internet saca la foto igual.

## 7. Orden sugerido

| Fase | Contenido | Depende de |
| --- | --- | --- |
| A | Sistema de diseño (tipografías + paleta en `ui/theme/`) | — |
| B | Pantalla de Inicio (sin las estadísticas) + hamburguesa + Salir | A |
| C | Pantalla de oferta + sonido del countdown | A |
| D | Fotos/firma configurables (panel + backend + app) | — |
| E | Recordatorio cada 30 min | B (necesita el Salir) |
| F | Rediseño de la pantalla del viaje + botón de reportar | A + Fase 3 del spec de anti-abuso |
| G | Estadísticas de Inicio — solo **"Conectado"**; las otras dos salen del historial con `desde=hoy` | Backend: un endpoint chico |

**D se puede hacer primero y sola**: no depende de nada, es la más chica, y arregla una
inconsistencia que ya existe (foto clavada vs. firma configurable).

## 8. Decisiones — resueltas el 2026-09-20

1. **La oferta sigue el tema del sistema**, no va fija en oscuro. Ver sección 3.
2. **La hamburguesa abre el menú de cuenta**: Salir, Ayuda y configuración del servidor. No
   navega entre pantallas — para eso está la barra inferior, y así no se contradice la
   decisión del 2026-09-15. Es además donde vive el botón **"Salir"** de la sección 5.
3. **Naranja de marca: no se toca nada.** Corregido el 2026-09-20: la pregunta original
   planteaba una elección entre dos colores que en realidad **no competían** — `#F26B1D` es
   `CademOrange`, el `primary` de la app en ambos esquemas, y el mockup lo estaba usando.
   `CademOrangeDark` (`#D65A12`) es un tono de contenedor y se mantiene como tal.
4. **Pesos de fuente: se recortan.** Se cargan solo los pesos que las pantallas usan de verdad
   (`Plus Jakarta Sans` no necesita los 5; `Space Grotesk` se usa solo para números). El APK se
   reparte por Bluetooth, así que cada MB cuenta.

## 9. Lo que este spec NO hace

- **No toca el panel admin.** El mockup del naranja unificado en todo el sistema se descartó
  (decisión 3): el panel sigue como está.
- **No toca el backend**, salvo el endpoint chico de "Conectado" para las estadísticas
  (fase G del orden sugerido), que es lo único que necesita datos que hoy no expone.
- **No implementa el botón de reportar al cliente.** Ese vive en
  [`spec-antiabuso-pedidos-publicos.md`](./spec-antiabuso-pedidos-publicos.md) Fase 3; acá solo
  se define que el rediseño de la pantalla del viaje tiene que dejarle lugar.
