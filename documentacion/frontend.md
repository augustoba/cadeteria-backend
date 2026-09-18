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

| Carpeta | Pantalla |
| --- | --- |
| `dashboard` | Pedidos del día (lista/kanban), cadetes libres, alertas |
| `pedidos` | Alta de pedido nuevo, detalle |
| `cadetes` | ABM de cadetes, ficha con estadísticas, solicitudes de alta |
| `zonas` | ABM de zonas (círculo o polígono dibujado a mano en el mapa) |
| `clientes` | Ficha de cliente por teléfono (tarifa especial, cuenta corriente) |
| `pedir` | Página **pública** sin login — el cliente carga su propio pedido, y la bandeja de "Pedidos web" que el admin revisa/cotiza |
| `mapa` | Mapa en vivo de cadetes y pedidos |
| `metricas` | Gráficos y tablas de Métricas (por cadete, por zona, por hora) |
| `pagos` | Pagos semanales / crédito por cadete |
| `chat` | Chat interno admin ↔ cadete |
| `incidencias` | Tickets/reclamos, generales o ligados a un pedido/cadete |
| `configuracion` | Toda la configuración editable — ver `configuracion.md` en esta misma carpeta |
| `usuarios` | Roles de admin |
| `seguimiento` | Página pública de seguimiento de un pedido (el link que recibe el cliente) |
| `hoja-ruta` | Hoja de ruta imprimible de los pedidos de un cadete |
| `registro-cadete` | Formulario público de alta de cadete por link |
| `login` | Login de admin |

## Convenciones

- Componentes standalone, `imports: [...]` en el decorador en vez de módulos.
- Estado con `signal`/`computed` (no NgRx ni servicios con `BehaviorSubject`).
- Mapas con Leaflet (`shared/mapa-picker.component.ts` para elegir/dibujar puntos, círculos y
  polígonos).
- Geocodificación de direcciones (autocompletar) combina varias fuentes gratuitas (Nominatim,
  Photon, y opcionalmente Geoapify/LocationIQ si se cargan sus keys en
  `core/services/geocoding.service.ts` — a diferencia de las keys de rutas del backend, estas
  quedan hardcodeadas en el código porque no son sensibles y no varían por cliente).
- La cotización automática de precio (`core/services/cotizacion.service.ts`) llama al backend
  (`/api/publico/cotizar`) — toda la lógica de precio vive en el backend, el front solo la
  muestra y permite sobreescribirla.

## Documentación relacionada

- [`configuracion.md`](./configuracion.md) — qué configurar en la pantalla Configuración y de
  dónde sacar cada API key.
- [`backend.md`](./backend.md) — API que consume este panel.
- [`apk.md`](./apk.md) — app de cadetes (Android), la otra punta del sistema.
