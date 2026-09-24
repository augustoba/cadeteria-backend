# Frontend — cadeteria-frontend

Panel de administración. Angular 19, standalone components (sin `NgModule`), Tailwind para
estilos, señales (`signal`/`computed`) para estado.

> Repo separado (`cadeteria-frontend`), documentado acá junto con el resto a pedido.

## Cómo correrlo

```bash
ng serve
```

Por defecto apunta al backend en `http://localhost:4200` de CORS permitido — la URL del
backend se configura en `src/app/core/config/site-config.ts` (`apiUrl`). Login con el usuario
admin sembrado por el backend (ver `backend.md` → `DataSeeder`).

## Estructura

```
src/app/
├── core/
│   ├── services/   Un servicio HTTP por entidad (PedidoService, CadeteService, ZonaService…)
│   ├── models/     Interfaces TypeScript que reflejan los DTOs del backend
│   ├── guards/     Route guards (login requerido, roles de admin)
│   ├── http/       Interceptor que agrega el JWT a cada request
│   └── config/     URL base de la API
└── features/       Una carpeta por pantalla — ver abajo
```

### Pantallas (`features/`)

| Carpeta | Ruta | Pantalla |
| --- | --- | --- |
| `dashboard` | `/` | Pedidos del día (lista/kanban), cadetes libres, alertas |
| `pedidos` | `/pedidos/nuevo` | Alta de pedido nuevo, detalle |
| `cadetes` | `/cadetes`, `/cadetes/:id`, `/cadetes/:id/ficha`, `/cadetes/solicitudes` | ABM de cadetes, ficha con estadísticas, solicitudes de alta |
| `zonas` | `/zonas` | ABM de zonas (círculo o polígono dibujado a mano en el mapa) |
| `clientes` | `/clientes`, `/clientes/:telefono` | Listado y ficha de cliente por teléfono (tarifa especial, cuenta corriente) |
| `pedir` | `/pedir`, `/confirmar-pedido/:token`, `/solicitudes-pedido` | Página **pública** sin login — el cliente carga su propio pedido (con verificación del teléfono, ver abajo), la página pública de confirmación, y la bandeja de "Pedidos web" que el admin revisa/cotiza |
| `mapa` | `/mapa` | Mapa en vivo de cadetes y pedidos |
| `metricas` | `/metricas` | Gráficos y tablas de Métricas (por cadete, por zona, por hora) |
| `pagos` | `/pagos` | Pagos semanales / crédito por cadete |
| `chat` | `/chat` | Chat interno admin ↔ cadete |
| `whatsapp` | `/whatsapp` | Panel del gateway de WhatsApp: chips, mensajes enviados (entregado/leído), respuestas de clientes. Solo `ROLE_ADMIN_DUENO` |
| `incidencias` | `/incidencias` | Tickets/reclamos, generales o ligados a un pedido/cadete |
| `configuracion` | `/configuracion` | Toda la configuración editable — ver `configuracion.md` en esta misma carpeta |
| `usuarios` | `/usuarios` | Cuentas de admin |
| `roles` | `/roles` | Roles y permisos de admin |
| `seguimiento` | `/seguimiento/:token` | Página pública de seguimiento de un pedido (el link que recibe el cliente) |
| `hoja-ruta` | `/hoja-ruta/:cadeteId` | Hoja de ruta imprimible de los pedidos de un cadete |
| `registro-cadete` | `/registro-cadete/:token` | Formulario público de alta de cadete por link |
| `login` | `/login` | Login de admin |

### Verificación del teléfono en la página pública

El cliente que carga un pedido en `/pedir` **no puede enviarlo sin confirmar antes que el
teléfono es suyo**: pide un código de 6 dígitos a `/api/publico/verificacion-telefono/enviar`
(que sale por SMS), lo confirma con `/verificar`, y el token de un solo uso resultante es lo
que `POST /api/publico/solicitudes-pedido` exige en el campo `verificacionToken` (default
`@NotBlank`). Es anti-abuso: sin esto cualquiera podía cargar pedidos falsos a nombre de un
número ajeno. Ver `VerificacionTelefonoService` en `backend.md`.

**Ojo con SMS deshabilitado**: si `SMS_ENABLED=false` (el default) el código no llega nunca
— `SmsGatewayService` loguea "SMS gateway deshabilitado" y devuelve `true` a propósito (no
cuenta como fallo), así que la página pública queda **inutilizable** en la práctica. Para
que `/pedir` funcione hay que tener el gateway de SMS configurado.

## Convenciones

- Componentes standalone, `imports: [...]` en el decorador en vez de módulos.
- Estado con `signal`/`computed` (no NgRx ni servicios con `BehaviorSubject`).
- Mapas con Leaflet (`shared/mapa-picker.component.ts` para elegir/dibujar puntos, círculos y
  polígonos).
- Geocodificación de direcciones (autocompletar): el front **no** llama a ningún proveedor
  directo (2026-09-21) — `core/services/geocoding-publico.service.ts` pasa siempre por el
  backend (`GeocodingProxyService`, ver `spec-geocoding-cache.md`), que combina Nominatim,
  Geoapify y LocationIQ detrás de una cache compartida. No hay ninguna API key de geocoding en
  el bundle del front.
- La cotización automática de precio (`core/services/cotizacion.service.ts`) llama al backend
  (`/api/publico/cotizar`) — toda la lógica de precio vive en el backend, el front solo la
  muestra y permite sobreescribirla.

## Documentación relacionada

- [`configuracion.md`](./configuracion.md) — qué configurar en la pantalla Configuración y de
  dónde sacar cada API key.
- [`backend.md`](./backend.md) — API que consume este panel.
- [`apk.md`](./apk.md) — app de cadetes (Android), la otra punta del sistema.
