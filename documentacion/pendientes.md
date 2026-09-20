# Pendientes

Última actualización: 2026-09-20.

> El backlog largo (las 11 rondas de propuestas) vive en `MEJORAS-PROPUESTAS.md`, en la raíz
> del proyecto. **Ese archivo está fuera de cualquier repo git**, así que no viaja con el
> código. Este archivo es el pendiente corto y accionable, versionado acá a propósito.

Todo el trabajo nuevo va en la rama **`develop`** de los 3 repos (`cadeteria`, `admin-front`,
`cadete-app`).

---

## 1. Algoritmo de asignación — enfoque decidido, sin codificar

**El problema.** Cuando un cadete **rechaza** un pedido, la cadena de reasignación se lo
devuelve **en el acto** si él sigue siendo elegible (su contador de rechazos es 1 y el tope es
3). Con un solo cadete libre, el mismo pedido le vuelve 3 veces seguidas, aunque haya otros
pedidos esperando en el dashboard. Y como el cadete no ve los pedidos sin asignar, los otros
no compiten: se quedan esperando.

**Decidido:**

1. **No devolverle el mismo pedido en el acto al que acaba de rechazarlo** — un filtro en la
   cadena de reasignación (`liberarYReasignar` ya recibe el cadete que no aceptó, así que es
   local).
2. **Un cartel en la pantalla de la oferta** explicándole al cadete las reglas: que rechazar
   tiene un límite, y que si deja pasar el tiempo el pedido se le puede volver a ofrecer.
   *(Encaja con el rediseño de la oferta de `spec-app-mejoras-visuales.md` — el mockup ya tiene
   la mitad de ese texto.)*

**Ya existe y NO hay que construir:** pasados `minutos_pedido_urgente_reintentar` (30 por
defecto) el pedido se considera urgente y **el filtro de rechazos se saltea entero**
(`PedidoService:478-484`), así que se le puede volver a ofrecer a todos los que no lo
aceptaron. Es la regla de los 30 minutos, ya implementada y configurable desde el panel.

**Arregla de yapa un segundo bug:** las ofertas **expiradas no cuentan** como rechazo
(`PedidoService:472` filtra solo `RECHAZADO`), así que con pocos cadetes la cadena rebota para
siempre entre los mismos y el pedido nunca llega a `SIN_ASIGNAR`. Esa fue la causa del "sigue
apareciendo como pendiente hasta pasado un buen tiempo".

## 2. El mapa del viaje no muestra el origen

Cuando se le asigna un pedido al cadete, el mapa muestra el destino y dónde está el cadete,
pero **no de dónde retira**.

## 3. Bug sin explicar

Por qué la cadena no le volvió a ofrecer a "Test Cadete" una tercera vez. Se revisaron los
filtros de `buscarCandidato` uno por uno y los pasa todos: activo, LIBRE, MOTO como el pedido,
sin turno fijo, sin topes configurados, sin incidencia grave, SEMANAL habilitado. Juan sí queda
fuera y eso se entiende (2 viajes en curso contra `asignacion_automatica_max_viajes_cadete`,
default 1). **Antes de tocar el punto 1 conviene entender esto**, porque el punto 1 asume que
la cadena corta por el filtro de rechazos y acá cortó por otra cosa.

## 4. Verificación visual pendiente

La animación del pulso **nunca se vio moverse**. Se confirmó que el anillo aparece en LIBRE y
no en OCUPADO (comparando capturas), pero no que se expanda. El intento de medirlo por píxeles
fue inconcluso (la estimación del centro del icono estaba mal). Hay que mirarlo en el teléfono.

## 5. Trabajo empezado sin terminar

- **`plan-app-pulso-y-sonido.md`, Task 2**: que el contador para aceptar **suene** además de
  vibrar (el cadete va en moto y no siente la vibración). La Task 1 (el pulso) está hecha.
- **`spec-app-mejoras-visuales.md`**: sin implementar salvo el pulso. Falta el rediseño de
  Inicio, la oferta como pantalla completa, las estadísticas de Inicio (solo "Conectado"
  necesita backend; las otras dos salen de `/pedidos/me/historial?desde=hoy`), el recordatorio
  cada 30 min, y las tipografías del mockup (**necesitan los `.ttf`**, que hay que conseguir).
- **`spec-antiabuso-pedidos-publicos.md`**: nada implementado.

## 6. Datos sucios conocidos

El seeder demo recrea los pedidos con **ids fijos** (`demo-pedido-01`…`08`) en cada reinicio,
así que quedan **filas huérfanas en `oferta_pedido`** apuntando a esos ids. Al leer el historial
de ofertas mezcla corridas y engaña — se presta a diagnosticar mal un bug, como pasó el
2026-09-20.

## 7. Deudas de mantenimiento anotadas

- `documentacion/backend.md`: el seeder demo crea `jperez`/`mgomez` como usuario, y el login de
  cadete exige que el usuario sea el **DNI** (`^[0-9]{1,8}$`). El login no valida formato así
  que entran igual, pero **el panel los rechaza si los editás**. Los DNIs ya están cargados en
  el seeder — usarlos como username.
- `cadete-app/README.md` dice que hay que regenerar el wrapper de Gradle porque el `.jar` no
  está versionado. **`./gradlew` funciona** (los scripts están versionados y el `.jar` está en
  disco). Solo hace falta setear `ANDROID_HOME`.
- `documentacion/frontend.md` menciona `features/auth/` y `features/layout/` como si fueran
  pantallas: están **vacías y sin referencias** en `app.routes.ts`.
