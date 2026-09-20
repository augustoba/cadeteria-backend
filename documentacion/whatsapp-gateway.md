# Gateway de WhatsApp — whatsappgateway

Envía avisos de pedidos a clientes por WhatsApp sin usar la API oficial (que cobra por
mensaje/conversación) — usa [Baileys](https://github.com/WhiskeySockets/Baileys) para hablar
el protocolo de WhatsApp directo por WebSocket, con chips SIM descartables en vez del número
real de atención.

> Repo separado (`whatsappgateway`), documentado acá junto con el resto a pedido. Node.js.
> **Estado actual: código listo y compilando, todavía sin probar en vivo** — falta el primer
> chip de prueba.

## Cómo funciona (arquitectura)

- El backend corre en un servidor (hoy local, después en la nube); este gateway corre en
  **una PC del negocio** (no hace falta una dedicada). Como esa PC no tiene IP pública, la
  conexión se da vuelta: **el gateway se conecta hacia afuera** al backend por
  WebSocket/STOMP (mismo mecanismo que el chat y el mapa en vivo del panel) — sin
  port-forwarding, sin VPN, sin IP fija.
- El backend guarda cada mensaje en una cola (tabla `whatsapp_mensaje`) antes de publicarlo —
  si la PC/gateway está apagada o sin internet, el mensaje queda `PENDIENTE` y se reenvía
  solo al reconectar. Nada se pierde por un corte.
- Con varios chips cargados, cada mensaje elige uno por hash del id (reparte la carga entre
  todos).

## Instalación y uso

```bash
npm install
npm run pair -- chip1       # emparejar un chip nuevo (ver pasos abajo)
npm run test-send -- chip1 5493811234568 "prueba"   # mandar un mensaje suelto, sin backend
npm start                    # conectarse al backend y quedar escuchando comandos
```

**Emparejar un chip**: `npm run pair -- chip1` pide el número (con código de país, sin "+")
y devuelve un código de 8 dígitos. En el celular con esa SIM: WhatsApp → Ajustes →
Dispositivos vinculados → Vincular un dispositivo → Vincular con número de teléfono → cargar
el código. La sesión queda guardada en `sessions/chip1/`, no hace falta repetirlo salvo que se
cierre sesión desde el celular. Cada chip nuevo usa un id distinto (`chip2`, `chip3`, ...).

## Variables de entorno

Ambas opcionales, con default para desarrollo local:

| Variable | Para qué | Default |
| --- | --- | --- |
| `BACKEND_WS_URL` | URL del WebSocket del backend | `ws://localhost:8080/ws` |
| `GATEWAY_TOKEN` | Tiene que ser **igual** al `WHATSAPP_GATEWAY_TOKEN` del backend (ver `configuracion.md`) — si no coincide, el backend descarta la conexión en silencio, para que nadie que encuentre el endpoint pueda leer teléfonos/mensajes de clientes | valor de desarrollo |

Cuando el backend migre a la nube, solo cambia esta configuración (nada de código):
`BACKEND_WS_URL=wss://tu-dominio.com/ws` + `GATEWAY_TOKEN` igual al que tenga el backend en
producción (ahí sí, **no** dejar el default de desarrollo de ninguno de los dos lados).

## Probar el circuito completo

Con el backend corriendo local y `npm start` corriendo en otra terminal, desde el panel admin
(o con el JWT de admin a mano):

```
POST /api/admin/whatsapp/test
{ "telefono": "5493811234568", "mensaje": "Prueba end-to-end" }

GET /api/admin/whatsapp/estado   → si el gateway está conectado en este momento
```

Guía paso a paso completa (con qué mirar en cada paso, y cómo probar que nada se pierde si se
apaga la PC) en el `README.md` de este repo.

## Panel admin (`/whatsapp`, agregado 2026-09-16)

El panel tiene una pantalla propia (commit `440b017` de `admin-front`), con 3 pestañas:

- **Chips** — estado de cada chip (VINCULANDO/CONECTADO/DESCONECTADO/BANEADO), el código de
  pairing de 8 dígitos en vivo mientras vincula, y un form para **cargar un chip nuevo sin
  tocar la consola** (`POST /api/admin/whatsapp/chips` → el backend publica
  `/topic/whatsapp/vincular-chip` → el gateway pide el pairing code → lo devuelve por
  `onCodigo` → aparece en la tarjeta del chip sin refrescar).
- **Mensajes enviados** — tabla con estado, chip usado, entregado ✓ y leído ✓✓, más un form
  de prueba manual que reusa `/test`.
- **Respuestas de clientes** — lo que contestan, vía los listeners `messages.upsert` de cada
  chip.

El panel no mergea eventos puntuales: ante cualquier evento de `/topic/whatsapp/panel`
recarga las 3 listas (mismo patrón que `ChatService` con `/queue/admin/chat`).

**Todo `/api/admin/whatsapp/**` está restringido a `ROLE_ADMIN_DUENO`**, no `ADMIN` genérico
— expone teléfonos y contenido de mensajes de clientes. En el panel, el item de nav
"WhatsApp" va con `soloDueno: true`.

## Pendiente (a propósito, fuera de este prototipo)

- **Conectar el envío real a los eventos del negocio** (pedido creado, cadete asignado,
  etc.) — sigue siendo lo más grande que falta: `WhatsappGatewayService.enviar()` existe y
  funciona, pero hoy nadie lo llama desde el flujo real de pedidos, solo se dispara a mano
  con `/api/admin/whatsapp/test` o desde el form de prueba del panel.
- Comprar y emparejar el resto de los chips (10-15 según lo hablado).
- Variar un poco el texto entre envíos (acordado, no implementado todavía).
- El `Map` en memoria `waMessageId -> mensajeId` del gateway se pierde si reinicia — el
  mensaje ya quedó `ENVIADO` en la base igual, no es crítico, pero un reinicio pierde la
  trazabilidad de entrega/lectura de lo que estaba en vuelo.

## Si agregás un `@MessageMapping` nuevo de WhatsApp

No olvidar sumarlo a `WhatsappGatewayAuthInterceptor.DESTINOS_GATEWAY` — si no, el gateway
no puede llamarlo (se descarta en silencio, sin error visible).

## Documentación relacionada

- [`configuracion.md`](./configuracion.md) — `WHATSAPP_GATEWAY_TOKEN` y el resto de las
  variables del backend.
- [`backend.md`](./backend.md) — `WhatsappGatewayService`, la cola `whatsapp_mensaje` y los
  endpoints `/api/admin/whatsapp/*`.
