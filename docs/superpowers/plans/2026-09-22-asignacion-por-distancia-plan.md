# Asignación por distancia (sin zona) + tope de BICI al retiro — Plan de implementación

> **Para agentes que ejecuten esto:** SUB-SKILL REQUERIDA: usar superpowers:subagent-driven-development (recomendado) o superpowers:executing-plans para implementar este plan tarea por tarea. Los pasos usan sintaxis de checkbox (`- [ ]`) para el seguimiento.

**Objetivo:** Reemplazar el matching de pedidos por zona por matching 100% por distancia GPS, agregar un tope de distancia cadete-BICI→retiro, y pasar `tipoVehiculoRequerido` de un desplegable obligatorio MOTO/BICI a un checkbox opcional "requiere moto".

**Arquitectura:** `Pedido`/`SolicitudPedido` pierden el campo obligatorio de vehículo (pasa a `boolean requiereMoto`); `Pedido.zona` y `SolicitudPedido.zona` dejan de usarse (el primero solo se relaja en el esquema, el segundo se deja de mapear). `PedidoService.buscarCandidato` cae siempre en su fallback de distancia GPS existente, con dos topes nuevos aplicados solo a candidatos BICI. `asignarLote` reemplaza su chequeo "misma zona" por cercanía entre orígenes. Front (`admin-front`) y `cadete-app` siguen el contrato nuevo.

**Stack tecnológico:** Spring Boot 3 / JPA (Hibernate, `ddl-auto: update`, MySQL) + JUnit 5/Mockito en el backend (`cadeteria`); Angular 19 standalone components en `admin-front`; Kotlin/Retrofit en `cadete-app`.

**Spec:** `documentacion/spec-asignacion-por-distancia.md`

## Restricciones globales

- `distancia_maxima_bici_km` (viaje) y `distancia_maxima_bici_retiro_km` (retiro): `0` = sin límite, mismo patrón que la config existente.
- `distancia_maxima_lote_km`: default `3`.
- Ningún dato histórico se borra: `Pedido.zona` se mantiene (solo se relaja el NOT NULL); la columna vieja `tipo_vehiculo_requerido_id` queda huérfana con el historial.
- Nada en `Zona`, `ZonaController/Service/Dtos`, `CotizacionService`, `Cadete.zonaActual` ni el reporte `MetricasService.metricasPorZona` se toca.
- Backend: verificar con `./mvnw.cmd test` (Windows) tras cada tarea que compile y toda la suite pase.
- Front (`admin-front`): verificar con `npm run build` tras cada tarea — no hay tests unitarios de componentes en este repo, así que el build (con chequeo de tipos de plantillas) es la verificación.

---

### Tarea 1: Núcleo — modelo, DTOs, alta de pedido y fix de esquema

**Archivos:**
- Modificar: `src/main/java/com/cadeteria/backend/model/Pedido.java`
- Modificar: `src/main/java/com/cadeteria/backend/model/SolicitudPedido.java`
- Modificar: `src/main/java/com/cadeteria/backend/dto/PedidoDtos.java`
- Modificar: `src/main/java/com/cadeteria/backend/dto/SolicitudPedidoDtos.java`
- Modificar: `src/main/java/com/cadeteria/backend/service/PedidoService.java` (constructor, `crear`, `repetirPorToken` — el resto de este archivo lo toca la Task 2)
- Modificar: `src/main/java/com/cadeteria/backend/service/SolicitudPedidoService.java`
- Modificar: `src/main/java/com/cadeteria/backend/controller/SolicitudPedidoAdminController.java`
- Crear: `src/main/java/com/cadeteria/backend/config/PedidoZonaVehiculoSchemaFix.java`
- Modificar: `src/test/java/com/cadeteria/backend/service/PedidoServiceReasignacionTest.java`
- Modificar: `src/test/java/com/cadeteria/backend/service/PedidoServiceQuitarCadeteTest.java`

**Interfaces:**
- Produce: `Pedido.isRequiereMoto()/setRequiereMoto(boolean)` (reemplaza `getTipoVehiculoRequerido`), `Pedido.getZona()/setZona(Zona)` sigue igual pero ya no obligatorio. `SolicitudPedido.isRequiereMoto()/setRequiereMoto(boolean)` (reemplaza zona y tipoVehiculoRequerido por completo). `PedidoDtos.PedidoRequest(..., String detalle, boolean requiereMoto, boolean programado, Instant fechaProgramada, List<ParadaRequest> paradasAdicionales)`. `PedidoDtos.PedidoResponse(..., String detalle, boolean requiereMoto, LookupResponse estado, ...)`. `SolicitudPedidoDtos.RevisarSolicitudRequest(boolean requiereMoto, BigDecimal precio, BigDecimal montoDeclarado)`. `SolicitudPedidoService.confirmarDirecto(String id, boolean requiereMoto, BigDecimal precio, BigDecimal montoDeclarado)` y `.cotizar(String id, boolean requiereMoto, BigDecimal precio, BigDecimal montoDeclarado)` (sin `zonaId`/`tipoVehiculoId`). Estas firmas las consume la Task 7/8 de front indirectamente (vía el JSON) y esta misma task en `PedidoService.crear`.
- Consume: nada de tasks anteriores (es la primera).

- [ ] **Paso 1: Editar `Pedido.java` — modelo**

En `src/main/java/com/cadeteria/backend/model/Pedido.java`, reemplazar (líneas 71-77):

```java
    @ManyToOne(optional = false)
    @JoinColumn(name = "zona_id")
    private Zona zona;

    @ManyToOne(optional = false)
    @JoinColumn(name = "tipo_vehiculo_requerido_id")
    private TipoVehiculo tipoVehiculoRequerido;
```

por:

```java
    @ManyToOne
    @JoinColumn(name = "zona_id")
    private Zona zona;

    /**
     * Tildado: el matching automático solo ofrece el pedido a cadetes en MOTO. Sin tildar
     * (default): entra cualquiera, moto o bici — la bici queda sujeta a los topes de
     * distancia (ver PedidoService.buscarCandidato).
     */
    @Column(nullable = false)
    private boolean requiereMoto = false;
```

Y reemplazar los getters/setters (líneas 328-334):

```java
    public TipoVehiculo getTipoVehiculoRequerido() {
        return tipoVehiculoRequerido;
    }

    public void setTipoVehiculoRequerido(TipoVehiculo tipoVehiculoRequerido) {
        this.tipoVehiculoRequerido = tipoVehiculoRequerido;
    }
```

por:

```java
    public boolean isRequiereMoto() {
        return requiereMoto;
    }

    public void setRequiereMoto(boolean requiereMoto) {
        this.requiereMoto = requiereMoto;
    }
```

(El getter/setter de `zona`, líneas 320-326, no se toca.)

- [ ] **Paso 2: Editar `SolicitudPedido.java` — modelo**

Reemplazar el bloque de campos (líneas 62-68):

```java
    @ManyToOne
    @JoinColumn(name = "zona_id")
    private Zona zona;

    @ManyToOne
    @JoinColumn(name = "tipo_vehiculo_id")
    private TipoVehiculo tipoVehiculoRequerido;
```

por:

```java
    @Column(nullable = false)
    private boolean requiereMoto = false;
```

Y reemplazar los getters/setters (líneas 207-221):

```java
    public Zona getZona() {
        return zona;
    }

    public void setZona(Zona zona) {
        this.zona = zona;
    }

    public TipoVehiculo getTipoVehiculoRequerido() {
        return tipoVehiculoRequerido;
    }

    public void setTipoVehiculoRequerido(TipoVehiculo tipoVehiculoRequerido) {
        this.tipoVehiculoRequerido = tipoVehiculoRequerido;
    }
```

por:

```java
    public boolean isRequiereMoto() {
        return requiereMoto;
    }

    public void setRequiereMoto(boolean requiereMoto) {
        this.requiereMoto = requiereMoto;
    }
```

- [ ] **Paso 3: Crear el runner de fix de esquema**

Crear `src/main/java/com/cadeteria/backend/config/PedidoZonaVehiculoSchemaFix.java`:

```java
package com.cadeteria.backend.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Pedido.zona y Pedido.tipoVehiculoRequerido dejaron de ser obligatorios
 * (documentacion/spec-asignacion-por-distancia.md) — con `ddl-auto: update`, Hibernate
 * agrega columnas nuevas solas (como `requiere_moto`) pero no relaja un NOT NULL que ya
 * existía, así que lo hacemos acá a mano. `tipo_vehiculo_requerido_id` queda como columna
 * huérfana (con el historial viejo, ya no mapeada por ninguna entidad) y se usa una única
 * vez para el backfill de `requiere_moto`: los pedidos nuevos nunca la cargan, así que el
 * backfill es idempotente en cada arranque (nunca toca una fila creada después de este
 * cambio).
 * <p>
 * `MODIFY COLUMN` es sintaxis de MySQL (producción, ver application.yml). Los tests con
 * `@SpringBootTest` (ej. BackendApplicationTests) arrancan contra H2 con un esquema fresco
 * generado directo desde las entidades — ahí no existe nada que migrar (la columna vieja
 * `tipo_vehiculo_requerido_id` ni se crea, al no estar mapeada por ninguna entidad, y
 * `zona_id` ya nace nullable). Cada sentencia se ignora si falla, en vez de tirar abajo el
 * arranque — mismo criterio de degradación que ya usan RutaService/CotizacionService con
 * sus proveedores opcionales.
 */
@Component
@Order(-1)
public class PedidoZonaVehiculoSchemaFix implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(PedidoZonaVehiculoSchemaFix.class);

    private final JdbcTemplate jdbcTemplate;

    public PedidoZonaVehiculoSchemaFix(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(String... args) {
        alterSiAplica("ALTER TABLE pedido MODIFY COLUMN zona_id VARCHAR(255) NULL");
        alterSiAplica("ALTER TABLE pedido MODIFY COLUMN tipo_vehiculo_requerido_id VARCHAR(255) NULL");
        try {
            int actualizados = jdbcTemplate.update(
                    "UPDATE pedido SET requiere_moto = (tipo_vehiculo_requerido_id = 'MOTO') "
                            + "WHERE tipo_vehiculo_requerido_id IS NOT NULL");
            if (actualizados > 0) {
                log.info("Esquema: {} pedido(s) viejo(s) migrado(s) a requiere_moto.", actualizados);
            }
        } catch (DataAccessException e) {
            log.debug("Sin tipo_vehiculo_requerido_id para migrar (esquema nuevo, ej. H2 en tests): {}", e.getMessage());
        }
    }

    private void alterSiAplica(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (DataAccessException e) {
            log.debug("No se aplico '{}' (no hace falta en este entorno/dialecto): {}", sql, e.getMessage());
        }
    }
}
```

- [ ] **Paso 4: Editar `PedidoDtos.java` — `PedidoRequest`, `PedidoResponse`, comentario de `AsignarLoteRequest`**

Reemplazar (líneas 17-35):

```java
    public record PedidoRequest(
            @NotBlank String clienteTelefono,
            @NotBlank String clienteNombre,
            @NotBlank String origenDireccion,
            @NotNull Double origenLat,
            @NotNull Double origenLng,
            @NotBlank String destinoDireccion,
            @NotNull Double destinoLat,
            @NotNull Double destinoLng,
            @NotNull BigDecimal precio,
            BigDecimal montoDeclarado,
            String detalle,
            @NotBlank String zonaId,
            @NotBlank String tipoVehiculoRequeridoId,
            boolean programado,
            Instant fechaProgramada,
            /** Paradas intermedias, en orden (ronda 3, punto 38) — null o vacío si el pedido es simple. */
            List<ParadaRequest> paradasAdicionales
    ) {}
```

por:

```java
    public record PedidoRequest(
            @NotBlank String clienteTelefono,
            @NotBlank String clienteNombre,
            @NotBlank String origenDireccion,
            @NotNull Double origenLat,
            @NotNull Double origenLng,
            @NotBlank String destinoDireccion,
            @NotNull Double destinoLat,
            @NotNull Double destinoLng,
            @NotNull BigDecimal precio,
            BigDecimal montoDeclarado,
            String detalle,
            boolean requiereMoto,
            boolean programado,
            Instant fechaProgramada,
            /** Paradas intermedias, en orden (ronda 3, punto 38) — null o vacío si el pedido es simple. */
            List<ParadaRequest> paradasAdicionales
    ) {}
```

Reemplazar la firma del record `PedidoResponse` (línea 59):

```java
            LookupResponse zona, LookupResponse tipoVehiculoRequerido, LookupResponse estado,
```

por:

```java
            boolean requiereMoto, LookupResponse estado,
```

Reemplazar dentro de `from(Pedido p, boolean incluirParadas)` (líneas 102-104):

```java
                    p.getPrecio(), p.getMontoDeclarado(), p.getDetalle(),
                    LookupResponse.from(p.getZona()), LookupResponse.from(p.getTipoVehiculoRequerido()),
                    LookupResponse.from(p.getEstado()),
```

por:

```java
                    p.getPrecio(), p.getMontoDeclarado(), p.getDetalle(),
                    p.isRequiereMoto(), LookupResponse.from(p.getEstado()),
```

Y actualizar el comentario de `AsignarLoteRequest` (línea 141), de:

```java
    /** Agrupar pedidos de la misma zona en una sola oferta a un cadete (ronda 4, punto 61). */
```

a:

```java
    /** Agrupar pedidos con orígenes cercanos en una sola oferta a un cadete (ronda 4, punto 61). */
```

- [ ] **Paso 5: Reescribir `SolicitudPedidoDtos.java` completo**

Reemplazar todo el archivo `src/main/java/com/cadeteria/backend/dto/SolicitudPedidoDtos.java` por:

```java
package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.SolicitudPedido;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class SolicitudPedidoDtos {

    private SolicitudPedidoDtos() {}

    /** Lo que completa el cliente en la página pública "/pedir". */
    public record SolicitudPedidoRequest(
            @NotBlank String origenDireccion, @NotNull Double origenLat, @NotNull Double origenLng,
            @NotBlank String destinoDireccion, @NotNull Double destinoLat, @NotNull Double destinoLng,
            boolean llevaDinero, BigDecimal montoDeclarado,
            boolean retornaAlOrigen,
            @NotBlank String clienteNombre, @NotBlank String clienteTelefono,
            String detalle,
            /** Token de VerificacionTelefonoService.verificarCodigo — confirma que el teléfono es real (mejora 2026-09-17). */
            @NotBlank String verificacionToken
    ) {}

    public record SolicitudPedidoResponse(
            String id,
            String origenDireccion, Double origenLat, Double origenLng,
            String destinoDireccion, Double destinoLat, Double destinoLng,
            boolean llevaDinero, BigDecimal montoDeclarado,
            boolean retornaAlOrigen,
            String clienteNombre, String clienteTelefono,
            String detalle,
            String estado,
            boolean requiereMoto, BigDecimal precio,
            String pedidoCreadoId, String motivoRechazo,
            Instant creadoEn
    ) {
        public static SolicitudPedidoResponse from(SolicitudPedido s) {
            return new SolicitudPedidoResponse(
                    s.getId(), s.getOrigenDireccion(), s.getOrigenLat(), s.getOrigenLng(),
                    s.getDestinoDireccion(), s.getDestinoLat(), s.getDestinoLng(),
                    s.isLlevaDinero(), s.getMontoDeclarado(), s.isRetornaAlOrigen(),
                    s.getClienteNombre(), s.getClienteTelefono(), s.getDetalle(), s.getEstado(),
                    s.isRequiereMoto(), s.getPrecio(),
                    s.getPedidoCreadoId(), s.getMotivoRechazo(), s.getCreadoEn());
        }
    }

    /** Confirmar directo (ya se acordó el precio) o mandar cotización (el cliente confirma solo) — mismos campos. */
    public record RevisarSolicitudRequest(
            boolean requiereMoto, @NotNull BigDecimal precio, BigDecimal montoDeclarado
    ) {}

    public record RechazarSolicitudRequest(String motivo) {}

    /** Lo que ve la página pública "/confirmar-pedido/:token" antes (y después) de confirmar. */
    public record ConfirmacionPublicaResponse(
            String estado, String origenDireccion, String destinoDireccion, BigDecimal precio,
            String tokenSeguimiento
    ) {}
}
```

- [ ] **Paso 6: Editar `PedidoService.java` — constructor y `crear`/`repetirPorToken`**

Quitar los imports que quedan sin uso (líneas 17-18, 32-33):

```java
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.model.Zona;
```

y

```java
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import com.cadeteria.backend.repository.ZonaRepository;
```

Reemplazar los campos y el constructor (líneas 68-118):

```java
    private final PedidoRepository repo;
    private final CadeteRepository cadeteRepo;
    private final ZonaRepository zonaRepo;
    private final TipoVehiculoRepository tipoVehiculoRepo;
    private final EstadoPedidoRepository estadoPedidoRepo;
    private final ResultadoOfertaRepository resultadoOfertaRepo;
    private final OfertaPedidoRepository ofertaRepo;
    private final EstadoCadeteRepository estadoCadeteRepo;
    private final ConfiguracionService configuracionService;
    private final WebSocketPublisher publisher;
    private final FcmService fcmService;
    private final SmsGatewayService smsGatewayService;
    private final PedidoUbicacionRepository pedidoUbicacionRepo;
    private final PedidoComentarioRepository pedidoComentarioRepo;
    private final PedidoPrecioLogRepository pedidoPrecioLogRepo;
    private final WebPushService webPushService;
    private final PedidoParadaRepository pedidoParadaRepo;
    private final MovimientoCreditoRepository movimientoCreditoRepo;
    private final IncidenciaRepository incidenciaRepo;
    private final String frontBaseUrlSeguimiento;

    public PedidoService(PedidoRepository repo, CadeteRepository cadeteRepo, ZonaRepository zonaRepo,
                          TipoVehiculoRepository tipoVehiculoRepo, EstadoPedidoRepository estadoPedidoRepo,
                          ResultadoOfertaRepository resultadoOfertaRepo, OfertaPedidoRepository ofertaRepo,
                          EstadoCadeteRepository estadoCadeteRepo, ConfiguracionService configuracionService,
                          WebSocketPublisher publisher, FcmService fcmService, SmsGatewayService smsGatewayService,
                          PedidoUbicacionRepository pedidoUbicacionRepo, PedidoComentarioRepository pedidoComentarioRepo,
                          PedidoPrecioLogRepository pedidoPrecioLogRepo, WebPushService webPushService,
                          PedidoParadaRepository pedidoParadaRepo, MovimientoCreditoRepository movimientoCreditoRepo,
                          IncidenciaRepository incidenciaRepo, AppProperties props) {
        this.repo = repo;
        this.movimientoCreditoRepo = movimientoCreditoRepo;
        this.incidenciaRepo = incidenciaRepo;
        this.cadeteRepo = cadeteRepo;
        this.zonaRepo = zonaRepo;
        this.tipoVehiculoRepo = tipoVehiculoRepo;
        this.estadoPedidoRepo = estadoPedidoRepo;
        this.resultadoOfertaRepo = resultadoOfertaRepo;
        this.ofertaRepo = ofertaRepo;
        this.estadoCadeteRepo = estadoCadeteRepo;
        this.configuracionService = configuracionService;
        this.publisher = publisher;
        this.fcmService = fcmService;
        this.smsGatewayService = smsGatewayService;
        this.pedidoParadaRepo = pedidoParadaRepo;
        this.pedidoUbicacionRepo = pedidoUbicacionRepo;
        this.pedidoComentarioRepo = pedidoComentarioRepo;
        this.pedidoPrecioLogRepo = pedidoPrecioLogRepo;
        this.webPushService = webPushService;
        this.frontBaseUrlSeguimiento = props.getFrontBaseUrl();
    }
```

por (sin `zonaRepo`/`tipoVehiculoRepo`):

```java
    private final PedidoRepository repo;
    private final CadeteRepository cadeteRepo;
    private final EstadoPedidoRepository estadoPedidoRepo;
    private final ResultadoOfertaRepository resultadoOfertaRepo;
    private final OfertaPedidoRepository ofertaRepo;
    private final EstadoCadeteRepository estadoCadeteRepo;
    private final ConfiguracionService configuracionService;
    private final WebSocketPublisher publisher;
    private final FcmService fcmService;
    private final SmsGatewayService smsGatewayService;
    private final PedidoUbicacionRepository pedidoUbicacionRepo;
    private final PedidoComentarioRepository pedidoComentarioRepo;
    private final PedidoPrecioLogRepository pedidoPrecioLogRepo;
    private final WebPushService webPushService;
    private final PedidoParadaRepository pedidoParadaRepo;
    private final MovimientoCreditoRepository movimientoCreditoRepo;
    private final IncidenciaRepository incidenciaRepo;
    private final String frontBaseUrlSeguimiento;

    public PedidoService(PedidoRepository repo, CadeteRepository cadeteRepo, EstadoPedidoRepository estadoPedidoRepo,
                          ResultadoOfertaRepository resultadoOfertaRepo, OfertaPedidoRepository ofertaRepo,
                          EstadoCadeteRepository estadoCadeteRepo, ConfiguracionService configuracionService,
                          WebSocketPublisher publisher, FcmService fcmService, SmsGatewayService smsGatewayService,
                          PedidoUbicacionRepository pedidoUbicacionRepo, PedidoComentarioRepository pedidoComentarioRepo,
                          PedidoPrecioLogRepository pedidoPrecioLogRepo, WebPushService webPushService,
                          PedidoParadaRepository pedidoParadaRepo, MovimientoCreditoRepository movimientoCreditoRepo,
                          IncidenciaRepository incidenciaRepo, AppProperties props) {
        this.repo = repo;
        this.movimientoCreditoRepo = movimientoCreditoRepo;
        this.incidenciaRepo = incidenciaRepo;
        this.cadeteRepo = cadeteRepo;
        this.estadoPedidoRepo = estadoPedidoRepo;
        this.resultadoOfertaRepo = resultadoOfertaRepo;
        this.ofertaRepo = ofertaRepo;
        this.estadoCadeteRepo = estadoCadeteRepo;
        this.configuracionService = configuracionService;
        this.publisher = publisher;
        this.fcmService = fcmService;
        this.smsGatewayService = smsGatewayService;
        this.pedidoParadaRepo = pedidoParadaRepo;
        this.pedidoUbicacionRepo = pedidoUbicacionRepo;
        this.pedidoComentarioRepo = pedidoComentarioRepo;
        this.pedidoPrecioLogRepo = pedidoPrecioLogRepo;
        this.webPushService = webPushService;
        this.frontBaseUrlSeguimiento = props.getFrontBaseUrl();
    }
```

Reemplazar el principio de `crear` (líneas 341-369):

```java
    public Pedido crear(PedidoRequest req) {
        Zona zona = zonaRepo.findById(req.zonaId())
                .orElseThrow(() -> ResourceNotFoundException.of("Zona", req.zonaId()));
        TipoVehiculo tipo = tipoVehiculoRepo.findById(req.tipoVehiculoRequeridoId())
                .orElseThrow(() -> ResourceNotFoundException.of("Tipo de vehiculo", req.tipoVehiculoRequeridoId()));

        boolean esProgramado = req.programado() && req.fechaProgramada() != null
                && req.fechaProgramada().isAfter(Instant.now());

        Pedido p = new Pedido();
        p.setId(UUID.randomUUID().toString());
        p.setNumero(configuracionService.siguienteNumeroPedido());
        p.setTokenSeguimiento(UUID.randomUUID().toString());
        p.setClienteTelefono(req.clienteTelefono().trim());
        p.setClienteNombre(req.clienteNombre().trim());
        p.setOrigenDireccion(req.origenDireccion().trim());
        p.setOrigenLat(req.origenLat());
        p.setOrigenLng(req.origenLng());
        p.setDestinoDireccion(req.destinoDireccion().trim());
        p.setDestinoLat(req.destinoLat());
        p.setDestinoLng(req.destinoLng());
        p.setPrecio(req.precio());
        p.setMontoDeclarado(req.montoDeclarado() == null ? BigDecimal.ZERO : req.montoDeclarado());
        p.setDetalle(req.detalle());
        p.setZona(zona);
        p.setTipoVehiculoRequerido(tipo);
        p.setProgramado(esProgramado);
```

por:

```java
    public Pedido crear(PedidoRequest req) {
        boolean esProgramado = req.programado() && req.fechaProgramada() != null
                && req.fechaProgramada().isAfter(Instant.now());

        Pedido p = new Pedido();
        p.setId(UUID.randomUUID().toString());
        p.setNumero(configuracionService.siguienteNumeroPedido());
        p.setTokenSeguimiento(UUID.randomUUID().toString());
        p.setClienteTelefono(req.clienteTelefono().trim());
        p.setClienteNombre(req.clienteNombre().trim());
        p.setOrigenDireccion(req.origenDireccion().trim());
        p.setOrigenLat(req.origenLat());
        p.setOrigenLng(req.origenLng());
        p.setDestinoDireccion(req.destinoDireccion().trim());
        p.setDestinoLat(req.destinoLat());
        p.setDestinoLng(req.destinoLng());
        p.setPrecio(req.precio());
        p.setMontoDeclarado(req.montoDeclarado() == null ? BigDecimal.ZERO : req.montoDeclarado());
        p.setDetalle(req.detalle());
        p.setRequiereMoto(req.requiereMoto());
        p.setProgramado(esProgramado);
```

Y reemplazar `repetirPorToken` (líneas 407-413):

```java
        PedidoRequest req = new PedidoRequest(
                original.getClienteTelefono(), original.getClienteNombre(),
                original.getOrigenDireccion(), original.getOrigenLat(), original.getOrigenLng(),
                original.getDestinoDireccion(), original.getDestinoLat(), original.getDestinoLng(),
                original.getPrecio(), null, "Repetición del pedido #" + original.getNumero(),
                original.getZona().getId(), original.getTipoVehiculoRequerido().getId(),
                false, null, null);
```

por:

```java
        PedidoRequest req = new PedidoRequest(
                original.getClienteTelefono(), original.getClienteNombre(),
                original.getOrigenDireccion(), original.getOrigenLat(), original.getOrigenLng(),
                original.getDestinoDireccion(), original.getDestinoLat(), original.getDestinoLng(),
                original.getPrecio(), null, "Repetición del pedido #" + original.getNumero(),
                original.isRequiereMoto(),
                false, null, null);
```

- [ ] **Paso 7: Editar `SolicitudPedidoService.java`**

Quitar los imports sin uso:

```java
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.model.Zona;
```

```java
import com.cadeteria.backend.repository.TipoVehiculoRepository;
import com.cadeteria.backend.repository.ZonaRepository;
```

Reemplazar los campos y el constructor (líneas 37-64):

```java
    private final SolicitudPedidoRepository repo;
    private final ZonaRepository zonaRepo;
    private final TipoVehiculoRepository tipoVehiculoRepo;
    private final PedidoService pedidoService;
    private final SmsGatewayService smsGatewayService;
    private final WebSocketPublisher publisher;
    private final VerificacionTelefonoService verificacionTelefonoService;
    private final ConfiguracionService configuracionService;
    private final String frontBaseUrl;

    private static final ZoneId ZONA_ART = ZoneId.of("America/Argentina/Buenos_Aires");

    public SolicitudPedidoService(SolicitudPedidoRepository repo, ZonaRepository zonaRepo,
                                   TipoVehiculoRepository tipoVehiculoRepo, PedidoService pedidoService,
                                   SmsGatewayService smsGatewayService, WebSocketPublisher publisher,
                                   VerificacionTelefonoService verificacionTelefonoService,
                                   ConfiguracionService configuracionService,
                                   AppProperties props) {
        this.repo = repo;
        this.zonaRepo = zonaRepo;
        this.tipoVehiculoRepo = tipoVehiculoRepo;
        this.pedidoService = pedidoService;
        this.smsGatewayService = smsGatewayService;
        this.publisher = publisher;
        this.verificacionTelefonoService = verificacionTelefonoService;
        this.configuracionService = configuracionService;
        this.frontBaseUrl = props.getFrontBaseUrl();
    }
```

por:

```java
    private final SolicitudPedidoRepository repo;
    private final PedidoService pedidoService;
    private final SmsGatewayService smsGatewayService;
    private final WebSocketPublisher publisher;
    private final VerificacionTelefonoService verificacionTelefonoService;
    private final ConfiguracionService configuracionService;
    private final String frontBaseUrl;

    private static final ZoneId ZONA_ART = ZoneId.of("America/Argentina/Buenos_Aires");

    public SolicitudPedidoService(SolicitudPedidoRepository repo, PedidoService pedidoService,
                                   SmsGatewayService smsGatewayService, WebSocketPublisher publisher,
                                   VerificacionTelefonoService verificacionTelefonoService,
                                   ConfiguracionService configuracionService,
                                   AppProperties props) {
        this.repo = repo;
        this.pedidoService = pedidoService;
        this.smsGatewayService = smsGatewayService;
        this.publisher = publisher;
        this.verificacionTelefonoService = verificacionTelefonoService;
        this.configuracionService = configuracionService;
        this.frontBaseUrl = props.getFrontBaseUrl();
    }
```

Reemplazar `confirmarDirecto`, `cotizar`, `confirmarPorToken` y `crearPedidoDesde` (líneas 143-226) completos:

```java
    /** El admin ya tiene el precio acordado (ej. lo charló por teléfono) — crea el pedido de una. */
    public Pedido confirmarDirecto(String id, String zonaId, String tipoVehiculoId, BigDecimal precio, BigDecimal montoDeclarado) {
        SolicitudPedido s = exigirPendiente(id);
        Zona zona = zonaRepo.findById(zonaId).orElseThrow(() -> ResourceNotFoundException.of("Zona", zonaId));
        TipoVehiculo tipo = tipoVehiculoRepo.findById(tipoVehiculoId)
                .orElseThrow(() -> ResourceNotFoundException.of("Tipo de vehiculo", tipoVehiculoId));
        Pedido pedido = crearPedidoDesde(s, zona, tipo, precio, montoDeclarado);

        s.setZona(zona);
        s.setTipoVehiculoRequerido(tipo);
        s.setPrecio(precio);
        s.setEstado("CONFIRMADA");
        s.setPedidoCreadoId(pedido.getId());
        repo.save(s);

        enviarSmsConfirmado(pedido);
        return pedido;
    }

    /** El admin no tiene un precio ya charlado — le manda una cotización, el cliente confirma solo con el link. */
    public SolicitudPedido cotizar(String id, String zonaId, String tipoVehiculoId, BigDecimal precio, BigDecimal montoDeclarado) {
        SolicitudPedido s = exigirPendiente(id);
        Zona zona = zonaRepo.findById(zonaId).orElseThrow(() -> ResourceNotFoundException.of("Zona", zonaId));
        TipoVehiculo tipo = tipoVehiculoRepo.findById(tipoVehiculoId)
                .orElseThrow(() -> ResourceNotFoundException.of("Tipo de vehiculo", tipoVehiculoId));
        s.setZona(zona);
        s.setTipoVehiculoRequerido(tipo);
        s.setPrecio(precio);
        s.setMontoDeclarado(montoDeclarado);
        s.setEstado("COTIZADO");
        s = repo.save(s);

        String link = frontBaseUrl + "/confirmar-pedido/" + s.getTokenConfirmacion();
        smsGatewayService.enviar(s.getClienteTelefono(),
                "Cotización de tu envío: $" + precio + ". Para confirmarlo entrá acá: " + link);
        return s;
    }

    /** El cliente entra al link de la cotización y confirma — recién ahí se crea el Pedido real. Idempotente. */
    public Pedido confirmarPorToken(String token) {
        SolicitudPedido s = getPorToken(token);
        if ("CONFIRMADA".equals(s.getEstado())) {
            return pedidoService.get(s.getPedidoCreadoId());
        }
        if (!"COTIZADO".equals(s.getEstado())) {
            throw new BadRequestException("Esta solicitud no tiene una cotización esperando confirmación.");
        }
        Pedido pedido = crearPedidoDesde(s, s.getZona(), s.getTipoVehiculoRequerido(), s.getPrecio(), s.getMontoDeclarado());
        s.setEstado("CONFIRMADA");
        s.setPedidoCreadoId(pedido.getId());
        repo.save(s);

        enviarSmsConfirmado(pedido);
        return pedido;
    }
```

```java
    private Pedido crearPedidoDesde(SolicitudPedido s, Zona zona, TipoVehiculo tipo, BigDecimal precio, BigDecimal montoDeclarado) {
        StringBuilder detalle = new StringBuilder();
        if (s.isRetornaAlOrigen()) detalle.append("🔁 Retorna al origen. ");
        if (s.isLlevaDinero()) detalle.append("💵 Lleva dinero. ");
        if (s.getDetalle() != null) detalle.append(s.getDetalle());

        PedidoRequest req = new PedidoRequest(
                s.getClienteTelefono(), s.getClienteNombre(),
                s.getOrigenDireccion(), s.getOrigenLat(), s.getOrigenLng(),
                s.getDestinoDireccion(), s.getDestinoLat(), s.getDestinoLng(),
                precio, montoDeclarado, detalle.length() == 0 ? null : detalle.toString().trim(),
                zona.getId(), tipo.getId(), false, null, null);
        return pedidoService.crear(req);
    }
```

por:

```java
    /** El admin ya tiene el precio acordado (ej. lo charló por teléfono) — crea el pedido de una. */
    public Pedido confirmarDirecto(String id, boolean requiereMoto, BigDecimal precio, BigDecimal montoDeclarado) {
        SolicitudPedido s = exigirPendiente(id);
        Pedido pedido = crearPedidoDesde(s, requiereMoto, precio, montoDeclarado);

        s.setRequiereMoto(requiereMoto);
        s.setPrecio(precio);
        s.setEstado("CONFIRMADA");
        s.setPedidoCreadoId(pedido.getId());
        repo.save(s);

        enviarSmsConfirmado(pedido);
        return pedido;
    }

    /** El admin no tiene un precio ya charlado — le manda una cotización, el cliente confirma solo con el link. */
    public SolicitudPedido cotizar(String id, boolean requiereMoto, BigDecimal precio, BigDecimal montoDeclarado) {
        SolicitudPedido s = exigirPendiente(id);
        s.setRequiereMoto(requiereMoto);
        s.setPrecio(precio);
        s.setMontoDeclarado(montoDeclarado);
        s.setEstado("COTIZADO");
        s = repo.save(s);

        String link = frontBaseUrl + "/confirmar-pedido/" + s.getTokenConfirmacion();
        smsGatewayService.enviar(s.getClienteTelefono(),
                "Cotización de tu envío: $" + precio + ". Para confirmarlo entrá acá: " + link);
        return s;
    }

    /** El cliente entra al link de la cotización y confirma — recién ahí se crea el Pedido real. Idempotente. */
    public Pedido confirmarPorToken(String token) {
        SolicitudPedido s = getPorToken(token);
        if ("CONFIRMADA".equals(s.getEstado())) {
            return pedidoService.get(s.getPedidoCreadoId());
        }
        if (!"COTIZADO".equals(s.getEstado())) {
            throw new BadRequestException("Esta solicitud no tiene una cotización esperando confirmación.");
        }
        Pedido pedido = crearPedidoDesde(s, s.isRequiereMoto(), s.getPrecio(), s.getMontoDeclarado());
        s.setEstado("CONFIRMADA");
        s.setPedidoCreadoId(pedido.getId());
        repo.save(s);

        enviarSmsConfirmado(pedido);
        return pedido;
    }
```

```java
    private Pedido crearPedidoDesde(SolicitudPedido s, boolean requiereMoto, BigDecimal precio, BigDecimal montoDeclarado) {
        StringBuilder detalle = new StringBuilder();
        if (s.isRetornaAlOrigen()) detalle.append("🔁 Retorna al origen. ");
        if (s.isLlevaDinero()) detalle.append("💵 Lleva dinero. ");
        if (s.getDetalle() != null) detalle.append(s.getDetalle());

        PedidoRequest req = new PedidoRequest(
                s.getClienteTelefono(), s.getClienteNombre(),
                s.getOrigenDireccion(), s.getOrigenLat(), s.getOrigenLng(),
                s.getDestinoDireccion(), s.getDestinoLat(), s.getDestinoLng(),
                precio, montoDeclarado, detalle.length() == 0 ? null : detalle.toString().trim(),
                requiereMoto, false, null, null);
        return pedidoService.crear(req);
    }
```

- [ ] **Paso 8: Editar `SolicitudPedidoAdminController.java`**

Reemplazar (líneas 34-46):

```java
    /** Ya se acordó el precio (ej. por teléfono) — crea el pedido de una. */
    @PostMapping("/{id}/confirmar-directo")
    public PedidoResponse confirmarDirecto(@PathVariable String id, @Valid @RequestBody RevisarSolicitudRequest req) {
        return PedidoResponse.from(
                service.confirmarDirecto(id, req.zonaId(), req.tipoVehiculoRequeridoId(), req.precio(), req.montoDeclarado()));
    }

    /** Le manda la cotización por SMS con un link — el cliente confirma solo, sin llamar. */
    @PostMapping("/{id}/cotizar")
    public SolicitudPedidoResponse cotizar(@PathVariable String id, @Valid @RequestBody RevisarSolicitudRequest req) {
        return SolicitudPedidoResponse.from(
                service.cotizar(id, req.zonaId(), req.tipoVehiculoRequeridoId(), req.precio(), req.montoDeclarado()));
    }
```

por:

```java
    /** Ya se acordó el precio (ej. por teléfono) — crea el pedido de una. */
    @PostMapping("/{id}/confirmar-directo")
    public PedidoResponse confirmarDirecto(@PathVariable String id, @Valid @RequestBody RevisarSolicitudRequest req) {
        return PedidoResponse.from(
                service.confirmarDirecto(id, req.requiereMoto(), req.precio(), req.montoDeclarado()));
    }

    /** Le manda la cotización por SMS con un link — el cliente confirma solo, sin llamar. */
    @PostMapping("/{id}/cotizar")
    public SolicitudPedidoResponse cotizar(@PathVariable String id, @Valid @RequestBody RevisarSolicitudRequest req) {
        return SolicitudPedidoResponse.from(
                service.cotizar(id, req.requiereMoto(), req.precio(), req.montoDeclarado()));
    }
```

- [ ] **Paso 9: Arreglar `PedidoServiceReasignacionTest.java`**

El constructor de `PedidoService` perdió `zonaRepo`/`tipoVehiculoRepo`, y el matching pasa a ser 100% por distancia GPS — el cadete y los pedidos de este test necesitan lat/lng para seguir siendo candidatos.

Reemplazar la construcción del service (líneas 68-76):

```java
        service = new PedidoService(
                repo, cadeteRepo, mock(ZonaRepository.class), mock(TipoVehiculoRepository.class),
                estadoPedidoRepo, resultadoOfertaRepo, ofertaRepo,
                mock(EstadoCadeteRepository.class), configuracionService,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), new AppProperties());
```

por:

```java
        service = new PedidoService(
                repo, cadeteRepo, estadoPedidoRepo, resultadoOfertaRepo, ofertaRepo,
                mock(EstadoCadeteRepository.class), configuracionService,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), new AppProperties());
```

Reemplazar la creación del cadete (líneas 87-93):

```java
        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setNombre("Juan");
        cadete.setApellido("Perez");
        cadete.setEstado(libre);
        cadete.setTipoVehiculo(moto);
        cadete.setZonaActual(zona);
```

por:

```java
        cadete = new Cadete();
        cadete.setId("c1");
        cadete.setNombre("Juan");
        cadete.setApellido("Perez");
        cadete.setEstado(libre);
        cadete.setTipoVehiculo(moto);
        cadete.setZonaActual(zona);
        cadete.setLat(-26.8135);
        cadete.setLng(-65.2245);
```

Y en el helper `pedido(...)` (líneas 106-115), reemplazar:

```java
    private Pedido pedido(String id, Instant creadoEn) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setEstado(estadoPedido("PENDIENTE"));
        p.setZona(zona);
        p.setTipoVehiculoRequerido(moto);
        p.setCreadoEn(creadoEn);
        when(repo.findById(id)).thenReturn(Optional.of(p));
        return p;
    }
```

por:

```java
    private Pedido pedido(String id, Instant creadoEn) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setEstado(estadoPedido("PENDIENTE"));
        p.setZona(zona);
        p.setRequiereMoto(true);
        p.setOrigenLat(-26.8135);
        p.setOrigenLng(-65.2245);
        p.setCreadoEn(creadoEn);
        when(repo.findById(id)).thenReturn(Optional.of(p));
        return p;
    }
```

Y quitar, si el IDE marca sin uso, los imports `com.cadeteria.backend.repository.ZonaRepository` y `com.cadeteria.backend.repository.TipoVehiculoRepository` de este test — vía `import com.cadeteria.backend.repository.*;` esto ya está cubierto (el archivo usa el import con wildcard, no hace falta tocarlo).

- [ ] **Paso 10: Arreglar `PedidoServiceQuitarCadeteTest.java`**

Reemplazar (líneas 47-55):

```java
        service = new PedidoService(
                repo, cadeteRepo, mock(ZonaRepository.class), mock(TipoVehiculoRepository.class),
                estadoPedidoRepo, mock(ResultadoOfertaRepository.class), ofertaRepo,
                mock(EstadoCadeteRepository.class), mock(ConfiguracionService.class),
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), new AppProperties());
```

por:

```java
        service = new PedidoService(
                repo, cadeteRepo, estadoPedidoRepo, mock(ResultadoOfertaRepository.class), ofertaRepo,
                mock(EstadoCadeteRepository.class), mock(ConfiguracionService.class),
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), new AppProperties());
```

- [ ] **Paso 11: Compilar y correr toda la suite**

Run: `mvnw.cmd test` (desde `C:\proyectos\cadeteria\cadeteria`)
Expected: BUILD SUCCESS, todos los tests existentes en verde (no hay tests nuevos todavía, esta task es puramente estructural).

- [ ] **Paso 12: Commit**

```bash
git add src/main/java/com/cadeteria/backend/model/Pedido.java src/main/java/com/cadeteria/backend/model/SolicitudPedido.java src/main/java/com/cadeteria/backend/dto/PedidoDtos.java src/main/java/com/cadeteria/backend/dto/SolicitudPedidoDtos.java src/main/java/com/cadeteria/backend/service/PedidoService.java src/main/java/com/cadeteria/backend/service/SolicitudPedidoService.java src/main/java/com/cadeteria/backend/controller/SolicitudPedidoAdminController.java src/main/java/com/cadeteria/backend/config/PedidoZonaVehiculoSchemaFix.java src/test/java/com/cadeteria/backend/service/PedidoServiceReasignacionTest.java src/test/java/com/cadeteria/backend/service/PedidoServiceQuitarCadeteTest.java
git commit -m "Pedido/SolicitudPedido: zona opcional, tipoVehiculoRequerido -> requiereMoto"
```

---

### Tarea 2: Matching por distancia (buscarCandidato) + topes de BICI

**Archivos:**
- Modificar: `src/main/java/com/cadeteria/backend/service/PedidoService.java` (`buscarCandidato`, `viajeSuperaTopeDeBici`, `zonasCompatibles`)
- Crear: `src/test/java/com/cadeteria/backend/service/PedidoServiceMatchingPorDistanciaTest.java`

**Interfaces:**
- Consume: `Pedido.isRequiereMoto()` y el constructor de `PedidoService` de la Task 1.
- Produce: `PedidoService.sugerirCandidato(String pedidoId)` (ya existía, mismo nombre — ahora usa el matching nuevo). Configs nuevas: `distancia_maxima_bici_retiro_km`.

- [ ] **Paso 1: Escribir el test que falla**

Crear `src/test/java/com/cadeteria/backend/service/PedidoServiceMatchingPorDistanciaTest.java`:

```java
package com.cadeteria.backend.service;

import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.TipoVehiculo;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Matching 100% por distancia (sin zona) y topes de BICI, tras sacar el requisito de zona
 * y de tipo de vehículo del pedido — ver documentacion/spec-asignacion-por-distancia.md.
 */
class PedidoServiceMatchingPorDistanciaTest {

    private PedidoRepository repo;
    private CadeteRepository cadeteRepo;
    private OfertaPedidoRepository ofertaRepo;
    private EstadoPedidoRepository estadoPedidoRepo;
    private ConfiguracionService configuracionService;
    private PedidoService service;

    private TipoVehiculo moto;
    private TipoVehiculo bici;

    // Origen del pedido: Av. Mate de Luna 1800, San Miguel de Tucumán.
    private static final double ORIGEN_LAT = -26.8135;
    private static final double ORIGEN_LNG = -65.2245;
    // Yerba Buena — a varios km del origen.
    private static final double LEJOS_LAT = -26.8161;
    private static final double LEJOS_LNG = -65.3086;

    private static EstadoPedido estadoPedido(String id) {
        EstadoPedido e = new EstadoPedido();
        e.setId(id);
        return e;
    }

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        ofertaRepo = mock(OfertaPedidoRepository.class);
        estadoPedidoRepo = mock(EstadoPedidoRepository.class);
        configuracionService = mock(ConfiguracionService.class);

        service = new PedidoService(
                repo, cadeteRepo, estadoPedidoRepo, mock(ResultadoOfertaRepository.class), ofertaRepo,
                mock(EstadoCadeteRepository.class), configuracionService,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), new AppProperties());

        moto = new TipoVehiculo();
        moto.setId("MOTO");
        bici = new TipoVehiculo();
        bici.setId("BICI");

        when(ofertaRepo.findByPedidoId(any())).thenReturn(List.of());
        when(repo.findByCadeteAsignadoIdAndEstadoIdIn(any(), any())).thenReturn(List.of());
        when(configuracionService.getInt(any(), any(Integer.class))).thenAnswer(i -> i.getArgument(1));
        when(configuracionService.getBoolean(any(), any(Boolean.class))).thenAnswer(i -> i.getArgument(1));
        when(configuracionService.getBigDecimal(any(), any(BigDecimal.class))).thenAnswer(i -> i.getArgument(1));
    }

    private Cadete cadete(String id, TipoVehiculo tipo, double lat, double lng) {
        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");
        Cadete c = new Cadete();
        c.setId(id);
        c.setNombre(id);
        c.setApellido("Test");
        c.setEstado(libre);
        c.setTipoVehiculo(tipo);
        c.setLat(lat);
        c.setLng(lng);
        return c;
    }

    private Pedido pedido(boolean requiereMoto) {
        Pedido p = new Pedido();
        p.setId("p1");
        p.setEstado(estadoPedido("SIN_ASIGNAR"));
        p.setRequiereMoto(requiereMoto);
        p.setOrigenLat(ORIGEN_LAT);
        p.setOrigenLng(ORIGEN_LNG);
        p.setDestinoLat(ORIGEN_LAT);
        p.setDestinoLng(ORIGEN_LNG);
        when(repo.findById("p1")).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void bicicletaLejosDelRetiroQuedaAfueraPeroMotoEnElMismoLugarNo() {
        when(configuracionService.getBigDecimal("distancia_maxima_bici_retiro_km", BigDecimal.ZERO))
                .thenReturn(BigDecimal.valueOf(2));
        pedido(false);
        Cadete bicicletero = cadete("bici1", bici, LEJOS_LAT, LEJOS_LNG);
        Cadete motoquero = cadete("moto1", moto, LEJOS_LAT, LEJOS_LNG);
        when(cadeteRepo.findAll()).thenReturn(List.of(bicicletero, motoquero));

        Optional<Cadete> resultado = service.sugerirCandidato("p1");

        assertEquals("moto1", resultado.orElseThrow().getId(),
                "la bici queda afuera por el tope de retiro, la moto no tiene tope de distancia");
    }

    @Test
    void sinRequerirMotoUnaBiciDentroDelTopeEsCandidata() {
        when(configuracionService.getBigDecimal("distancia_maxima_bici_retiro_km", BigDecimal.ZERO))
                .thenReturn(BigDecimal.valueOf(2));
        pedido(false);
        Cadete bicicletero = cadete("bici1", bici, ORIGEN_LAT, ORIGEN_LNG);
        when(cadeteRepo.findAll()).thenReturn(List.of(bicicletero));

        Optional<Cadete> resultado = service.sugerirCandidato("p1");

        assertEquals("bici1", resultado.orElseThrow().getId());
    }

    @Test
    void requiereMotoDejaAfueraALasBicisSinImportarDistancia() {
        pedido(true);
        Cadete bicicletero = cadete("bici1", bici, ORIGEN_LAT, ORIGEN_LNG);
        when(cadeteRepo.findAll()).thenReturn(List.of(bicicletero));

        Optional<Cadete> resultado = service.sugerirCandidato("p1");

        assertTrue(resultado.isEmpty(), "sin motos disponibles, no hay candidato");
    }

    @Test
    void viajeLargoEnBiciNoBloqueaATodosSigueOfreciendoseAUnaMoto() {
        when(configuracionService.getBigDecimal("distancia_maxima_bici_km", BigDecimal.ZERO))
                .thenReturn(BigDecimal.valueOf(2));
        Pedido p = pedido(false);
        p.setDestinoLat(LEJOS_LAT);
        p.setDestinoLng(LEJOS_LNG);
        Cadete motoquero = cadete("moto1", moto, ORIGEN_LAT, ORIGEN_LNG);
        when(cadeteRepo.findAll()).thenReturn(List.of(motoquero));

        Optional<Cadete> resultado = service.sugerirCandidato("p1");

        assertEquals("moto1", resultado.orElseThrow().getId());
    }
}
```

- [ ] **Paso 2: Correr el test para ver que falla**

Run: `mvnw.cmd test -Dtest=PedidoServiceMatchingPorDistanciaTest`
Expected: FAIL — compila pero los asserts fallan (el matching viejo por zona/igualdad de vehículo todavía no fue reemplazado, así que ninguno de estos casos da lo esperado). Si no compila porque `PedidoService` ya no acepta este constructor, es porque la Task 1 no se aplicó — no seguir sin eso en verde.

- [ ] **Paso 3: Reescribir `buscarCandidato` y los topes de BICI en `PedidoService.java`**

Reemplazar el javadoc + `buscarCandidato` + `viajeSuperaTopeDeBici` completos (líneas 441-525):

```java
    /**
     * Primero busca candidato dentro de la zona del pedido (o una aledaña). Un cadete que
     * ya rechazó ESTE pedido puntual "max_rechazos_por_pedido" veces (default 3) queda
     * afuera de él — salvo que el pedido ya lleve "minutos_pedido_urgente_reintentar"
     * minutos (default 30) sin poder asignarse, en cuyo caso se ignora ese límite para que
     * un pedido que todos rechazan no quede flotando para siempre. Una oferta vencida sin
     * respuesta ("no lo vio") NO cuenta como rechazo ni lo excluye, pero sí lo manda al
     * final de la cola: se prueba primero con cadetes a los que nunca se les ofreció este
     * pedido. Tampoco se le ofrece a un cadete que ya tiene "asignacion_automatica_max_viajes_cadete"
     * viajes sin terminar encima (independiente de su propio tope maxViajesSimultaneos,
     * que es cuánto puede cargar él, no cuánto quiere darle de una el sistema).
     * <p>
     * Si nadie matchea zona (ej. todavía no hay zonas cargadas para donde está el cadete,
     * o el pedido cayó en una zona sin cadetes cerca), en vez de dejarlo sin nadie se lo
     * ofrece igual al elegible más cercano por GPS en línea recta al origen del pedido. Un
     * cadete sin ubicación cargada todavía no entra en este fallback porque no hay con qué
     * calcular la distancia.
     * <p>
     * Si el pedido requiere BICI y el viaje (origen→destino) supera {@code distancia_maxima_bici_km},
     * no se ofrece a nadie automáticamente — una bici no puede cubrir esa distancia. Es un
     * límite de la ASIGNACIÓN AUTOMÁTICA nomás; el admin puede seguir asignando a mano si le
     * parece razonable en el caso puntual.
     */
    private Optional<Cadete> buscarCandidato(Pedido pedido) {
        if (viajeSuperaTopeDeBici(pedido)) {
            return Optional.empty();
        }
        Set<String> zonasCompatibles = zonasCompatibles(pedido.getZona());
        List<OfertaPedido> ofertasPrevias = ofertaRepo.findByPedidoId(pedido.getId());
        Set<String> yaIntentados = ofertasPrevias.stream().map(o -> o.getCadete().getId()).collect(Collectors.toSet());
        Map<String, Long> rechazosPorCadete = ofertasPrevias.stream()
                .filter(o -> "RECHAZADO".equals(o.getResultado().getId()))
                .collect(Collectors.groupingBy(o -> o.getCadete().getId(), Collectors.counting()));

        int maxRechazos = configuracionService.getInt("max_rechazos_por_pedido", 3);
        int minutosUrgente = configuracionService.getInt("minutos_pedido_urgente_reintentar", 30);
        int maxViajesAsignacion = configuracionService.getInt("asignacion_automatica_max_viajes_cadete", 1);
        boolean pedidoUrgente = pedido.getCreadoEn().isBefore(Instant.now().minus(Duration.ofMinutes(minutosUrgente)));

        List<Cadete> elegibles = cadeteRepo.findAll().stream()
                .filter(Cadete::isActivo)
                .filter(c -> "LIBRE".equals(c.getEstado().getId()))
                .filter(this::puedeRecibirViajes)
                .filter(c -> pedidoUrgente || rechazosPorCadete.getOrDefault(c.getId(), 0L) < maxRechazos)
                .filter(c -> c.getTipoVehiculo().getId().equals(pedido.getTipoVehiculoRequerido().getId()))
                .filter(c -> dentroDeTopes(c, pedido))
                .filter(c -> cantidadPedidosPendientes(c) < maxViajesAsignacion)
                .filter(this::dentroDeTurno)
                .filter(c -> !incidenciaRepo.existsByCadeteIdAndPrioridadAndEstado(c.getId(), "GRAVE", "ABIERTA"))
                .toList();

        Comparator<Cadete> prioridad = Comparator
                .comparing((Cadete c) -> yaIntentados.contains(c.getId()))
                .thenComparing(this::tienePedidosPendientes);
        // Apagado por defecto: con esto prendido, entre cadetes libres igual de disponibles
        // se prioriza al que históricamente rechaza menos ofertas, en vez de solo el orden
        // FIFO de la cola de espera (pedido del dueño: en horas flojas, no siempre conviene
        // ofrecerle primero al que está hace más tiempo libre si suele rechazar casi todo).
        if (configuracionService.getBoolean("asignacion_prioriza_ranking_aceptacion", false)) {
            prioridad = prioridad.thenComparingDouble(this::tasaRechazoHistorica);
        }
        prioridad = prioridad.thenComparing(Cadete::getOrdenColaEspera);

        Optional<Cadete> enZona = elegibles.stream()
                .filter(c -> c.getZonaActual() != null && zonasCompatibles.contains(c.getZonaActual().getId()))
                .min(prioridad);
        if (enZona.isPresent()) return enZona;

        return elegibles.stream()
                .filter(c -> c.getLat() != null && c.getLng() != null)
                .min(Comparator.comparing((Cadete c) -> yaIntentados.contains(c.getId()))
                        .thenComparingDouble(c ->
                                GeocodingService.distanciaKm(c.getLat(), c.getLng(), pedido.getOrigenLat(), pedido.getOrigenLng()))
                        .thenComparing(this::tienePedidosPendientes)
                        .thenComparing(Cadete::getOrdenColaEspera));
    }

    private boolean viajeSuperaTopeDeBici(Pedido pedido) {
        if (!"BICI".equals(pedido.getTipoVehiculoRequerido().getId())) return false;
        BigDecimal topeKm = configuracionService.getBigDecimal("distancia_maxima_bici_km", BigDecimal.ZERO);
        if (topeKm.signum() <= 0) return false; // 0 o sin cargar = sin límite
        double distanciaViaje = GeocodingService.distanciaKm(
                pedido.getOrigenLat(), pedido.getOrigenLng(), pedido.getDestinoLat(), pedido.getDestinoLng());
        return distanciaViaje > topeKm.doubleValue();
    }
```

por:

```java
    /**
     * El elegible más cercano por GPS en línea recta al origen del pedido — sin zona, el
     * matching es 100% distancia. Un cadete que ya rechazó ESTE pedido puntual
     * "max_rechazos_por_pedido" veces (default 3) queda afuera de él — salvo que el pedido
     * ya lleve "minutos_pedido_urgente_reintentar" minutos (default 30) sin poder asignarse,
     * en cuyo caso se ignora ese límite para que un pedido que todos rechazan no quede
     * flotando para siempre. Una oferta vencida sin respuesta ("no lo vio") NO cuenta como
     * rechazo ni lo excluye, pero sí lo manda al final de la cola: se prueba primero con
     * cadetes a los que nunca se les ofreció este pedido. Tampoco se le ofrece a un cadete
     * que ya tiene "asignacion_automatica_max_viajes_cadete" viajes sin terminar encima
     * (independiente de su propio tope maxViajesSimultaneos, que es cuánto puede cargar él,
     * no cuánto quiere darle de una el sistema). Un cadete sin ubicación cargada no es
     * candidato — no hay con qué calcular la distancia.
     * <p>
     * Si {@code pedido.isRequiereMoto()}, solo entran cadetes en MOTO. Si no, entran motos y
     * bicis por igual, pero a una bici se la excluye si el viaje (origen→destino) supera
     * {@code distancia_maxima_bici_km}, o si su distancia actual hasta el origen supera
     * {@code distancia_maxima_bici_retiro_km} (0 en cualquiera de las dos = sin límite). Estos
     * dos topes son un límite de la ASIGNACIÓN AUTOMÁTICA nomás; el admin puede seguir
     * asignando a mano si le parece razonable en el caso puntual.
     */
    private Optional<Cadete> buscarCandidato(Pedido pedido) {
        List<OfertaPedido> ofertasPrevias = ofertaRepo.findByPedidoId(pedido.getId());
        Set<String> yaIntentados = ofertasPrevias.stream().map(o -> o.getCadete().getId()).collect(Collectors.toSet());
        Map<String, Long> rechazosPorCadete = ofertasPrevias.stream()
                .filter(o -> "RECHAZADO".equals(o.getResultado().getId()))
                .collect(Collectors.groupingBy(o -> o.getCadete().getId(), Collectors.counting()));

        int maxRechazos = configuracionService.getInt("max_rechazos_por_pedido", 3);
        int minutosUrgente = configuracionService.getInt("minutos_pedido_urgente_reintentar", 30);
        int maxViajesAsignacion = configuracionService.getInt("asignacion_automatica_max_viajes_cadete", 1);
        boolean pedidoUrgente = pedido.getCreadoEn().isBefore(Instant.now().minus(Duration.ofMinutes(minutosUrgente)));

        List<Cadete> elegibles = cadeteRepo.findAll().stream()
                .filter(Cadete::isActivo)
                .filter(c -> "LIBRE".equals(c.getEstado().getId()))
                .filter(this::puedeRecibirViajes)
                .filter(c -> pedidoUrgente || rechazosPorCadete.getOrDefault(c.getId(), 0L) < maxRechazos)
                .filter(c -> !pedido.isRequiereMoto() || "MOTO".equals(c.getTipoVehiculo().getId()))
                .filter(c -> !"BICI".equals(c.getTipoVehiculo().getId())
                        || (dentroDeTopeDeViajeBici(pedido) && dentroDeTopeDeRetiroBici(c, pedido)))
                .filter(c -> dentroDeTopes(c, pedido))
                .filter(c -> cantidadPedidosPendientes(c) < maxViajesAsignacion)
                .filter(this::dentroDeTurno)
                .filter(c -> !incidenciaRepo.existsByCadeteIdAndPrioridadAndEstado(c.getId(), "GRAVE", "ABIERTA"))
                .toList();

        Comparator<Cadete> prioridad = Comparator
                .comparing((Cadete c) -> yaIntentados.contains(c.getId()))
                .thenComparing(this::tienePedidosPendientes);
        // Apagado por defecto: con esto prendido, entre cadetes libres igual de disponibles
        // se prioriza al que históricamente rechaza menos ofertas, en vez de solo el orden
        // FIFO de la cola de espera (pedido del dueño: en horas flojas, no siempre conviene
        // ofrecerle primero al que está hace más tiempo libre si suele rechazar casi todo).
        if (configuracionService.getBoolean("asignacion_prioriza_ranking_aceptacion", false)) {
            prioridad = prioridad.thenComparingDouble(this::tasaRechazoHistorica);
        }
        prioridad = prioridad.thenComparing(Cadete::getOrdenColaEspera);

        return elegibles.stream()
                .filter(c -> c.getLat() != null && c.getLng() != null)
                .min(Comparator.comparing((Cadete c) -> yaIntentados.contains(c.getId()))
                        .thenComparingDouble(c ->
                                GeocodingService.distanciaKm(c.getLat(), c.getLng(), pedido.getOrigenLat(), pedido.getOrigenLng()))
                        .thenComparing(this::tienePedidosPendientes)
                        .thenComparing(Cadete::getOrdenColaEspera));
    }

    /** true si NO hay tope, o el viaje (origen→destino) no lo supera. Solo aplica a candidatos BICI. */
    private boolean dentroDeTopeDeViajeBici(Pedido pedido) {
        BigDecimal topeKm = configuracionService.getBigDecimal("distancia_maxima_bici_km", BigDecimal.ZERO);
        if (topeKm.signum() <= 0) return true; // 0 o sin cargar = sin límite
        double distanciaViaje = GeocodingService.distanciaKm(
                pedido.getOrigenLat(), pedido.getOrigenLng(), pedido.getDestinoLat(), pedido.getDestinoLng());
        return distanciaViaje <= topeKm.doubleValue();
    }

    /** true si NO hay tope, o la distancia del cadete al origen no lo supera. Solo aplica a candidatos BICI. */
    private boolean dentroDeTopeDeRetiroBici(Cadete cadete, Pedido pedido) {
        if (cadete.getLat() == null || cadete.getLng() == null) return false;
        BigDecimal topeKm = configuracionService.getBigDecimal("distancia_maxima_bici_retiro_km", BigDecimal.ZERO);
        if (topeKm.signum() <= 0) return true;
        double distancia = GeocodingService.distanciaKm(cadete.getLat(), cadete.getLng(), pedido.getOrigenLat(), pedido.getOrigenLng());
        return distancia <= topeKm.doubleValue();
    }
```

Y borrar el método `zonasCompatibles` (queda sin uso en esta clase — la Task 4 lo confirma buscando otros usos antes de tocar `asignarLote`):

```java
    private Set<String> zonasCompatibles(Zona zona) {
        Set<String> ids = zona.getZonasAledanas().stream().map(Zona::getId).collect(Collectors.toSet());
        ids.add(zona.getId());
        return ids;
    }
```

**No borrar todavía este método si la Task 3 (asignarLote) no se hizo antes** — `asignarLote` también lo usa. Si esta Task 2 se ejecuta antes que la Task 3, dejar `zonasCompatibles` en el archivo (con una advertencia de "unused" del lado de `buscarCandidato" es aceptable temporalmente) y borrarlo recién en la Task 3, que es quien saca su otro único uso.

- [ ] **Paso 4: Correr el test — debe pasar**

Run: `mvnw.cmd test -Dtest=PedidoServiceMatchingPorDistanciaTest`
Expected: PASS (4/4).

- [ ] **Paso 5: Correr toda la suite**

Run: `mvnw.cmd test`
Expected: BUILD SUCCESS.

- [ ] **Paso 6: Commit**

```bash
git add src/main/java/com/cadeteria/backend/service/PedidoService.java src/test/java/com/cadeteria/backend/service/PedidoServiceMatchingPorDistanciaTest.java
git commit -m "Matching de pedidos: 100% por distancia GPS + tope de BICI al retiro"
```

---

### Tarea 3: Asignación en lote por distancia (`asignarLote`)

**Archivos:**
- Modificar: `src/main/java/com/cadeteria/backend/service/PedidoService.java` (`asignarLote`, borrar `zonasCompatibles` si sigue en el archivo)
- Crear: `src/test/java/com/cadeteria/backend/service/PedidoServiceAsignarLoteTest.java`

**Interfaces:**
- Consume: `PedidoService` constructor y `Pedido.setRequiereMoto` de la Task 1.
- Produce: config nueva `distancia_maxima_lote_km` (default `3`), leída en `asignarLote`.

- [ ] **Paso 1: Escribir el test que falla**

Crear `src/test/java/com/cadeteria/backend/service/PedidoServiceAsignarLoteTest.java`:

```java
package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.config.AppProperties;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.EstadoCadete;
import com.cadeteria.backend.model.EstadoPedido;
import com.cadeteria.backend.model.Pedido;
import com.cadeteria.backend.model.ResultadoOferta;
import com.cadeteria.backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** `asignarLote` reemplaza el chequeo "misma zona" por cercanía entre orígenes (spec-asignacion-por-distancia.md). */
class PedidoServiceAsignarLoteTest {

    private PedidoRepository repo;
    private CadeteRepository cadeteRepo;
    private OfertaPedidoRepository ofertaRepo;
    private EstadoPedidoRepository estadoPedidoRepo;
    private ResultadoOfertaRepository resultadoOfertaRepo;
    private ConfiguracionService configuracionService;
    private PedidoService service;

    private static EstadoPedido estadoPedido(String id) {
        EstadoPedido e = new EstadoPedido();
        e.setId(id);
        return e;
    }

    private static ResultadoOferta resultadoOferta(String id) {
        ResultadoOferta r = new ResultadoOferta();
        r.setId(id);
        return r;
    }

    @BeforeEach
    void setUp() {
        repo = mock(PedidoRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        ofertaRepo = mock(OfertaPedidoRepository.class);
        estadoPedidoRepo = mock(EstadoPedidoRepository.class);
        resultadoOfertaRepo = mock(ResultadoOfertaRepository.class);
        configuracionService = mock(ConfiguracionService.class);

        service = new PedidoService(
                repo, cadeteRepo, estadoPedidoRepo, resultadoOfertaRepo, ofertaRepo,
                mock(EstadoCadeteRepository.class), configuracionService,
                mock(WebSocketPublisher.class), mock(FcmService.class), mock(SmsGatewayService.class),
                mock(PedidoUbicacionRepository.class), mock(PedidoComentarioRepository.class),
                mock(PedidoPrecioLogRepository.class), mock(WebPushService.class),
                mock(PedidoParadaRepository.class), mock(MovimientoCreditoRepository.class),
                mock(IncidenciaRepository.class), new AppProperties());

        EstadoCadete libre = new EstadoCadete();
        libre.setId("LIBRE");
        Cadete cadete = new Cadete();
        cadete.setId("c1");
        cadete.setEstado(libre);

        when(cadeteRepo.findById("c1")).thenReturn(Optional.of(cadete));
        when(estadoPedidoRepo.findById("PENDIENTE")).thenReturn(Optional.of(estadoPedido("PENDIENTE")));
        when(resultadoOfertaRepo.findById("PENDIENTE")).thenReturn(Optional.of(resultadoOferta("PENDIENTE")));
        when(repo.save(any(Pedido.class))).thenAnswer(i -> i.getArgument(0));
        when(repo.findByCadeteAsignadoIdAndEstadoIdIn(any(), any())).thenReturn(List.of());
        when(configuracionService.getInt(any(), any(Integer.class))).thenAnswer(i -> i.getArgument(1));
        when(configuracionService.getBigDecimal("distancia_maxima_lote_km", BigDecimal.valueOf(3)))
                .thenReturn(BigDecimal.valueOf(3));
    }

    private Pedido pedido(String id, double origenLat, double origenLng) {
        Pedido p = new Pedido();
        p.setId(id);
        p.setNumero(1L);
        p.setEstado(estadoPedido("SIN_ASIGNAR"));
        p.setOrigenLat(origenLat);
        p.setOrigenLng(origenLng);
        p.setMontoDeclarado(BigDecimal.ZERO);
        when(repo.findById(id)).thenReturn(Optional.of(p));
        return p;
    }

    @Test
    void rechazaElLoteSiLosOrigenesEstanLejosEntreSi() {
        pedido("p1", -26.8135, -65.2245);
        pedido("p2", -26.8161, -65.3086); // varios km del anterior

        assertThrows(BadRequestException.class, () -> service.asignarLote(List.of("p1", "p2"), "c1", "admin"));
    }

    @Test
    void aceptaElLoteSiLosOrigenesEstanCerca() {
        Pedido p1 = pedido("p1", -26.8135, -65.2245);
        Pedido p2 = pedido("p2", -26.8140, -65.2250); // pocos metros del anterior

        List<Pedido> resultado = service.asignarLote(List.of("p1", "p2"), "c1", "admin");

        assertEquals(2, resultado.size());
        assertEquals("PENDIENTE", p1.getEstado().getId());
        assertEquals("PENDIENTE", p2.getEstado().getId());
    }
}
```

- [ ] **Paso 2: Correr el test para ver que falla**

Run: `mvnw.cmd test -Dtest=PedidoServiceAsignarLoteTest`
Expected: FAIL — `asignarLote` todavía llama a `pedidos.get(0).getZona()`/`p.getZona()`, que no tiene nada que ver con las coordenadas de este test (y puede tirar NPE si `zona` es null en los pedidos de prueba, ya que no se está seteando).

- [ ] **Paso 3: Reescribir `asignarLote` en `PedidoService.java`**

Reemplazar (líneas 707-716):

```java
        List<Pedido> pedidos = pedidoIds.stream().map(this::get).toList();
        Set<String> zonasCompatibles = zonasCompatibles(pedidos.get(0).getZona());
        for (Pedido p : pedidos) {
            if (!"SIN_ASIGNAR".equals(p.getEstado().getId())) {
                throw new BadRequestException("El pedido #" + p.getNumero() + " ya tiene una asignacion en curso.");
            }
            if (!zonasCompatibles.contains(p.getZona().getId())) {
                throw new BadRequestException("Todos los pedidos del lote tienen que ser de la misma zona (o zonas aledañas).");
            }
        }
```

por:

```java
        List<Pedido> pedidos = pedidoIds.stream().map(this::get).toList();
        BigDecimal topeLoteKm = configuracionService.getBigDecimal("distancia_maxima_lote_km", BigDecimal.valueOf(3));
        Pedido primero = pedidos.get(0);
        for (Pedido p : pedidos) {
            if (!"SIN_ASIGNAR".equals(p.getEstado().getId())) {
                throw new BadRequestException("El pedido #" + p.getNumero() + " ya tiene una asignacion en curso.");
            }
            double distancia = GeocodingService.distanciaKm(
                    primero.getOrigenLat(), primero.getOrigenLng(), p.getOrigenLat(), p.getOrigenLng());
            if (distancia > topeLoteKm.doubleValue()) {
                throw new BadRequestException("Todos los pedidos del lote tienen que tener orígenes cercanos entre sí.");
            }
        }
```

Y actualizar el javadoc del método (líneas 688-694), de:

```java
    /**
     * Agrupar pedidos de la misma zona (o zonas aledañas) en una sola oferta a un cadete
     * (ronda 4, punto 61) — para repartos que van al mismo lado, en vez de asignarlos uno
     * por uno. Reusa `ofertar()` pedido por pedido (el cadete sigue viendo/aceptando cada
     * uno por separado en la app, sin ningún cambio ahí); requiere que el cadete tenga
     * "Máx. viajes simultáneos" configurado en 2 o más si el lote tiene más de un pedido.
     */
```

a:

```java
    /**
     * Agrupar pedidos con orígenes cercanos entre sí (dentro de "distancia_maxima_lote_km",
     * default 3) en una sola oferta a un cadete (ronda 4, punto 61) — para repartos que van
     * al mismo lado, en vez de asignarlos uno por uno. Reusa `ofertar()` pedido por pedido
     * (el cadete sigue viendo/aceptando cada uno por separado en la app, sin ningún cambio
     * ahí); requiere que el cadete tenga "Máx. viajes simultáneos" configurado en 2 o más si
     * el lote tiene más de un pedido.
     */
```

Si el método `zonasCompatibles` seguía en el archivo desde la Task 2, borrarlo ahora — ya no le queda ningún uso:

```java
    private Set<String> zonasCompatibles(Zona zona) {
        Set<String> ids = zona.getZonasAledanas().stream().map(Zona::getId).collect(Collectors.toSet());
        ids.add(zona.getId());
        return ids;
    }
```

Y si el import `com.cadeteria.backend.model.Zona` (línea 18) o `java.util.Set` quedan sin uso en el archivo, sacarlos (`Set` probablemente lo sigan usando `zonasCompatibles` de otros métodos si quedara alguno — revisar con el propio compilador en el Paso 4; si `mvnw.cmd test` no marca error por import sin usar, no hace falta tocarlo, Java no falla por imports sin uso, solo el linter podría advertir).

- [ ] **Paso 4: Correr el test — debe pasar**

Run: `mvnw.cmd test -Dtest=PedidoServiceAsignarLoteTest`
Expected: PASS (2/2).

- [ ] **Paso 5: Correr toda la suite**

Run: `mvnw.cmd test`
Expected: BUILD SUCCESS.

- [ ] **Paso 6: Commit**

```bash
git add src/main/java/com/cadeteria/backend/service/PedidoService.java src/test/java/com/cadeteria/backend/service/PedidoServiceAsignarLoteTest.java
git commit -m "asignarLote: agrupar por cercania de origenes en vez de zona"
```

---

### Tarea 4: Seeders de demo (`DemoPedidoSeeder`, `DemoExtrasSeeder`)

**Archivos:**
- Modificar: `src/main/java/com/cadeteria/backend/config/DemoPedidoSeeder.java`
- Modificar: `src/main/java/com/cadeteria/backend/config/DemoExtrasSeeder.java`

**Interfaces:**
- Consume: `Pedido.setRequiereMoto(boolean)` y `SolicitudPedido.setRequiereMoto(boolean)` de la Task 1.

- [ ] **Paso 1: Editar `DemoPedidoSeeder.java`**

En el método `base(...)` (línea 256), reemplazar:

```java
        p.setZona(zona);
        p.setTipoVehiculoRequerido(tipo);
```

por:

```java
        p.setZona(zona);
        p.setRequiereMoto("MOTO".equals(tipo.getId()));
```

- [ ] **Paso 2: Editar `DemoExtrasSeeder.java`**

En `seedSolicitudesPedido(...)` (líneas 222-223), reemplazar:

```java
        cotizada.setZona(zona);
        cotizada.setTipoVehiculoRequerido(moto);
```

por:

```java
        cotizada.setRequiereMoto(true);
```

(El parámetro `zona` del método `seedSolicitudesPedido(Zona zona, TipoVehiculo moto, Instant ahora)` queda sin uso — es aceptable dejarlo así, Java no falla por parámetros sin usar; no vale la pena encadenar la limpieza de `zonaRepo`/`zonaOpt` en este seeder de demo para esta tarea.)

- [ ] **Paso 3: Verificar que compila**

Run: `mvnw.cmd compile`
Expected: BUILD SUCCESS.

- [ ] **Paso 4: Levantar el backend en modo demo y verificar manualmente**

Run: `mvnw.cmd spring-boot:run` (con `app.demo.enabled=true`, el default de `application-local.yml` si existe, o pasar `-Dspring-boot.run.arguments=--app.demo.enabled=true`)
Expected: en el log, "Demo: chat, solicitudes, incidencia y pagos semanales sembrados." sin excepciones; parar el proceso después de confirmarlo (Ctrl+C).

- [ ] **Paso 5: Commit**

```bash
git add src/main/java/com/cadeteria/backend/config/DemoPedidoSeeder.java src/main/java/com/cadeteria/backend/config/DemoExtrasSeeder.java
git commit -m "Seeders de demo: adaptar a requiereMoto"
```

---

### Tarea 5: Front — modelos + panel de Configuración (nuevos topes)

**Archivos:**
- Modificar: `admin-front/src/app/core/models/pedido.model.ts`
- Modificar: `admin-front/src/app/core/models/solicitud-pedido.model.ts`
- Modificar: `admin-front/src/app/features/configuracion/configuracion.component.ts`

**Interfaces:**
- Produce: `Pedido.requiereMoto: boolean` (reemplaza `zona`/`tipoVehiculoRequerido`), `PedidoInput.requiereMoto: boolean` (reemplaza `zonaId`/`tipoVehiculoRequeridoId`), `SolicitudPedido.requiereMoto: boolean` (reemplaza `zona`/`tipoVehiculoRequerido`), `RevisarSolicitudInput.requiereMoto: boolean` (reemplaza `zonaId`/`tipoVehiculoRequeridoId`). Estas interfaces las consumen las Tasks 6, 7 y 8.

- [ ] **Paso 1: Editar `pedido.model.ts`**

Reemplazar (líneas 25-26):

```ts
  zona: Lookup;
  tipoVehiculoRequerido: Lookup;
```

por:

```ts
  requiereMoto: boolean;
```

Reemplazar (líneas 115-116):

```ts
  zonaId: string;
  tipoVehiculoRequeridoId: string;
```

por:

```ts
  requiereMoto: boolean;
```

- [ ] **Paso 2: Editar `solicitud-pedido.model.ts`**

Reemplazar (líneas 20-21):

```ts
  zona: Lookup | null;
  tipoVehiculoRequerido: Lookup | null;
```

por:

```ts
  requiereMoto: boolean;
```

Reemplazar (líneas 45-49):

```ts
export interface RevisarSolicitudInput {
  zonaId: string;
  tipoVehiculoRequeridoId: string;
  precio: number;
  montoDeclarado: number | null;
}
```

por:

```ts
export interface RevisarSolicitudInput {
  requiereMoto: boolean;
  precio: number;
  montoDeclarado: number | null;
}
```

Si el import `{ Lookup }` (línea 1) queda sin uso en este archivo, sacarlo (revisar con el build del Paso 5 — TypeScript sí marca error por imports sin usar según la config del proyecto).

- [ ] **Paso 3: Editar `configuracion.component.ts` — plantilla**

Reemplazar el bloque del campo de BICI existente (líneas 145-153):

```html
              <label class="flex flex-col gap-1">
                <span class="text-sm font-medium text-gray-700">Distancia máxima de viaje para BICI (km)</span>
                <input type="number" min="0" step="0.5" class="input" [(ngModel)]="distanciaMaximaBiciKm" name="distanciaMaximaBiciKm" placeholder="0 = sin límite" />
                <span class="text-xs text-gray-400">
                  Si el pedido pide BICI y el viaje (origen→destino) supera esta distancia, la asignación
                  automática/sugerida no se lo ofrece a nadie — 0 = sin límite. Vos podés seguir asignando a mano
                  igual si te parece razonable en un caso puntual.
                </span>
              </label>
```

por:

```html
              <label class="flex flex-col gap-1">
                <span class="text-sm font-medium text-gray-700">Distancia máxima de viaje para BICI (km)</span>
                <input type="number" min="0" step="0.5" class="input" [(ngModel)]="distanciaMaximaBiciKm" name="distanciaMaximaBiciKm" placeholder="0 = sin límite" />
                <span class="text-xs text-gray-400">
                  Si un cadete en BICI es candidato y el viaje (origen→destino) supera esta distancia, la
                  asignación automática/sugerida no se lo ofrece a él (sí puede seguir ofreciéndose a una moto) —
                  0 = sin límite. Vos podés seguir asignando a mano igual si te parece razonable en un caso puntual.
                </span>
              </label>
              <label class="flex flex-col gap-1">
                <span class="text-sm font-medium text-gray-700">Distancia máxima del cadete al retiro (BICI, km)</span>
                <input type="number" min="0" step="0.5" class="input" [(ngModel)]="distanciaMaximaBiciRetiroKm" name="distanciaMaximaBiciRetiroKm" placeholder="0 = sin límite" />
                <span class="text-xs text-gray-400">
                  Si un cadete en BICI está más lejos que esto del punto de retiro del pedido, la asignación
                  automática/sugerida no se lo ofrece — 0 = sin límite.
                </span>
              </label>
              <label class="flex flex-col gap-1">
                <span class="text-sm font-medium text-gray-700">Distancia máxima entre orígenes para agrupar en un lote (km)</span>
                <input type="number" min="0" step="0.5" class="input" [(ngModel)]="distanciaMaximaLoteKm" name="distanciaMaximaLoteKm" />
                <span class="text-xs text-gray-400">
                  Al agrupar varios pedidos en una sola tanda de ofertas a un cadete ("Asignar viaje" desde
                  Cadetes libres), todos los orígenes tienen que estar a esta distancia o menos entre sí.
                </span>
              </label>
```

- [ ] **Paso 4: Editar `configuracion.component.ts` — campos, lectura y guardado**

Reemplazar (línea 718):

```ts
  distanciaMaximaBiciKm: number | null = null;
```

por:

```ts
  distanciaMaximaBiciKm: number | null = null;
  distanciaMaximaBiciRetiroKm: number | null = null;
  distanciaMaximaLoteKm: number | null = null;
```

Reemplazar (línea 777):

```ts
      this.distanciaMaximaBiciKm = Number(v['distancia_maxima_bici_km'] ?? 0);
```

por:

```ts
      this.distanciaMaximaBiciKm = Number(v['distancia_maxima_bici_km'] ?? 0);
      this.distanciaMaximaBiciRetiroKm = Number(v['distancia_maxima_bici_retiro_km'] ?? 0);
      this.distanciaMaximaLoteKm = Number(v['distancia_maxima_lote_km'] ?? 3);
```

Reemplazar (línea 882):

```ts
    agregarSiCambio('distancia_maxima_bici_km', String(this.distanciaMaximaBiciKm ?? 0));
```

por:

```ts
    agregarSiCambio('distancia_maxima_bici_km', String(this.distanciaMaximaBiciKm ?? 0));
    agregarSiCambio('distancia_maxima_bici_retiro_km', String(this.distanciaMaximaBiciRetiroKm ?? 0));
    agregarSiCambio('distancia_maxima_lote_km', String(this.distanciaMaximaLoteKm ?? 3));
```

- [ ] **Paso 5: Build**

Run: `npm run build` (desde `C:\proyectos\cadeteria\admin-front`)
Expected: BUILD SUCCESS, sin errores de tipos (nada más referencia `zona`/`tipoVehiculoRequerido` de `Pedido`/`SolicitudPedido` todavía — eso lo rompe intencionalmente esta task; se arregla en las Tasks 6-8. Si preferís un build verde en cada task, hacer las Tasks 6-8 antes del `npm run build` final de esta task — el orden entre Task 5 y 6-8 no importa funcionalmente, son archivos disjuntos).

- [ ] **Paso 6: Commit**

```bash
git add admin-front/src/app/core/models/pedido.model.ts admin-front/src/app/core/models/solicitud-pedido.model.ts admin-front/src/app/features/configuracion/configuracion.component.ts
git commit -m "Front: modelos de Pedido/SolicitudPedido a requiereMoto + topes nuevos en Configuracion"
```

---

### Tarea 6: Front — `nuevo-pedido.component.ts` (alta directa del admin)

**Archivos:**
- Modificar: `admin-front/src/app/features/pedidos/nuevo-pedido.component.ts`

**Interfaces:**
- Consume: `PedidoInput.requiereMoto: boolean` de la Task 5.

- [ ] **Paso 1: Quitar el import y la inyección de `ZonaService`**

Quitar (línea 7):

```ts
import { ZonaService } from '../../core/services/zona.service';
```

Quitar (línea 256):

```ts
  readonly zonas = inject(ZonaService);
```

- [ ] **Paso 2: Reemplazar el combo de Zona y el de Tipo de vehículo por un checkbox**

Reemplazar (líneas 163-183):

```html
          <div class="grid sm:grid-cols-4 gap-4">
            <label class="flex flex-col gap-1">
              <span class="text-sm font-medium text-gray-700">Zona</span>
              <select class="input" [ngModel]="zonaId" (ngModelChange)="onZonaChange($event)" name="zonaId">
                <option [ngValue]="null" disabled>Elegir…</option>
                @for (z of zonas.zonas(); track z.id) {
                  @if (z.activo) {
                    <option [ngValue]="z.id">{{ z.nombre }}</option>
                  }
                }
              </select>
            </label>
            <label class="flex flex-col gap-1">
              <span class="text-sm font-medium text-gray-700">Tipo de vehículo</span>
              <select class="input" [(ngModel)]="tipoVehiculoRequeridoId" name="tipoVehiculoRequeridoId">
                <option [ngValue]="null" disabled>Elegir…</option>
                @for (t of lookups.tiposVehiculo(); track t.id) {
                  <option [ngValue]="t.id">{{ t.nombre }}</option>
                }
              </select>
            </label>
```

por:

```html
          <div class="grid sm:grid-cols-4 gap-4">
            <label class="flex items-center gap-2 mt-6">
              <input type="checkbox" [(ngModel)]="requiereMoto" name="requiereMoto" />
              <span class="text-sm font-medium text-gray-700">Requiere moto</span>
            </label>
```

- [ ] **Paso 3: Reemplazar el campo `zonaId`/`tipoVehiculoRequeridoId` por `requiereMoto`**

Reemplazar (líneas 278-279):

```ts
  zonaId: string | null = null;
  tipoVehiculoRequeridoId: string | null = null;
```

por:

```ts
  requiereMoto = false;
```

- [ ] **Paso 4: Quitar `zonas.ensureLoaded()` y `onZonaChange`**

Quitar de `ngOnInit` (línea 341):

```ts
    this.zonas.ensureLoaded();
```

Quitar el método completo `onZonaChange` (líneas 405-413):

```ts
  onZonaChange(zonaId: string | null): void {
    this.zonaId = zonaId;
    if (this.precio != null) return;
    const zona = this.zonas.zonas().find((z) => z.id === zonaId);
    if (zona?.tarifaSugerida != null) {
      this.precio = zona.tarifaSugerida;
      this.precioSugeridoInfo = `Sugerido por zona (${zona.nombre})`;
    }
  }
```

- [ ] **Paso 5: Simplificar la sugerencia de precio por cotización (ya no guarda `zonaId`)**

Reemplazar dentro de `sugerirPrecio()` (líneas 441-449):

```ts
      .subscribe((c) => {
        if ((this.precio != null && this.precioSugeridoInfo == null) || c.precioSugerido == null) return;
        this.precio = c.precioSugerido;
        if (c.metodo === 'ZONA' && c.zonaId) {
          if (!this.zonaId) this.zonaId = c.zonaId;
          this.precioSugeridoInfo = `Sugerido por zona (${c.zonaNombre})`;
        } else {
          this.precioSugeridoInfo = `Sugerido por distancia (~${c.distanciaKm?.toFixed(1)} km)`;
        }
      });
```

por:

```ts
      .subscribe((c) => {
        if ((this.precio != null && this.precioSugeridoInfo == null) || c.precioSugerido == null) return;
        this.precio = c.precioSugerido;
        this.precioSugeridoInfo =
          c.metodo === 'ZONA' ? `Sugerido por zona (${c.zonaNombre})` : `Sugerido por distancia (~${c.distanciaKm?.toFixed(1)} km)`;
      });
```

- [ ] **Paso 6: Actualizar la validación y el payload de `guardar()`**

Reemplazar (líneas 480-483):

```ts
    if (!this.zonaId || !this.tipoVehiculoRequeridoId) {
      this.error.set('Elegí la zona y el tipo de vehículo.');
      return;
    }
```

Borrar este bloque entero (ya no hay nada obligatorio que validar acá).

Reemplazar dentro del objeto `input` (líneas 516-517):

```ts
      zonaId: this.zonaId,
      tipoVehiculoRequeridoId: this.tipoVehiculoRequeridoId,
```

por:

```ts
      requiereMoto: this.requiereMoto,
```

- [ ] **Paso 7: Actualizar los dos reseteos de formulario**

Reemplazar en `resetearFormularioMismoOrigen()` (líneas 546-547):

```ts
    this.zonaId = null;
    this.tipoVehiculoRequeridoId = null;
```

por:

```ts
    this.requiereMoto = false;
```

`resetearFormulario()` (reseteo completo, líneas 556-573) no tocaba `zonaId`/`tipoVehiculoRequeridoId` (a propósito, para no perderlos entre pedidos seguidos) — dejar `requiereMoto` fuera de este método también, sin agregar nada ahí, para mantener el mismo comportamiento.

- [ ] **Paso 8: Build**

Run: `npm run build`
Expected: BUILD SUCCESS.

- [ ] **Paso 9: Commit**

```bash
git add admin-front/src/app/features/pedidos/nuevo-pedido.component.ts
git commit -m "Front: alta de pedido sin zona, tipo de vehiculo como checkbox opcional"
```

---

### Tarea 7: Front — `solicitudes-pedido.component.ts` (revisión admin de "/pedir")

**Archivos:**
- Modificar: `admin-front/src/app/features/pedir/solicitudes-pedido.component.ts`

**Interfaces:**
- Consume: `RevisarSolicitudInput.requiereMoto: boolean` de la Task 5.

- [ ] **Paso 1: Quitar el import y la inyección de `ZonaService`**

Quitar (línea 6):

```ts
import { ZonaService } from '../../core/services/zona.service';
```

Quitar (línea 204):

```ts
  readonly zonas = inject(ZonaService);
```

- [ ] **Paso 2: Reemplazar el combo de Zona y el de Vehículo por un checkbox**

Reemplazar (líneas 77-97):

```html
                    <label class="flex flex-col gap-1">
                      <span class="text-xs font-medium text-gray-700">Zona</span>
                      <select class="input" [(ngModel)]="zonaSeleccionada[s.id]" [name]="'zona-' + s.id">
                        <option [ngValue]="null" disabled>Elegir…</option>
                        @for (z of zonas.zonas(); track z.id) {
                          @if (z.activo) {
                            <option [ngValue]="z.id">{{ z.nombre }}</option>
                          }
                        }
                      </select>
                    </label>
                    <label class="flex flex-col gap-1">
                      <span class="text-xs font-medium text-gray-700">Vehículo</span>
                      <select class="input" [(ngModel)]="vehiculoSeleccionado[s.id]" [name]="'vehiculo-' + s.id">
                        <option [ngValue]="null" disabled>Elegir…</option>
                        @for (t of lookups.tiposVehiculo(); track t.id) {
                          <option [ngValue]="t.id">{{ t.nombre }}</option>
                        }
                      </select>
                    </label>
```

por:

```html
                    <label class="flex items-center gap-2">
                      <input type="checkbox" [(ngModel)]="requiereMotoSeleccionado[s.id]" [name]="'requiereMoto-' + s.id" />
                      <span class="text-xs font-medium text-gray-700">Requiere moto</span>
                    </label>
```

- [ ] **Paso 3: Reemplazar los campos por-solicitud**

Reemplazar (líneas 214-215):

```ts
  zonaSeleccionada: Record<string, string | null> = {};
  vehiculoSeleccionado: Record<string, string | null> = {};
```

por:

```ts
  requiereMotoSeleccionado: Record<string, boolean> = {};
```

- [ ] **Paso 4: Quitar `zonas.ensureLoaded()`**

Reemplazar `ngOnInit` (líneas 222-226):

```ts
  ngOnInit(): void {
    this.zonas.ensureLoaded();
    this.lookups.ensureLoaded();
    this.service.listar(this.filtroActual() ?? undefined);
  }
```

por:

```ts
  ngOnInit(): void {
    this.lookups.ensureLoaded();
    this.service.listar(this.filtroActual() ?? undefined);
  }
```

- [ ] **Paso 5: Simplificar `datosValidos` y los payloads de `confirmarDirecto`/`cotizar`**

Reemplazar (línea 245-247):

```ts
  private datosValidos(s: SolicitudPedido): boolean {
    return !!this.zonaSeleccionada[s.id] && !!this.vehiculoSeleccionado[s.id] && !!this.precioModal[s.id];
  }
```

por:

```ts
  private datosValidos(s: SolicitudPedido): boolean {
    return !!this.precioModal[s.id];
  }
```

Reemplazar dentro de `confirmarDirecto(s)` (líneas 249-267):

```ts
  confirmarDirecto(s: SolicitudPedido): void {
    if (!this.datosValidos(s)) {
      this.toast.error('Elegí zona, vehículo y precio antes de confirmar.');
      return;
    }
    this.service.confirmarDirecto(
      s.id,
      {
        zonaId: this.zonaSeleccionada[s.id]!,
        tipoVehiculoRequeridoId: this.vehiculoSeleccionado[s.id]!,
        precio: this.precioModal[s.id]!,
        montoDeclarado: this.montoModal[s.id] ?? null,
      },
      () => {
        this.toast.success(`Pedido confirmado — se le avisó a ${s.clienteNombre} por SMS.`);
        this.filtrar(this.filtroActual());
      },
    );
  }
```

por:

```ts
  confirmarDirecto(s: SolicitudPedido): void {
    if (!this.datosValidos(s)) {
      this.toast.error('Ingresá el precio antes de confirmar.');
      return;
    }
    this.service.confirmarDirecto(
      s.id,
      {
        requiereMoto: this.requiereMotoSeleccionado[s.id] ?? false,
        precio: this.precioModal[s.id]!,
        montoDeclarado: this.montoModal[s.id] ?? null,
      },
      () => {
        this.toast.success(`Pedido confirmado — se le avisó a ${s.clienteNombre} por SMS.`);
        this.filtrar(this.filtroActual());
      },
    );
  }
```

Reemplazar dentro de `sugerirPrecio(s)` (líneas 270-284):

```ts
  sugerirPrecio(s: SolicitudPedido): void {
    this.cotizacion.cotizar(s.origenLat, s.origenLng, s.destinoLat, s.destinoLng, this.montoModal[s.id]).subscribe((c) => {
      if (c.precioSugerido == null) {
        this.toast.error('No hay zona con precio cargado ni "precio por km" configurado — cargalo a mano.');
        return;
      }
      this.precioModal[s.id] = c.precioSugerido;
      if (c.metodo === 'ZONA' && c.zonaId && !this.zonaSeleccionada[s.id]) {
        this.zonaSeleccionada[s.id] = c.zonaId;
      }
      this.toast.success(
        c.metodo === 'ZONA' ? `Sugerido por zona (${c.zonaNombre}).` : `Sugerido por distancia (~${c.distanciaKm?.toFixed(1)} km).`,
      );
    });
  }
```

por:

```ts
  sugerirPrecio(s: SolicitudPedido): void {
    this.cotizacion.cotizar(s.origenLat, s.origenLng, s.destinoLat, s.destinoLng, this.montoModal[s.id]).subscribe((c) => {
      if (c.precioSugerido == null) {
        this.toast.error('No hay zona con precio cargado ni "precio por km" configurado — cargalo a mano.');
        return;
      }
      this.precioModal[s.id] = c.precioSugerido;
      this.toast.success(
        c.metodo === 'ZONA' ? `Sugerido por zona (${c.zonaNombre}).` : `Sugerido por distancia (~${c.distanciaKm?.toFixed(1)} km).`,
      );
    });
  }
```

Reemplazar dentro de `cotizar(s)` (líneas 286-299, mismo cambio de payload que `confirmarDirecto`):

```ts
  cotizar(s: SolicitudPedido): void {
    if (!this.datosValidos(s)) {
      this.toast.error('Elegí zona, vehículo y precio antes de cotizar.');
      return;
    }
    this.service.cotizar(
      s.id,
      {
        zonaId: this.zonaSeleccionada[s.id]!,
        tipoVehiculoRequeridoId: this.vehiculoSeleccionado[s.id]!,
        precio: this.precioModal[s.id]!,
        montoDeclarado: this.montoModal[s.id] ?? null,
      },
```

por:

```ts
  cotizar(s: SolicitudPedido): void {
    if (!this.datosValidos(s)) {
      this.toast.error('Ingresá el precio antes de cotizar.');
      return;
    }
    this.service.cotizar(
      s.id,
      {
        requiereMoto: this.requiereMotoSeleccionado[s.id] ?? false,
        precio: this.precioModal[s.id]!,
        montoDeclarado: this.montoModal[s.id] ?? null,
      },
```

(el resto del método, con el callback de éxito después de la llave de cierre, no cambia).

- [ ] **Paso 6: Build**

Run: `npm run build`
Expected: BUILD SUCCESS.

- [ ] **Paso 7: Commit**

```bash
git add admin-front/src/app/features/pedir/solicitudes-pedido.component.ts
git commit -m "Front: revision de solicitudes sin zona, vehiculo como checkbox opcional"
```

---

### Tarea 8: Front — `dashboard.component.ts` (visualización + lote por distancia)

**Archivos:**
- Modificar: `admin-front/src/app/features/dashboard/dashboard.component.ts`

**Interfaces:**
- Consume: `Pedido.requiereMoto: boolean` de la Task 5.

- [ ] **Paso 1: Inyectar `ConfiguracionService`**

Agregar el import junto a los demás servicios (después de la línea 12, junto a `import { ToastService } from '../../core/services/toast.service';`):

```ts
import { ConfiguracionService } from '../../core/services/configuracion.service';
```

Agregar la inyección junto a las demás (después de la línea 952, `private readonly route = inject(ActivatedRoute);`):

```ts
  private readonly config = inject(ConfiguracionService);
```

Agregar `this.config.ensureLoaded();` al principio de `ngOnInit()` (línea 1134, junto a `this.cadetesSvc.ensureLoaded();`).

- [ ] **Paso 2: Tarjeta de sugerencia de candidato (líneas 266-281)**

Reemplazar:

```html
            <p class="text-sm text-gray-600">
              {{ p.origenDireccion }} → {{ p.destinoDireccion }} · Zona {{ p.zona.nombre }} ·
              {{ p.tipoVehiculoRequerido.nombre }}
            </p>
            @if (buscandoSugerencia()) {
              <p class="text-xs text-gray-400">Buscando un candidato sugerido…</p>
            } @else if (sugerido()) {
              <p class="text-xs text-emerald-700">
                Sugerido por el sistema: {{ sugerido()!.nombre }} {{ sugerido()!.apellido }} (el primero libre de esa
                zona/vehículo). Podés confirmarlo o elegir otro cadete abajo.
              </p>
            } @else {
              <p class="text-xs text-amber-700">
                El sistema no encontró ningún cadete libre que matchee zona y vehículo — elegí uno a mano.
              </p>
            }
```

por:

```html
            <p class="text-sm text-gray-600">
              {{ p.origenDireccion }} → {{ p.destinoDireccion }} ·
              {{ p.requiereMoto ? 'Requiere moto' : 'Cualquier vehículo' }}
            </p>
            @if (buscandoSugerencia()) {
              <p class="text-xs text-gray-400">Buscando un candidato sugerido…</p>
            } @else if (sugerido()) {
              <p class="text-xs text-emerald-700">
                Sugerido por el sistema: {{ sugerido()!.nombre }} {{ sugerido()!.apellido }} (el más cercano libre que
                cumple los topes de distancia). Podés confirmarlo o elegir otro cadete abajo.
              </p>
            } @else {
              <p class="text-xs text-amber-700">
                El sistema no encontró ningún cadete libre cerca — elegí uno a mano.
              </p>
            }
```

Y actualizar el comentario de `abrirModalAsignar` (línea 1411), de:

```ts
  /** Botón "Asignar"/"Reasignar": el sistema sugiere un candidato (zona + vehículo + FIFO), el admin confirma o elige otro. */
```

a:

```ts
  /** Botón "Asignar"/"Reasignar": el sistema sugiere un candidato (distancia + FIFO), el admin confirma o elige otro. */
```

- [ ] **Paso 3: Selección de lote (líneas 358-382)**

Reemplazar:

```html
              <p class="text-xs text-gray-400 -mb-1">
                Marcá uno o más pedidos de la misma zona para agruparlos en una sola tanda de ofertas a este cadete
                (necesita "Máx. viajes simultáneos" configurado en 2 o más para que le entren varios a la vez).
              </p>
              <div class="flex flex-col gap-1.5 max-h-64 overflow-y-auto">
                @for (p of pedidosSinAsignar(); track p.id) {
                  <label class="flex items-start gap-2 text-sm border border-gray-200 rounded px-2.5 py-1.5 hover:bg-gray-50">
                    <input
                      type="checkbox"
                      class="mt-0.5"
                      [checked]="pedidoIdsParaCadete.includes(p.id)"
                      (change)="toggleSeleccionLote(p.id)"
                    />
                    <span>
                      #{{ p.numero }} — {{ p.origenDireccion }} → {{ p.destinoDireccion }}
                      <span class="text-gray-400">(Zona {{ p.zona.nombre }}, {{ p.tipoVehiculoRequerido.nombre }})</span>
                    </span>
                  </label>
                }
              </div>
              @if (loteZonaMezclada()) {
                <div class="rounded bg-amber-50 border border-amber-200 text-amber-800 text-xs px-2.5 py-1.5">
                  Todos los pedidos que agrupes tienen que ser de la misma zona (o zonas aledañas).
                </div>
              }
```

por:

```html
              <p class="text-xs text-gray-400 -mb-1">
                Marcá uno o más pedidos con orígenes cercanos entre sí para agruparlos en una sola tanda de ofertas
                a este cadete (necesita "Máx. viajes simultáneos" configurado en 2 o más para que le entren varios a
                la vez).
              </p>
              <div class="flex flex-col gap-1.5 max-h-64 overflow-y-auto">
                @for (p of pedidosSinAsignar(); track p.id) {
                  <label class="flex items-start gap-2 text-sm border border-gray-200 rounded px-2.5 py-1.5 hover:bg-gray-50">
                    <input
                      type="checkbox"
                      class="mt-0.5"
                      [checked]="pedidoIdsParaCadete.includes(p.id)"
                      (change)="toggleSeleccionLote(p.id)"
                    />
                    <span>
                      #{{ p.numero }} — {{ p.origenDireccion }} → {{ p.destinoDireccion }}
                      <span class="text-gray-400">({{ p.requiereMoto ? 'Requiere moto' : 'Cualquier vehículo' }})</span>
                    </span>
                  </label>
                }
              </div>
              @if (loteOrigenesLejos()) {
                <div class="rounded bg-amber-50 border border-amber-200 text-amber-800 text-xs px-2.5 py-1.5">
                  Todos los pedidos que agrupes tienen que tener orígenes cercanos entre sí.
                </div>
              }
```

Reemplazar el `[disabled]` del botón de confirmar lote (línea 390):

```html
              [disabled]="pedidoIdsParaCadete.length === 0 || loteZonaMezclada()"
```

por:

```html
              [disabled]="pedidoIdsParaCadete.length === 0 || loteOrigenesLejos()"
```

- [ ] **Paso 4: Ficha de detalle del pedido (líneas 516-524)**

Reemplazar:

```html
                <div>
                  <div class="text-xs text-gray-400">Zona</div>
                  <div class="font-medium text-gray-800">{{ p.zona.nombre }}</div>
                </div>
                <div>
                  <div class="text-xs text-gray-400">Vehículo</div>
                  <div class="font-medium text-gray-800">{{ p.tipoVehiculoRequerido.nombre }}</div>
                </div>
```

por:

```html
                <div>
                  <div class="text-xs text-gray-400">Vehículo</div>
                  <div class="font-medium text-gray-800">{{ p.requiereMoto ? 'Requiere moto' : 'Cualquier vehículo' }}</div>
                </div>
```

(No tocar el bloque un poco más abajo, líneas 577-585, `p.cadeteAsignado.tipoVehiculo.nombre` — es el vehículo real del cadete asignado, no cambia.)

- [ ] **Paso 5: Reemplazar `loteZonaMezclada()` por `loteOrigenesLejos()`**

Reemplazar (líneas 1106-1111):

```ts
  /** Método (no computed) porque depende de `pedidoIdsParaCadete`, un array plano que se muta con checkboxes. */
  loteZonaMezclada(): boolean {
    const seleccionados = this.pedidosSinAsignar().filter((p) => this.pedidoIdsParaCadete.includes(p.id));
    const zonas = new Set(seleccionados.map((p) => p.zona.id));
    return zonas.size > 1;
  }
```

por:

```ts
  /** Método (no computed) porque depende de `pedidoIdsParaCadete`, un array plano que se muta con checkboxes. */
  loteOrigenesLejos(): boolean {
    const seleccionados = this.pedidosSinAsignar().filter((p) => this.pedidoIdsParaCadete.includes(p.id));
    if (seleccionados.length < 2) return false;
    const topeKm = Number(this.config.valores()['distancia_maxima_lote_km'] ?? 3);
    const [primero, ...resto] = seleccionados;
    return resto.some((p) => this.distanciaKm(primero.origenLat, primero.origenLng, p.origenLat, p.origenLng) > topeKm);
  }

  private distanciaKm(lat1: number, lng1: number, lat2: number, lng2: number): number {
    const R = 6371;
    const toRad = (d: number) => (d * Math.PI) / 180;
    const dLat = toRad(lat2 - lat1);
    const dLng = toRad(lng2 - lng1);
    const a = Math.sin(dLat / 2) ** 2 + Math.cos(toRad(lat1)) * Math.cos(toRad(lat2)) * Math.sin(dLng / 2) ** 2;
    return 2 * R * Math.asin(Math.sqrt(a));
  }
```

- [ ] **Paso 6: Actualizar la llamada en `confirmarAsignarDesdeCadete()`**

Reemplazar (línea 1475):

```ts
    if (!c || this.pedidoIdsParaCadete.length === 0 || this.loteZonaMezclada()) return;
```

por:

```ts
    if (!c || this.pedidoIdsParaCadete.length === 0 || this.loteOrigenesLejos()) return;
```

- [ ] **Paso 7: Build**

Run: `npm run build`
Expected: BUILD SUCCESS.

- [ ] **Paso 8: Verificación manual en el navegador**

Levantar el backend (`mvnw.cmd spring-boot:run`) y el front (`npm start` en `admin-front`), abrir el dashboard, abrir el modal de "Asignar" sobre un pedido `SIN_ASIGNAR` y confirmar que ya no aparece "Zona" en la tarjeta ni en la ficha de detalle, y que el checkbox "Requiere moto" del alta funciona. Abrir "Cadetes libres" → "Asignar viaje" con 2+ pedidos y confirmar que el aviso de "orígenes cercanos" aparece/desaparece según la distancia real entre ellos.

- [ ] **Paso 9: Commit**

```bash
git add admin-front/src/app/features/dashboard/dashboard.component.ts
git commit -m "Front: dashboard sin zona, lote agrupado por distancia entre origenes"
```

---

### Tarea 9: `cadete-app` — actualizar el DTO de Pedido

**Archivos:**
- Modificar: `cadete-app/app/src/main/java/com/cadeteria/cadete/data/remote/dto/PedidoDtos.kt`

**Interfaces:**
- Consume: el contrato JSON nuevo de `PedidoResponse` (Task 1): sin `zona`, con `requiereMoto: boolean` en vez de `tipoVehiculoRequerido`.

- [ ] **Paso 1: Editar el DTO**

Reemplazar (líneas 25-26):

```kotlin
    val zona: LookupDto,
    val tipoVehiculoRequerido: LookupDto,
```

por:

```kotlin
    val requiereMoto: Boolean,
```

- [ ] **Paso 2: Verificar que compila**

Run: `./gradlew :app:compileDebugKotlin` (desde `C:\proyectos\cadeteria\cadete-app`)
Expected: BUILD SUCCESSFUL — ningún otro archivo construye `PedidoDto(...)` a mano ni lee `zona`/`tipoVehiculoRequerido` (son campos que solo se deserializaban del JSON y no se usaban en ninguna pantalla).

- [ ] **Paso 3: Commit**

```bash
git add app/src/main/java/com/cadeteria/cadete/data/remote/dto/PedidoDtos.kt
git commit -m "Actualiza PedidoDto al contrato nuevo (requiereMoto en vez de zona/tipoVehiculoRequerido)"
```
