# Backend — cadeteria-backend

API REST + WebSocket que centraliza todo el sistema: pedidos, cadetes, zonas, cotización,
métricas y el panel admin. Java 21 + Spring Boot 3.3.5 + MySQL + JPA/Hibernate.

Para variables de entorno y qué cargar en el panel, ver [`configuracion.md`](./configuracion.md).

## Cómo correrlo

```bash
./mvnw spring-boot:run
```

Necesita una instancia de MySQL corriendo (por defecto `localhost:3306`, credenciales
`DB_USER`/`DB_PASSWORD`, default `root`/`root`) — la base `cadeteria` se crea sola
(`createDatabaseIfNotExist=true`). Copiá `src/main/resources/application-local.yml.example` a
`application-local.yml` (gitignoreado) para tus credenciales locales, o usá variables de
entorno.

Al arrancar contra una base vacía, corren en orden (`config/*Seeder.java`, cada uno
`@Order(N)`):

1. `DataSeeder` — parametrías base (estados de pedido/cadete, tipos de vehículo, resultados de
   oferta), admin inicial, y toda la configuración por defecto (ver `configuracion.md`).
2. `DemoCadeteZonaSeeder` — si no hay ningún cadete/zona cargado, siembra una zona y 2 cadetes
   de ejemplo (contraseña `cadete123`) para no arrancar con el panel vacío.

   ⚠️ **Inconsistencia conocida**: los siembra con usuario `jperez` / `mgomez`, pero el login
   de cadete exige que el usuario sea el DNI (`^[0-9]{1,8}$`, `CadeteDtos.REGEX_USERNAME_DNI`)
   desde 2026-09-12. El login en sí no valida formato (hace un `findByUsername` pelado, así
   que `jperez` todavía entra), pero son usuarios que **el propio panel rechaza si los editás**.
   Los DNIs ya están cargados (`30111222` / `30222333`) — el fix pendiente es usarlos como
   username. Los otros seeders (`DemoPedidoSeeder`, `DemoExtrasSeeder`) ya prefieren el DNI
   `11111111` y caen a "cualquier cadete" si no existe, así que no se rompen.
3. `DemoPedidoSeeder` — 8 pedidos de ejemplo cubriendo todos los estados del ciclo de vida,
   asignados al cadete demo. Se recrean en cada reinicio (no se acumulan).
4. `DemoExtrasSeeder` — chat interno, solicitudes de pedido (flujo "pedido por WhatsApp"),
   una postulación de cadete, una incidencia y pagos semanales de ejemplo.

Los seeders 2 a 4 solo hacen algo si `app.demo.enabled=true` (default `DEMO_ENABLED`) —
**apagar esta variable en producción**, y los 3 últimos dejan de tocar nada apenas hay
cadetes/zonas/pedidos reales cargados.

## Estructura del código

```
src/main/java/com/cadeteria/backend/
├── config/       Seguridad (JWT), CORS, WebSocket, seeders, AppProperties (env vars)
├── controller/   Endpoints REST — un archivo por área (Pedido, Cadete, Zona, Configuracion…)
├── service/      Toda la lógica de negocio — ver "Servicios clave" abajo
├── repository/   Spring Data JPA, un repo por entidad
├── model/        Entidades JPA
├── dto/          Records de request/response de cada controller
└── common/       Excepciones (BadRequestException, ResourceNotFoundException) y ApiError
```

## Servicios clave

- **`PedidoService`** — el núcleo: alta de pedidos, ciclo ofertar/aceptar/rechazar/expirar con
  reasignación automática en cadena (`buscarCandidato`, `liberarYReasignar`), jobs programados
  (`@Scheduled`) para asignación automática, expiración de ofertas y avisos de demora. Ver
  `buscarCandidato` para el algoritmo completo de a quién se le ofrece cada pedido (zona →
  fallback por distancia GPS → reglas de rechazos/urgencia, todo configurable).
- **`CotizacionService`** — sugiere el precio de un pedido nuevo (por Zona o por distancia real,
  más el recargo por dinero declarado). Ver `configuracion.md`.
- **`RutaService`** — distancia/ruta real por calle, con fallback entre OSRM → GraphHopper →
  OpenRouteService (usado por `CotizacionService` y por la ruta sugerida al cadete al aceptar
  un viaje).
- **`GeocodingService`** — resuelve a qué Zona pertenece un punto (lat/lng), por geometría
  (círculo o polígono) contra las zonas cargadas — no depende de un servicio externo.
- **`ConfiguracionService`** — key/value genérico (`getInt`/`getBoolean`/`getString`/
  `getBigDecimal`) sobre la tabla `configuracion`, con default en código si la clave no existe
  todavía. Toda la pantalla "Configuración" del panel lee/escribe acá.
- **`CadeteService`** / **`ZonaService`** / **`AdminUsuarioService`** — ABM y lógica propia de
  cada entidad (estados, turnos, topes de viajes, pago semanal/crédito, roles de admin).
- **`WebSocketPublisher`** — pushea eventos en vivo al panel y a la app del cadete (nuevo
  pedido, cambio de estado, ubicación, chat) vía STOMP.
- **`VerificacionTelefonoService`** — anti-abuso de la página pública `/pedir`: código de 6
  dígitos por SMS (10 min, 5 intentos, 3 pedidos cada 10 min por teléfono) que se canjea por
  un token de un solo uso; `SolicitudPedidoService.crear()` lo exige y lo consume. Ver el
  aviso sobre SMS deshabilitado en `frontend.md`.
- **`WhatsappGatewayService`** / **`SmsGatewayService`** / **`FcmService`** / **`EmailService`**
  / **`WebPushService`** — todas siguen el mismo criterio: si no están configuradas, se loguea
  y no rompen el flujo (ver "Servicios opcionales" en `configuracion.md`).

## Seguridad

JWT stateless (`JwtService`/`JwtAuthFilter`). Tanto admins como cadetes tienen un
`sessionToken`/`sid` que se regenera en cada login — un JWT viejo con otro `sid` deja de ser
válido, lo que permite "cerrar todas las sesiones" desde el panel sin necesidad de una lista de
tokens revocados.

## Documentación relacionada

- [`configuracion.md`](./configuracion.md) — variables de entorno y de panel, con pasos para
  sacar cada API key.
- [`frontend.md`](./frontend.md) — panel admin (Angular).
- [`apk.md`](./apk.md) — app de cadetes (Android).
- [`whatsapp-gateway.md`](./whatsapp-gateway.md) — gateway de WhatsApp (Baileys + chips).
