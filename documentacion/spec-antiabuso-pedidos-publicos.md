# Spec — Anti-abuso y reputación en los pedidos públicos

Fecha: 2026-09-20. Estado: **propuesta, pendiente de revisión** — nada de esto está
implementado.

Alcance: 3 repos (`cadeteria` backend, `admin-front` panel, `cadete-app` app) +
`whatsapp-gateway`.

---

## 1. El problema

La página pública `/pedir` deja que cualquiera cargue un pedido sin login. Dos riesgos
distintos, que hasta ahora se estaban tratando como uno solo:

- **Suplantación** — alguien carga un pedido a nombre de un teléfono que no es suyo.
- **Pedido inventado** — el pedido es de verdad de esa persona, pero la dirección no
  existe o el viaje no es real. Le hace perder plata al negocio: un cadete manejando hasta
  una dirección falsa.

La suplantación ya está atacada (ver sección 2), pero **hoy está rota en la práctica**.
Los pedidos inventados no están atacados en absoluto.

## 2. Qué existe ya (importante: no reconstruir)

| Pieza | Dónde | Estado |
| --- | --- | --- |
| Código de 6 dígitos por **SMS** + token de un solo uso que `/pedir` exige | `VerificacionTelefonoService`, `SolicitudPedidoService.crear()` (`verificacionToken`, `@NotBlank`) | Funciona, **pero ver el agujero abajo** |
| Rate limit por IP en `/api/publico/**` (60 req/60s) | `RateLimitFilter` | Funciona |
| Ficha de cliente con **"cliente problemático"** + notas | `Cliente.problematico` / `notasProblematico`, `ClienteDtos.ClienteRequest/Response`, `ClienteService`, `ClienteAdminController` | Funciona, end-to-end |
| Aviso ámbar al cargar un pedido con un teléfono marcado | `nuevo-pedido.component.ts:85-89` (consume `ClienteAvisoResponse`) | Funciona — **solo en la carga manual del admin** |
| Gateway de WhatsApp con envío de texto libre | `WhatsappGatewayService.enviar(telefono, texto, pedidoId)` | Construido, **nunca probado en vivo** (falta el primer chip) |
| Tickets/reclamos | `Incidencia` (título, estado, prioridad, pedido, cadete) | Funciona, es del admin |

### El agujero

`SmsGatewayService.java:67-69`: si `SMS_ENABLED=false` (el **default**), loguea "SMS gateway
deshabilitado" y **devuelve `true`** — a propósito, para que "deshabilitado" no cuente como
fallo y no dispare las alertas de gateway caído. Consecuencia: el cliente nunca recibe el
código, la página no muestra ningún error, y **nadie puede cargar un pedido desde la web**.

Es el mismo agujero que tendría verificar por WhatsApp si el gateway está apagado: cambiar
de transporte no alcanza, hay que **degradar** (sección 6).

## 3. Principios

1. **Por teléfono, no por dirección.** La dirección la escribe el cliente a mano, así que
   "la misma dirección" puede estar tipeada de cinco formas. Matchear por texto no engancha
   nunca, y matchear por coordenadas redondeadas es frágil. El teléfono es un identificador
   limpio y normalizado (`TelefonoUtils.normalizar`).
2. **La verificación es lo que hace que la reputación sirva.** Antes de verificar el
   teléfono, cualquiera inventa un número y una lista negra no engancha nada. Después de
   verificado, un teléfono es una cuenta real de WhatsApp. **Por eso la Fase 2 es
   prerequisito de valor de la Fase 3**, aunque se implementen en otro orden.
3. **Nada bloquea automático — todo avisa.** El sistema acumula y muestra; el admin decide.
   Coherente con cómo ya funciona `problematico`.
4. **Nunca se pierde una venta.** Si no hay forma de verificar, el pedido entra igual
   marcado como "sin verificar" y lo valida un humano. Es el mismo criterio de degradación
   que usan `RutaService` y todos los servicios opcionales.
5. **Extender lo que existe**, no crear un sistema paralelo de reputación.

## 4. Fases

### Fase 1 — La alerta en la bandeja de Pedidos web

**Se puede implementar y probar hoy. No depende del gateway.** Es la que haría primero.

La bandeja (`features/pedir/solicitudes-pedido.component.ts`) hoy no muestra nada cuando el
teléfono de la solicitud está marcado como problemático. El dato ya está en la solicitud
(`SolicitudPedido.clienteTelefono`) y el endpoint ya existe (`ClienteAvisoResponse`).

- Al listar las solicitudes, resolver el `Cliente` por teléfono y mostrar el mismo cartel
  ámbar que ya usa `nuevo-pedido.component.ts`.
- Los teléfonos sin ficha de cliente se muestran sin aviso (no es un error).
- Evitar el N+1: resolver los avisos en el backend y devolverlos dentro de
  `SolicitudPedidoResponse` (campo `avisoCliente`), no una request por solicitud.

### Fase 2 — Verificación del teléfono por WhatsApp

Reemplaza el transporte SMS por WhatsApp, reusando **toda** la máquina existente
(`VerificacionTelefonoService`: código de 6 dígitos, 10 min, 5 intentos, 3 pedidos cada 10
min por teléfono, token de un solo uso). Lo que cambia:

- **Transporte**: `enviarCodigo` manda por `WhatsappGatewayService` en vez de
  `SmsGatewayService`. Nuevo parámetro de config `app.verificacion.transporte`
  (`WHATSAPP` | `SMS` | `AUTO`, default `AUTO`).
- **Validación permanente**: tabla nueva `telefono_validado` (sección 5) — una vez que un
  teléfono pasó el código, no se le vuelve a pedir. Es la lista blanca que se construye
  sola: resuelve que al principio sean todos nuevos. **Va en tabla propia y no como flag en
  `Cliente`** porque la mayoría de los clientes reales no tiene ficha de cliente cargada —
  la ficha es algo que el admin mantiene a mano para casos especiales (tarifa, cuenta
  corriente), no el padrón de clientes.
- **Degradación** (sección 6): WhatsApp → SMS → sin verificar.

`/pedir` deja de depender de `SMS_ENABLED`, que es lo que hoy lo tiene roto.

### Fase 3 — El reporte del cadete desde la app

El cadete, desde la pantalla del viaje, puede reportar algo sobre el cliente. Botón nuevo
en `ViajeScreen.kt`, junto a los que ya existen (llamar, WhatsApp, Maps, Waze):

| Tipo | Para qué |
| --- | --- |
| `DEMORO` | El cliente tardó en atender / entregar el paquete |
| `NO_DECLARO_VALORES` | Transportaba valores que no declaró |
| `PEDIDO_FALSO` | La dirección no existe o el pedido no era real |
| `OTRO` | Con nota libre |

- Tabla nueva **`reporte_cliente`**: `id`, `telefono`, `tipo`, `pedidoId`, `pedidoNumero`,
  `cadeteId`, `cadeteNombre`, `nota`, `creadoEn`.
- Endpoint `POST /api/pedidos/me/{id}/reporte` (rol CADETE) — el cadete solo puede
  reportar sobre un pedido propio.
- El reporte **no** bloquea ni marca nada por sí solo: solo acumula.

> **Por qué tabla nueva y no `Incidencia`**: `Incidencia` es un ticket con estado,
> prioridad y flujo de cierre (`ABIERTA`/cerrada, `cerradaPorUsername`) que el admin
> gestiona. Un reporte de reputación no tiene ciclo de vida — es un evento inmutable que se
> acumula por teléfono. Meterlos en la misma tabla ensucia las dos cosas.

### Fase 4 — El admin ve y decide

- **Aviso enriquecido**: donde hoy dice *"Este teléfono está marcado como cliente
  problemático: <nota>"*, pasa a mostrar el historial: *"Este teléfono tiene 3 reportes:
  2 de demora, 1 de valores no declarados (último: 2026-09-18)"*. Aplica tanto a la carga
  manual como a la bandeja web (Fase 1).
- **Marcar como fraudulento** — acción nueva sobre una solicitud o un pedido. Es lo que
  alimenta la lista negra; sin esta acción, la lista negra nunca se llena. Prende
  `Cliente.problematico` con la nota correspondiente (reusa lo que existe).
- **Bloquear o no** — el admin decide. Bloquear = `problematico` (avisa, no impide).
- **Mandarle WhatsApp al cliente** — botón en la solicitud que manda la plantilla con los
  detalles: *"Realizaste un pedido desde <origen> hasta <destino>. ¿Confirmás el envío?"*.
  Usa `WhatsappGatewayService.enviar()`. La respuesta del cliente ya se ve hoy en la pestaña
  **Respuestas** del panel de WhatsApp (`/whatsapp`).

## 5. Modelo de datos

```
reporte_cliente                          -- Fase 3
  id            VARCHAR PK
  telefono      VARCHAR NOT NULL, INDEX
  tipo          VARCHAR NOT NULL         -- DEMORO | NO_DECLARO_VALORES | PEDIDO_FALSO | OTRO
  pedido_id     VARCHAR NULL
  pedido_numero BIGINT  NULL
  cadete_id     VARCHAR NULL
  cadete_nombre VARCHAR NULL
  nota          VARCHAR(500) NULL
  creado_en     INSTANT NOT NULL

telefono_validado                        -- Fase 2
  telefono      VARCHAR PK
  validado_en   INSTANT NOT NULL
  via           VARCHAR NOT NULL         -- WHATSAPP | SMS | ADMIN
```

Sin FK duras (mismo criterio que el resto del modelo: se guardan los ids y el nombre
desnormalizado, ej. `Incidencia.cadeteNombre`).

`Cliente.problematico` / `notasProblematico` **no se tocan** — siguen siendo el flag manual
del admin. El aviso pasa a ser la combinación de ese flag + el historial de reportes.

## 6. Degradación (el punto crítico)

El envío de un código hoy puede fallar en silencio. El orden tiene que ser:

1. **WhatsApp** (gateway conectado) →
2. **SMS** (si `SMS_ENABLED=true` y el gateway responde) →
3. **Sin verificar**: el código no se pudo mandar → la solicitud igual se crea, marcada
   `sinVerificar`, y el admin la ve con esa marca y la valida a mano (Fase 4).

Para que el paso 1 sea confiable hace falta que `WhatsappGatewayService` sepa si el gateway
está **conectado ahora** (ya existe: `estado()` + `WhatsappGatewayConnectionListener`).
Hoy `enviar()` encola y publica sin importar si hay alguien del otro lado — con la cola
persistida el mensaje sale cuando el gateway vuelva, pero el cliente se queda esperando un
código que puede tardar horas. Para un OTP eso no sirve: **si el gateway no está conectado,
hay que caer al paso 2 inmediatamente**, no encolar.

> Esto es un cambio de comportamiento respecto al uso actual de la cola (donde encolar-y-
> esperar es exactamente lo que se quiere). El OTP necesita una variante "enviar ahora o
> fallar rápido" — probablemente un `enviarYa(telefono, texto)` con timeout corto, separado
> del `enviar()` encolador.

### Modo simulado (para poder desarrollar sin chip)

Las Fases 2 y 4 dependen de mandar WhatsApp, y el gateway todavía no se probó en vivo
(falta el primer chip). Para no quedar bloqueados, `WhatsappGatewayService.enviarYa()` tiene
un **modo simulado**: en vez de publicar el comando al gateway, escribe el mensaje en la
tabla `whatsapp_mensaje` con estado `ENVIADO` y `chipUsado = "SIMULADO"`, y lo loguea.

**Cómo se lee el código** — por la pestaña **Mensajes enviados** del panel de WhatsApp
(`/whatsapp`), que ya existe y ya muestra el texto completo de cada mensaje
(`whatsapp.component.ts:199`). No hace falta ninguna pantalla nueva.

> ⚠️ **Lo que NO hay que hacer: devolver el código en la respuesta de la API.** Es la
> tentación obvia y es un agujero grave: `POST /api/publico/verificacion-telefono/enviar` es
> público y sin auth — si devuelve el código, cualquiera verifica cualquier teléfono y toda
> esta feature deja de existir. El panel y el log dan la misma capacidad de prueba sin
> exponer nada, porque no son públicos.

**El flag**: `app.whatsapp.modo-simulado` (env var `WHATSAPP_MODO_SIMULADO`), default
`false`, siguiendo el patrón de `app.demo.enabled` / `DEMO_ENABLED`:
- Al arrancar con el modo prendido, **loguear un warning bien visible** (`log.warn`) — igual
  que el resto de los flags de desarrollo del proyecto.
- Documentarlo en `configuracion.md` junto a `DEMO_ENABLED`, en la lista de
  "apagar en producción", y sumarlo a la sección **Salud del sistema** del panel.
- Nunca prenderlo por default ni de forma implícita (ej. "si no hay gateway configurado")
  — tiene que ser una decisión explícita, si no el día que el gateway se caiga en producción
  el sistema empieza a *simular* que manda códigos y nadie puede pedir.

Con esto, el circuito completo — pedir el código, verlo en el panel, cargarlo, que valide y
que el teléfono quede marcado como validado — **se puede probar de punta a punta hoy**, sin
chip, y sin agregar ninguna superficie insegura.

## 7. Endpoints

| Método | Ruta | Rol | Fase |
| --- | --- | --- | --- |
| `POST` | `/api/pedidos/me/{id}/reporte` | CADETE | 3 |
| `GET` | `/api/admin/clientes/{telefono}/reportes` | ADMIN | 4 |
| `POST` | `/api/admin/solicitudes-pedido/{id}/fraudulento` | ADMIN | 4 |
| `POST` | `/api/admin/solicitudes-pedido/{id}/whatsapp-confirmacion` | ADMIN | 4 |
| `POST` | `/api/publico/verificacion-telefono/enviar` | público | 2 (modificado) |

`solicitudes-pedido` (listado) gana dos campos en la respuesta: `avisoCliente` (Fase 1) y
`sinVerificar` (Fase 2).

## 8. Testing

- **Unitario** (`VerificacionTelefonoService`): transporte elegido según config; cae a SMS
  si el gateway de WhatsApp no está conectado; no consume el token si el envío falló; un
  teléfono ya validado no vuelve a pedir código.
- **Unitario** (`ReporteClienteService`): no se puede reportar un pedido ajeno; el resumen
  agrupa por tipo.
- **Integración**: `POST /api/publico/solicitudes-pedido` con un teléfono validado no
  requiere `verificacionToken`.

## 9. Fuera de alcance (a propósito)

- **Importar teléfonos de la base vieja** — idea del dueño, para después. El diseño la
  habilita: como el "pasa directo" se apoya en una tabla de teléfonos (no en el historial
  de pedidos), un import masivo los deja validados sin inventar pedidos falsos.
- **Bloqueo automático** por acumulación de reportes — se decidió que avisa y el admin
  decide.
- **Lista negra por dirección** — descartada por frágil (principio 1 de la sección 3).
- **Reemplazar el SMS de los avisos de pedido** (confirmación, link de seguimiento) — eso
  es el pendiente "conectar el envío real a los eventos del negocio" de
  `whatsapp-gateway.md`, es otro trabajo.

## 10. Riesgos y bloqueos

| Riesgo | Impacto |
| --- | --- |
| **El gateway nunca se probó en vivo** (falta el primer chip) | Resuelto para desarrollo con el **modo simulado** (sección 6): el circuito completo se prueba hoy. Queda pendiente la prueba real de que Baileys entregue, que es del gateway y no de esta feature. |
| Gateway caído / PC apagada / chip baneado | Cubierto por la degradación de la sección 6, pero solo si se implementa el "fallar rápido". |
| El código por WhatsApp puede ser más lento que el SMS (Baileys, cola del chip) | Aceptable para un OTP de 10 minutos de validez. |
| Volumen de chips (10-15 para ~300 msjs/día) | Un OTP por cliente nuevo suma al volumen. Al principio (todos nuevos) es +1 mensaje por pedido web. |
