# Actualización de datos del cadete (con revisión del admin) — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** El cadete propone cambios de foto de perfil, foto de vehículo, tarjeta verde y datos
del vehículo desde la app — cada campo queda pendiente hasta que el admin lo aprueba o rechaza
por separado, con historial permanente de lo pedido y lo resuelto.

**Architecture:** Dos entidades nuevas (`CadeteActualizacion` = lote, `CadeteActualizacionCampo`
= un campo propuesto con su propio estado PENDIENTE/APROBADO/RECHAZADO) — no se reusa
`SolicitudCadete`, que es todo-o-nada por diseño. Backend expone 2 endpoints de auto-servicio
(`/api/cadetes/me/actualizaciones`) y 3 de admin (`/api/admin/cadetes/actualizaciones`). Al
aprobar un campo se aplica al `Cadete` real al toque; al rechazar, no se toca nada.

**Tech Stack:** Spring Boot 3.3.5 / Java 21 (backend), Angular 19 standalone + signals
(admin-front), Jetpack Compose + Retrofit (cadete-app).

**Spec:** `documentacion/spec-actualizacion-datos-cadete.md`

## Global Constraints

- No se reusa `SolicitudCadete` — son 2 entidades nuevas, propias de este feature.
- CBU/alias (`PATCH /api/cadetes/me/cuenta`) y teléfono (`PATCH /api/cadetes/me/telefono`) NO
  se tocan — siguen instantáneos, fuera de este mecanismo.
- Los 9 campos editables, con estos nombres EXACTOS de `campo` (String, no un `Lookup`):
  `FOTO_PERFIL`, `FOTO_VEHICULO`, `FOTO_TARJETA_VERDE`, `FOTO_TARJETA_VERDE_DORSO`,
  `VEHICULO_MARCA`, `VEHICULO_MODELO`, `VEHICULO_COLOR`, `VEHICULO_PATENTE`, `VEHICULO_ANIO`.
- Endpoints de auto-servicio bajo `/api/cadetes/me/actualizaciones` (ya protegido por
  `hasRole("CADETE")` vía el matcher genérico `/api/cadetes/me/**` en `SecurityConfig` — no
  hace falta tocar `SecurityConfig`). Endpoints admin bajo
  `/api/admin/cadetes/actualizaciones` (protegido por el matcher genérico
  `hasRole("ADMIN")` — **a propósito sin un `PERM_` nuevo**, mismo precedente que
  `/api/admin/solicitudes-cadete`, que tampoco tiene uno; la ruta del panel
  `/cadetes/actualizaciones` tampoco lleva `permisoGuard(...)`, mismo precedente que
  `/cadetes/solicitudes`).
- Tablas nuevas (`cadete_actualizacion`, `cadete_actualizacion_campo`) las crea Hibernate solo
  (`ddl-auto: update`) — no hace falta ningún script de migración.
- Sin WebSocket ni badge en vivo para el admin — la pantalla se revisa entrando, igual que
  `solicitudes-cadete` hoy.
- Backend: TDD con tests reales de servicio (mocks, mismo estilo que
  `PedidoServiceExclusionQuitarTest`). admin-front/cadete-app: sin suite de tests nueva —
  build-verificado (`npm run build` / `gradlew assembleDebug`), mismo precedente que el resto
  del proyecto (no hay tests de componente Angular ni de pantalla Compose en el repo hoy).
- Las fotos se suben directo a Cloudinary desde el cliente que las originó — el cadete ya tiene
  `CloudinaryUploader` (`app.cloudinaryUploader.subir(cloudName, uploadPreset, archivo)`) y
  `CadeteConfigDto.cloudinaryCloudName/cloudinaryUploadPreset`; el backend nunca ve el archivo,
  solo la URL resultante.

---

## Backend (`cadeteria`)

### Task 1: Entidades, repositorios y `CadeteActualizacionService.crear()`

**Files:**
- Create: `src/main/java/com/cadeteria/backend/model/CadeteActualizacion.java`
- Create: `src/main/java/com/cadeteria/backend/model/CadeteActualizacionCampo.java`
- Create: `src/main/java/com/cadeteria/backend/repository/CadeteActualizacionRepository.java`
- Create: `src/main/java/com/cadeteria/backend/repository/CadeteActualizacionCampoRepository.java`
- Create: `src/main/java/com/cadeteria/backend/dto/CadeteActualizacionDtos.java`
- Create: `src/main/java/com/cadeteria/backend/service/CadeteActualizacionService.java`
- Test: `src/test/java/com/cadeteria/backend/service/CadeteActualizacionServiceCrearTest.java`

**Interfaces:**
- Consumes: `Cadete` (modelo existente — `getId`, `getFotoUrl`, `getFotoVehiculoUrl`,
  `getFotoTarjetaVerdeUrl`, `getFotoTarjetaVerdeDorsoUrl`, `getVehiculoMarca`,
  `getVehiculoModelo`, `getVehiculoColor`, `getVehiculoPatente`, `getVehiculoAnio`),
  `CadeteRepository.findByUsername(String)`, `BadRequestException`,
  `ResourceNotFoundException.of(String, Object)`.
- Produces: `CadeteActualizacionService.crear(String username, ActualizacionCadeteRequest req)`
  → `CadeteActualizacion`; `CadeteActualizacionService.camposDe(String actualizacionId)` →
  `List<CadeteActualizacionCampo>`; `CadeteActualizacionService.misActualizaciones(String
  username)` → `List<CadeteActualizacion>`. Estos 3 métodos los consume el Task 3 (controllers)
  y el Task 2 (que agrega el resto de los métodos al mismo service).

- [ ] **Step 1: Crear las 2 entidades**

`src/main/java/com/cadeteria/backend/model/CadeteActualizacion.java`:

```java
package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Un "lote" de cambios que el propio cadete propone desde la app (mejora 2026-09-23) — agrupa
 * uno o más {@link CadeteActualizacionCampo} mandados juntos. No tiene estado propio: el admin
 * aprueba/rechaza cada campo por separado (ver CadeteActualizacionCampo), así que el "estado"
 * del lote se deriva de sus campos al armar la respuesta (nunca se persiste acá).
 */
@Entity
@Table(name = "cadete_actualizacion")
public class CadeteActualizacion {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "cadete_id")
    private Cadete cadete;

    @Column(nullable = false)
    private Instant creadoEn = Instant.now();

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public Cadete getCadete() {
        return cadete;
    }

    public void setCadete(Cadete cadete) {
        this.cadete = cadete;
    }

    public Instant getCreadoEn() {
        return creadoEn;
    }

    public void setCreadoEn(Instant creadoEn) {
        this.creadoEn = creadoEn;
    }
}
```

`src/main/java/com/cadeteria/backend/model/CadeteActualizacionCampo.java`:

```java
package com.cadeteria.backend.model;

import jakarta.persistence.*;

import java.time.Instant;

/**
 * Un campo propuesto dentro de un {@link CadeteActualizacion} — se aprueba/rechaza por
 * separado del resto (mejora 2026-09-23, pedido del dueño: "campo por campo", no todo el lote
 * junto). Esta tabla ES el historial: nunca se borra, queda como registro permanente de qué se
 * pidió, cuándo, y cómo se resolvió — no entra en RetencionDatosService (es auditoría de
 * identidad, no una foto de un pedido puntual).
 * <p>
 * {@code campo} es uno de: FOTO_PERFIL, FOTO_VEHICULO, FOTO_TARJETA_VERDE,
 * FOTO_TARJETA_VERDE_DORSO, VEHICULO_MARCA, VEHICULO_MODELO, VEHICULO_COLOR, VEHICULO_PATENTE,
 * VEHICULO_ANIO — valores fijos validados en {@code CadeteActualizacionService}, no una tabla
 * de parametría tipo {@link Lookup} (no son algo que el admin edite desde Configuración).
 */
@Entity
@Table(name = "cadete_actualizacion_campo")
public class CadeteActualizacionCampo {

    @Id
    private String id;

    @ManyToOne(optional = false)
    @JoinColumn(name = "actualizacion_id")
    private CadeteActualizacion actualizacion;

    @Column(nullable = false)
    private String campo;

    @Column(length = 1000)
    private String valorAnterior;

    @Column(length = 1000, nullable = false)
    private String valorPropuesto;

    /** PENDIENTE / APROBADO / RECHAZADO */
    @Column(nullable = false)
    private String estado = "PENDIENTE";

    private String motivoRechazo;

    private Instant resueltoEn;

    private String resueltoPorUsername;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public CadeteActualizacion getActualizacion() {
        return actualizacion;
    }

    public void setActualizacion(CadeteActualizacion actualizacion) {
        this.actualizacion = actualizacion;
    }

    public String getCampo() {
        return campo;
    }

    public void setCampo(String campo) {
        this.campo = campo;
    }

    public String getValorAnterior() {
        return valorAnterior;
    }

    public void setValorAnterior(String valorAnterior) {
        this.valorAnterior = valorAnterior;
    }

    public String getValorPropuesto() {
        return valorPropuesto;
    }

    public void setValorPropuesto(String valorPropuesto) {
        this.valorPropuesto = valorPropuesto;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getMotivoRechazo() {
        return motivoRechazo;
    }

    public void setMotivoRechazo(String motivoRechazo) {
        this.motivoRechazo = motivoRechazo;
    }

    public Instant getResueltoEn() {
        return resueltoEn;
    }

    public void setResueltoEn(Instant resueltoEn) {
        this.resueltoEn = resueltoEn;
    }

    public String getResueltoPorUsername() {
        return resueltoPorUsername;
    }

    public void setResueltoPorUsername(String resueltoPorUsername) {
        this.resueltoPorUsername = resueltoPorUsername;
    }
}
```

- [ ] **Step 2: Crear los repositorios**

`src/main/java/com/cadeteria/backend/repository/CadeteActualizacionRepository.java`:

```java
package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CadeteActualizacion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CadeteActualizacionRepository extends JpaRepository<CadeteActualizacion, String> {
    List<CadeteActualizacion> findByCadeteIdOrderByCreadoEnDesc(String cadeteId);
}
```

`src/main/java/com/cadeteria/backend/repository/CadeteActualizacionCampoRepository.java`:

```java
package com.cadeteria.backend.repository;

import com.cadeteria.backend.model.CadeteActualizacionCampo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface CadeteActualizacionCampoRepository extends JpaRepository<CadeteActualizacionCampo, String> {

    List<CadeteActualizacionCampo> findByActualizacionId(String actualizacionId);

    @Query("SELECT COUNT(c) > 0 FROM CadeteActualizacionCampo c WHERE c.actualizacion.cadete.id = :cadeteId AND c.estado = :estado")
    boolean existePendientePara(@Param("cadeteId") String cadeteId, @Param("estado") String estado);

    @Query("SELECT c FROM CadeteActualizacionCampo c WHERE c.estado = :estado ORDER BY c.actualizacion.creadoEn DESC")
    List<CadeteActualizacionCampo> findByEstadoOrderByActualizacionCreadoEnDesc(@Param("estado") String estado);
}
```

- [ ] **Step 3: Crear los DTOs**

`src/main/java/com/cadeteria/backend/dto/CadeteActualizacionDtos.java`:

```java
package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.CadeteActualizacion;
import com.cadeteria.backend.model.CadeteActualizacionCampo;

import java.time.Instant;
import java.util.List;

public final class CadeteActualizacionDtos {

    private CadeteActualizacionDtos() {}

    /** Todos los campos son opcionales — el cadete manda solo los que quiere cambiar. */
    public record ActualizacionCadeteRequest(
            String fotoUrl, String fotoVehiculoUrl,
            String fotoTarjetaVerdeUrl, String fotoTarjetaVerdeDorsoUrl,
            String vehiculoMarca, String vehiculoModelo, String vehiculoColor,
            String vehiculoPatente, Integer vehiculoAnio
    ) {}

    public record RechazarCampoRequest(String motivo) {}

    public record CampoResponse(
            String id, String campo, String valorAnterior, String valorPropuesto,
            String estado, String motivoRechazo, Instant resueltoEn, String resueltoPorUsername
    ) {
        public static CampoResponse from(CadeteActualizacionCampo c) {
            return new CampoResponse(c.getId(), c.getCampo(), c.getValorAnterior(), c.getValorPropuesto(),
                    c.getEstado(), c.getMotivoRechazo(), c.getResueltoEn(), c.getResueltoPorUsername());
        }
    }

    /** Un lote con sus campos — el "estado" del lote se deriva acá, no se persiste (spec 5.2). */
    public record ActualizacionResponse(
            String id, Instant creadoEn, String estado, List<CampoResponse> campos
    ) {
        public static ActualizacionResponse from(CadeteActualizacion a, List<CadeteActualizacionCampo> campos) {
            String estado = campos.stream().anyMatch(c -> "PENDIENTE".equals(c.getEstado())) ? "PENDIENTE"
                    : campos.stream().anyMatch(c -> "RECHAZADO".equals(c.getEstado())) ? "CON_RECHAZOS"
                    : "APROBADO";
            return new ActualizacionResponse(a.getId(), a.getCreadoEn(), estado,
                    campos.stream().map(CampoResponse::from).toList());
        }
    }

    /** Para la pantalla admin: el campo pendiente + de qué lote/cadete es. */
    public record CampoPendienteAdminResponse(
            String actualizacionId, CampoResponse campo,
            String cadeteId, String cadeteNombre, String cadeteApellido, String cadeteFotoUrl
    ) {}
}
```

- [ ] **Step 4: Escribir los tests de `crear()` (deben fallar — el service no existe todavía)**

`src/test/java/com/cadeteria/backend/service/CadeteActualizacionServiceCrearTest.java`:

```java
package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.ActualizacionCadeteRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteActualizacion;
import com.cadeteria.backend.model.CadeteActualizacionCampo;
import com.cadeteria.backend.repository.CadeteActualizacionCampoRepository;
import com.cadeteria.backend.repository.CadeteActualizacionRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CadeteActualizacionServiceCrearTest {

    private CadeteActualizacionRepository loteRepo;
    private CadeteActualizacionCampoRepository campoRepo;
    private CadeteRepository cadeteRepo;
    private CadeteActualizacionService service;

    @BeforeEach
    void setUp() {
        loteRepo = mock(CadeteActualizacionRepository.class);
        campoRepo = mock(CadeteActualizacionCampoRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        service = new CadeteActualizacionService(loteRepo, campoRepo, cadeteRepo);

        when(loteRepo.save(any(CadeteActualizacion.class))).thenAnswer(i -> i.getArgument(0));
        when(campoRepo.save(any(CadeteActualizacionCampo.class))).thenAnswer(i -> i.getArgument(0));
    }

    private Cadete cadeteActual() {
        Cadete c = new Cadete();
        c.setId("c1");
        c.setUsername("jperez");
        c.setFotoVehiculoUrl("http://viejo/vehiculo.jpg");
        c.setVehiculoColor("Rojo");
        c.setVehiculoAnio(2020);
        when(cadeteRepo.findByUsername("jperez")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void ignoraCamposIgualesAlValorActual() {
        cadeteActual();
        when(campoRepo.existePendientePara("c1", "PENDIENTE")).thenReturn(false);

        service.crear("jperez", new ActualizacionCadeteRequest(
                null, null, null, null, null, null, "Rojo", null, null));

        verify(campoRepo, never()).save(any());
    }

    @Test
    void fallaSiNoHayNingunCambioReal() {
        cadeteActual();
        when(campoRepo.existePendientePara("c1", "PENDIENTE")).thenReturn(false);

        assertThrows(BadRequestException.class, () ->
                service.crear("jperez", new ActualizacionCadeteRequest(
                        null, null, null, null, null, null, "Rojo", null, null)));
    }

    @Test
    void fallaSiYaTieneUnCampoPendiente() {
        cadeteActual();
        when(campoRepo.existePendientePara("c1", "PENDIENTE")).thenReturn(true);

        assertThrows(BadRequestException.class, () ->
                service.crear("jperez", new ActualizacionCadeteRequest(
                        null, null, null, null, null, null, "Azul", null, null)));
    }

    @Test
    void creaUnCampoPorCadaCambioConElValorAnteriorCorrecto() {
        cadeteActual();
        when(campoRepo.existePendientePara("c1", "PENDIENTE")).thenReturn(false);

        service.crear("jperez", new ActualizacionCadeteRequest(
                null, "http://nuevo/vehiculo.jpg", null, null, null, null, "Azul", null, 2022));

        ArgumentCaptor<CadeteActualizacionCampo> captor = ArgumentCaptor.forClass(CadeteActualizacionCampo.class);
        verify(campoRepo, times(3)).save(captor.capture());
        List<CadeteActualizacionCampo> guardados = captor.getAllValues();

        CadeteActualizacionCampo color = guardados.stream().filter(c -> "VEHICULO_COLOR".equals(c.getCampo())).findFirst().orElseThrow();
        assertEquals("Rojo", color.getValorAnterior());
        assertEquals("Azul", color.getValorPropuesto());
        assertEquals("PENDIENTE", color.getEstado());

        CadeteActualizacionCampo anio = guardados.stream().filter(c -> "VEHICULO_ANIO".equals(c.getCampo())).findFirst().orElseThrow();
        assertEquals("2020", anio.getValorAnterior());
        assertEquals("2022", anio.getValorPropuesto());

        CadeteActualizacionCampo vehiculo = guardados.stream().filter(c -> "FOTO_VEHICULO".equals(c.getCampo())).findFirst().orElseThrow();
        assertEquals("http://viejo/vehiculo.jpg", vehiculo.getValorAnterior());
        assertEquals("http://nuevo/vehiculo.jpg", vehiculo.getValorPropuesto());
    }
}
```

Run: `./mvnw.cmd test -Dtest=CadeteActualizacionServiceCrearTest`
Expected: FAIL — `CadeteActualizacionService` no existe todavía.

- [ ] **Step 5: Implementar `CadeteActualizacionService.crear()` (mínimo para pasar el test)**

`src/main/java/com/cadeteria/backend/service/CadeteActualizacionService.java`:

```java
package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.common.ResourceNotFoundException;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.ActualizacionCadeteRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteActualizacion;
import com.cadeteria.backend.model.CadeteActualizacionCampo;
import com.cadeteria.backend.repository.CadeteActualizacionCampoRepository;
import com.cadeteria.backend.repository.CadeteActualizacionRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class CadeteActualizacionService {

    private final CadeteActualizacionRepository loteRepo;
    private final CadeteActualizacionCampoRepository campoRepo;
    private final CadeteRepository cadeteRepo;

    public CadeteActualizacionService(CadeteActualizacionRepository loteRepo, CadeteActualizacionCampoRepository campoRepo,
                                       CadeteRepository cadeteRepo) {
        this.loteRepo = loteRepo;
        this.campoRepo = campoRepo;
        this.cadeteRepo = cadeteRepo;
    }

    private Cadete getCadete(String username) {
        return cadeteRepo.findByUsername(username).orElseThrow(() -> ResourceNotFoundException.of("Cadete", username));
    }

    private record CampoCandidato(String campo, String valorAnterior, String valorPropuesto) {}

    /** El cadete propone cambios — cada campo queda PENDIENTE hasta que el admin lo resuelva. */
    public CadeteActualizacion crear(String username, ActualizacionCadeteRequest req) {
        Cadete cadete = getCadete(username);
        if (campoRepo.existePendientePara(cadete.getId(), "PENDIENTE")) {
            throw new BadRequestException("Ya tenés una actualización esperando revisión — esperá a que se resuelva antes de mandar otra.");
        }

        List<CampoCandidato> candidatos = new ArrayList<>();
        agregarSiCambia(candidatos, "FOTO_PERFIL", req.fotoUrl(), cadete.getFotoUrl());
        agregarSiCambia(candidatos, "FOTO_VEHICULO", req.fotoVehiculoUrl(), cadete.getFotoVehiculoUrl());
        agregarSiCambia(candidatos, "FOTO_TARJETA_VERDE", req.fotoTarjetaVerdeUrl(), cadete.getFotoTarjetaVerdeUrl());
        agregarSiCambia(candidatos, "FOTO_TARJETA_VERDE_DORSO", req.fotoTarjetaVerdeDorsoUrl(), cadete.getFotoTarjetaVerdeDorsoUrl());
        agregarSiCambia(candidatos, "VEHICULO_MARCA", req.vehiculoMarca(), cadete.getVehiculoMarca());
        agregarSiCambia(candidatos, "VEHICULO_MODELO", req.vehiculoModelo(), cadete.getVehiculoModelo());
        agregarSiCambia(candidatos, "VEHICULO_COLOR", req.vehiculoColor(), cadete.getVehiculoColor());
        agregarSiCambia(candidatos, "VEHICULO_PATENTE", req.vehiculoPatente(), cadete.getVehiculoPatente());
        agregarSiCambia(candidatos, "VEHICULO_ANIO",
                req.vehiculoAnio() == null ? null : req.vehiculoAnio().toString(),
                cadete.getVehiculoAnio() == null ? null : cadete.getVehiculoAnio().toString());

        if (candidatos.isEmpty()) {
            throw new BadRequestException("No mandaste ningún cambio.");
        }

        CadeteActualizacion lote = new CadeteActualizacion();
        lote.setId(UUID.randomUUID().toString());
        lote.setCadete(cadete);
        lote.setCreadoEn(Instant.now());
        lote = loteRepo.save(lote);

        for (CampoCandidato c : candidatos) {
            CadeteActualizacionCampo campo = new CadeteActualizacionCampo();
            campo.setId(UUID.randomUUID().toString());
            campo.setActualizacion(lote);
            campo.setCampo(c.campo());
            campo.setValorAnterior(c.valorAnterior());
            campo.setValorPropuesto(c.valorPropuesto());
            campo.setEstado("PENDIENTE");
            campoRepo.save(campo);
        }
        return lote;
    }

    private static void agregarSiCambia(List<CampoCandidato> candidatos, String campo, String propuesto, String actual) {
        if (propuesto == null || propuesto.isBlank()) return;
        String propuestoTrim = propuesto.trim();
        if (propuestoTrim.equals(actual)) return;
        candidatos.add(new CampoCandidato(campo, actual, propuestoTrim));
    }

    @Transactional(readOnly = true)
    public List<CadeteActualizacion> misActualizaciones(String username) {
        Cadete cadete = getCadete(username);
        return loteRepo.findByCadeteIdOrderByCreadoEnDesc(cadete.getId());
    }

    @Transactional(readOnly = true)
    public List<CadeteActualizacionCampo> camposDe(String actualizacionId) {
        return campoRepo.findByActualizacionId(actualizacionId);
    }
}
```

- [ ] **Step 6: Correr los tests, deben pasar**

Run: `./mvnw.cmd test -Dtest=CadeteActualizacionServiceCrearTest`
Expected: PASS (4 tests).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/com/cadeteria/backend/model/CadeteActualizacion.java \
        src/main/java/com/cadeteria/backend/model/CadeteActualizacionCampo.java \
        src/main/java/com/cadeteria/backend/repository/CadeteActualizacionRepository.java \
        src/main/java/com/cadeteria/backend/repository/CadeteActualizacionCampoRepository.java \
        src/main/java/com/cadeteria/backend/dto/CadeteActualizacionDtos.java \
        src/main/java/com/cadeteria/backend/service/CadeteActualizacionService.java \
        src/test/java/com/cadeteria/backend/service/CadeteActualizacionServiceCrearTest.java
git commit -m "Modelo, repos y CadeteActualizacionService.crear() para la actualizacion de datos del cadete con revision"
```

---

### Task 2: `CadeteActualizacionService` — aprobar/rechazar/listar pendientes (admin)

**Files:**
- Modify: `src/main/java/com/cadeteria/backend/service/CadeteActualizacionService.java`
- Test: `src/test/java/com/cadeteria/backend/service/CadeteActualizacionServiceRevisionTest.java`

**Interfaces:**
- Consumes: `CadeteActualizacionCampo`/`CadeteActualizacion` (Task 1),
  `CadeteActualizacionCampoRepository.findByEstadoOrderByActualizacionCreadoEnDesc` (Task 1).
- Produces: `aprobarCampo(String campoId, String adminUsername)` → `CadeteActualizacionCampo`;
  `rechazarCampo(String campoId, String motivo, String adminUsername)` →
  `CadeteActualizacionCampo`; `listarPendientes()` → `List<CadeteActualizacionCampo>`. Los
  consume el Task 3 (controller admin).

- [ ] **Step 1: Escribir los tests (deben fallar — los métodos no existen todavía)**

`src/test/java/com/cadeteria/backend/service/CadeteActualizacionServiceRevisionTest.java`:

```java
package com.cadeteria.backend.service;

import com.cadeteria.backend.common.BadRequestException;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteActualizacion;
import com.cadeteria.backend.model.CadeteActualizacionCampo;
import com.cadeteria.backend.repository.CadeteActualizacionCampoRepository;
import com.cadeteria.backend.repository.CadeteActualizacionRepository;
import com.cadeteria.backend.repository.CadeteRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CadeteActualizacionServiceRevisionTest {

    private CadeteActualizacionCampoRepository campoRepo;
    private CadeteRepository cadeteRepo;
    private CadeteActualizacionService service;

    @BeforeEach
    void setUp() {
        campoRepo = mock(CadeteActualizacionCampoRepository.class);
        cadeteRepo = mock(CadeteRepository.class);
        service = new CadeteActualizacionService(mock(CadeteActualizacionRepository.class), campoRepo, cadeteRepo);

        when(campoRepo.save(any(CadeteActualizacionCampo.class))).thenAnswer(i -> i.getArgument(0));
        when(cadeteRepo.save(any(Cadete.class))).thenAnswer(i -> i.getArgument(0));
    }

    private CadeteActualizacionCampo campoPendiente(String campo, String valorPropuesto) {
        Cadete cadete = new Cadete();
        cadete.setId("c1");
        cadete.setVehiculoColor("Rojo");

        CadeteActualizacion lote = new CadeteActualizacion();
        lote.setId("lote1");
        lote.setCadete(cadete);

        CadeteActualizacionCampo c = new CadeteActualizacionCampo();
        c.setId("campo1");
        c.setActualizacion(lote);
        c.setCampo(campo);
        c.setValorAnterior("Rojo");
        c.setValorPropuesto(valorPropuesto);
        c.setEstado("PENDIENTE");
        when(campoRepo.findById("campo1")).thenReturn(Optional.of(c));
        return c;
    }

    @Test
    void aprobarAplicaElValorAlCadeteYMarcaAprobado() {
        CadeteActualizacionCampo campo = campoPendiente("VEHICULO_COLOR", "Azul");

        service.aprobarCampo("campo1", "admin");

        assertEquals("APROBADO", campo.getEstado());
        assertEquals("admin", campo.getResueltoPorUsername());
        assertNotNull(campo.getResueltoEn());
        assertEquals("Azul", campo.getActualizacion().getCadete().getVehiculoColor());
    }

    @Test
    void aprobarUnCampoYaResueltoFalla() {
        CadeteActualizacionCampo campo = campoPendiente("VEHICULO_COLOR", "Azul");
        campo.setEstado("APROBADO");

        assertThrows(BadRequestException.class, () -> service.aprobarCampo("campo1", "admin"));
    }

    @Test
    void rechazarNoTocaElCadeteYGuardaElMotivo() {
        CadeteActualizacionCampo campo = campoPendiente("VEHICULO_COLOR", "Azul");

        service.rechazarCampo("campo1", "no coincide con la foto del vehículo", "admin");

        assertEquals("RECHAZADO", campo.getEstado());
        assertEquals("no coincide con la foto del vehículo", campo.getMotivoRechazo());
        assertEquals("Rojo", campo.getActualizacion().getCadete().getVehiculoColor(), "rechazar no debe tocar el Cadete real");
        verify(cadeteRepo, never()).save(any());
    }

    @Test
    void aprobarUnCampoDeAnioParseaElEnteroCorrectamente() {
        CadeteActualizacionCampo campo = campoPendiente("VEHICULO_ANIO", "2022");

        service.aprobarCampo("campo1", "admin");

        assertEquals(2022, campo.getActualizacion().getCadete().getVehiculoAnio());
    }

    @Test
    void listarPendientesDelegaEnElRepositorioConElEstadoPendiente() {
        service.listarPendientes();

        verify(campoRepo).findByEstadoOrderByActualizacionCreadoEnDesc("PENDIENTE");
    }
}
```

Run: `./mvnw.cmd test -Dtest=CadeteActualizacionServiceRevisionTest`
Expected: FAIL — `aprobarCampo`/`rechazarCampo`/`listarPendientes` no existen todavía.

- [ ] **Step 2: Agregar los métodos al service**

Agregar al final de la clase `CadeteActualizacionService` (antes de la llave de cierre):

```java
    @Transactional(readOnly = true)
    public List<CadeteActualizacionCampo> listarPendientes() {
        return campoRepo.findByEstadoOrderByActualizacionCreadoEnDesc("PENDIENTE");
    }

    /** El admin aprueba un campo puntual — se aplica al Cadete real al toque. */
    public CadeteActualizacionCampo aprobarCampo(String campoId, String adminUsername) {
        CadeteActualizacionCampo campo = getCampo(campoId);
        exigirPendiente(campo);
        aplicarValor(campo.getActualizacion().getCadete(), campo);
        campo.setEstado("APROBADO");
        campo.setResueltoEn(Instant.now());
        campo.setResueltoPorUsername(adminUsername);
        return campoRepo.save(campo);
    }

    /** El admin rechaza un campo puntual — el Cadete real no se toca. */
    public CadeteActualizacionCampo rechazarCampo(String campoId, String motivo, String adminUsername) {
        CadeteActualizacionCampo campo = getCampo(campoId);
        exigirPendiente(campo);
        campo.setEstado("RECHAZADO");
        campo.setMotivoRechazo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        campo.setResueltoEn(Instant.now());
        campo.setResueltoPorUsername(adminUsername);
        return campoRepo.save(campo);
    }

    private CadeteActualizacionCampo getCampo(String id) {
        return campoRepo.findById(id).orElseThrow(() -> ResourceNotFoundException.of("Actualización de cadete", id));
    }

    private void exigirPendiente(CadeteActualizacionCampo campo) {
        if (!"PENDIENTE".equals(campo.getEstado())) {
            throw new BadRequestException("Este campo ya fue resuelto.");
        }
    }

    private void aplicarValor(Cadete cadete, CadeteActualizacionCampo campo) {
        String valor = campo.getValorPropuesto();
        switch (campo.getCampo()) {
            case "FOTO_PERFIL" -> cadete.setFotoUrl(valor);
            case "FOTO_VEHICULO" -> cadete.setFotoVehiculoUrl(valor);
            case "FOTO_TARJETA_VERDE" -> cadete.setFotoTarjetaVerdeUrl(valor);
            case "FOTO_TARJETA_VERDE_DORSO" -> cadete.setFotoTarjetaVerdeDorsoUrl(valor);
            case "VEHICULO_MARCA" -> cadete.setVehiculoMarca(valor);
            case "VEHICULO_MODELO" -> cadete.setVehiculoModelo(valor);
            case "VEHICULO_COLOR" -> cadete.setVehiculoColor(valor);
            case "VEHICULO_PATENTE" -> cadete.setVehiculoPatente(valor);
            case "VEHICULO_ANIO" -> cadete.setVehiculoAnio(valor == null ? null : Integer.valueOf(valor));
            default -> throw new IllegalStateException("Campo desconocido: " + campo.getCampo());
        }
        cadeteRepo.save(cadete);
    }
```

- [ ] **Step 3: Correr los tests, deben pasar**

Run: `./mvnw.cmd test -Dtest=CadeteActualizacionServiceRevisionTest`
Expected: PASS (5 tests).

- [ ] **Step 4: Correr toda la suite del backend para confirmar que no rompió nada**

Run: `./mvnw.cmd test`
Expected: PASS (todos los tests, incluidos los 2 nuevos archivos).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cadeteria/backend/service/CadeteActualizacionService.java \
        src/test/java/com/cadeteria/backend/service/CadeteActualizacionServiceRevisionTest.java
git commit -m "CadeteActualizacionService: aprobar/rechazar campo por campo y listar pendientes para el admin"
```

---

### Task 3: Controllers (auto-servicio del cadete + admin)

**Files:**
- Create: `src/main/java/com/cadeteria/backend/controller/CadeteActualizacionController.java`
- Create: `src/main/java/com/cadeteria/backend/controller/CadeteActualizacionAdminController.java`

**Interfaces:**
- Consumes: `CadeteActualizacionService` completo (Tasks 1 y 2).
- Produces: `POST/GET /api/cadetes/me/actualizaciones`,
  `GET /api/admin/cadetes/actualizaciones`,
  `POST /api/admin/cadetes/actualizaciones/campos/{id}/aprobar`,
  `POST /api/admin/cadetes/actualizaciones/campos/{id}/rechazar`. Los consumen los Tasks 4-5
  (admin-front) y 7-8 (cadete-app).

- [ ] **Step 1: Controller de auto-servicio del cadete**

`src/main/java/com/cadeteria/backend/controller/CadeteActualizacionController.java`:

```java
package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.CadeteActualizacionDtos.ActualizacionCadeteRequest;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.ActualizacionResponse;
import com.cadeteria.backend.model.CadeteActualizacion;
import com.cadeteria.backend.service.CadeteActualizacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** El cadete propone cambios de foto/datos del vehículo desde la app (mejora 2026-09-23). */
@RestController
@RequestMapping("/api/cadetes/me/actualizaciones")
public class CadeteActualizacionController {

    private final CadeteActualizacionService service;

    public CadeteActualizacionController(CadeteActualizacionService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<ActualizacionResponse> crear(Authentication auth, @RequestBody ActualizacionCadeteRequest req) {
        CadeteActualizacion lote = service.crear(auth.getName(), req);
        return ResponseEntity.status(201).body(ActualizacionResponse.from(lote, service.camposDe(lote.getId())));
    }

    @GetMapping
    public List<ActualizacionResponse> listar(Authentication auth) {
        return service.misActualizaciones(auth.getName()).stream()
                .map(lote -> ActualizacionResponse.from(lote, service.camposDe(lote.getId())))
                .toList();
    }
}
```

- [ ] **Step 2: Controller admin**

`src/main/java/com/cadeteria/backend/controller/CadeteActualizacionAdminController.java`:

```java
package com.cadeteria.backend.controller;

import com.cadeteria.backend.dto.CadeteActualizacionDtos.CampoPendienteAdminResponse;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.CampoResponse;
import com.cadeteria.backend.dto.CadeteActualizacionDtos.RechazarCampoRequest;
import com.cadeteria.backend.model.Cadete;
import com.cadeteria.backend.model.CadeteActualizacionCampo;
import com.cadeteria.backend.service.CadeteActualizacionService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** Revisión admin de las actualizaciones que proponen los cadetes (mejora 2026-09-23). */
@RestController
@RequestMapping("/api/admin/cadetes/actualizaciones")
public class CadeteActualizacionAdminController {

    private final CadeteActualizacionService service;

    public CadeteActualizacionAdminController(CadeteActualizacionService service) {
        this.service = service;
    }

    @GetMapping
    public List<CampoPendienteAdminResponse> listarPendientes() {
        return service.listarPendientes().stream().map(this::toAdminResponse).toList();
    }

    @PostMapping("/campos/{id}/aprobar")
    public CampoResponse aprobar(@PathVariable String id, Authentication auth) {
        return CampoResponse.from(service.aprobarCampo(id, auth.getName()));
    }

    @PostMapping("/campos/{id}/rechazar")
    public CampoResponse rechazar(@PathVariable String id, @RequestBody(required = false) RechazarCampoRequest req, Authentication auth) {
        return CampoResponse.from(service.rechazarCampo(id, req == null ? null : req.motivo(), auth.getName()));
    }

    private CampoPendienteAdminResponse toAdminResponse(CadeteActualizacionCampo c) {
        Cadete cadete = c.getActualizacion().getCadete();
        return new CampoPendienteAdminResponse(c.getActualizacion().getId(), CampoResponse.from(c),
                cadete.getId(), cadete.getNombre(), cadete.getApellido(), cadete.getFotoUrl());
    }
}
```

- [ ] **Step 3: Compilar y correr toda la suite**

Run: `./mvnw.cmd test`
Expected: PASS — compila y ningún test existente se rompe (los controllers no tienen tests de
integración en este repo, ver Global Constraints).

- [ ] **Step 4: Verificación manual rápida (opcional pero recomendada)**

Con el backend levantado local (`./mvnw.cmd spring-boot:run`) y un cadete/admin logueados:

```bash
# Como cadete (token JWT de cadete):
curl -X POST http://localhost:8080/api/cadetes/me/actualizaciones \
  -H "Authorization: Bearer <TOKEN_CADETE>" -H "Content-Type: application/json" \
  -d '{"vehiculoColor":"Verde"}'

# Como admin (token JWT de admin):
curl http://localhost:8080/api/admin/cadetes/actualizaciones -H "Authorization: Bearer <TOKEN_ADMIN>"
```

Expected: el primer curl devuelve 201 con un `ActualizacionResponse` en estado `PENDIENTE`; el
segundo lista ese campo con los datos del cadete.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/com/cadeteria/backend/controller/CadeteActualizacionController.java \
        src/main/java/com/cadeteria/backend/controller/CadeteActualizacionAdminController.java
git commit -m "Endpoints de actualizacion de datos del cadete: auto-servicio y revision admin"
```

---

## admin-front

### Task 4: Modelo y servicio Angular

**Files:**
- Create: `src/app/core/models/cadete-actualizacion.model.ts`
- Create: `src/app/core/services/cadete-actualizacion.service.ts`

**Interfaces:**
- Consumes: `apiUrl` (`../config/site-config`), backend endpoints del Task 3.
- Produces: `CadeteActualizacionService` (Angular) con signals `pendientes`/`loading` y métodos
  `listar()`, `aprobar(id, onSuccess?)`, `rechazar(id, motivo, onSuccess?)`. Los consume el
  Task 5.

- [ ] **Step 1: Modelo**

`src/app/core/models/cadete-actualizacion.model.ts`:

```typescript
export interface CadeteActualizacionCampo {
  id: string;
  campo: string;
  valorAnterior: string | null;
  valorPropuesto: string;
  estado: 'PENDIENTE' | 'APROBADO' | 'RECHAZADO';
  motivoRechazo: string | null;
  resueltoEn: string | null;
  resueltoPorUsername: string | null;
}

export interface CampoPendienteAdmin {
  actualizacionId: string;
  campo: CadeteActualizacionCampo;
  cadeteId: string;
  cadeteNombre: string;
  cadeteApellido: string;
  cadeteFotoUrl: string | null;
}
```

- [ ] **Step 2: Servicio**

`src/app/core/services/cadete-actualizacion.service.ts`:

```typescript
import { Injectable, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { apiUrl } from '../config/site-config';
import { CampoPendienteAdmin } from '../models/cadete-actualizacion.model';

/** Revisión admin de las actualizaciones de datos que proponen los cadetes (mejora 2026-09-23). */
@Injectable({ providedIn: 'root' })
export class CadeteActualizacionService {
  private readonly http = inject(HttpClient);

  private readonly pendientesSignal = signal<CampoPendienteAdmin[]>([]);
  readonly pendientes = this.pendientesSignal.asReadonly();
  private readonly loadingSignal = signal(false);
  readonly loading = this.loadingSignal.asReadonly();

  listar(): void {
    this.loadingSignal.set(true);
    this.http.get<CampoPendienteAdmin[]>(apiUrl('/admin/cadetes/actualizaciones')).subscribe({
      next: (lista) => {
        this.pendientesSignal.set(lista);
        this.loadingSignal.set(false);
      },
      error: () => this.loadingSignal.set(false),
    });
  }

  aprobar(id: string, onSuccess?: () => void): void {
    this.http.post(apiUrl(`/admin/cadetes/actualizaciones/campos/${id}/aprobar`), {}).subscribe(() => onSuccess?.());
  }

  rechazar(id: string, motivo: string | null, onSuccess?: () => void): void {
    this.http.post(apiUrl(`/admin/cadetes/actualizaciones/campos/${id}/rechazar`), { motivo }).subscribe(() => onSuccess?.());
  }
}
```

- [ ] **Step 3: Verificar que compila**

Run: `npm run build`
Expected: exit 0, sin errores de TypeScript.

- [ ] **Step 4: Commit**

```bash
git add src/app/core/models/cadete-actualizacion.model.ts src/app/core/services/cadete-actualizacion.service.ts
git commit -m "Modelo y servicio Angular para revision de actualizaciones de cadete"
```

---

### Task 5: Pantalla de revisión + ruta + link desde Cadetes

**Files:**
- Create: `src/app/features/cadetes/revision-cadetes.component.ts`
- Modify: `src/app/app.routes.ts`
- Modify: `src/app/features/cadetes/cadetes.component.ts`

**Interfaces:**
- Consumes: `CadeteActualizacionService` (Task 4), `LightboxService`, `ToastService`,
  `optimizarImagen`, `EmptyStateComponent`, `LoadingSkeletonComponent` (ya existen, mismos
  imports que `solicitudes-cadete.component.ts`).

- [ ] **Step 1: Crear el componente**

`src/app/features/cadetes/revision-cadetes.component.ts`:

```typescript
import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { CadeteActualizacionService } from '../../core/services/cadete-actualizacion.service';
import { CampoPendienteAdmin } from '../../core/models/cadete-actualizacion.model';
import { ToastService } from '../../core/services/toast.service';
import { LightboxService } from '../../core/services/lightbox.service';
import { optimizarImagen } from '../../core/utils/imagen.util';
import { EmptyStateComponent } from '../../shared/empty-state.component';
import { LoadingSkeletonComponent } from '../../shared/loading-skeleton.component';

const CAMPO_LABEL: Record<string, string> = {
  FOTO_PERFIL: 'Foto de perfil',
  FOTO_VEHICULO: 'Foto del vehículo',
  FOTO_TARJETA_VERDE: 'Tarjeta verde — frente',
  FOTO_TARJETA_VERDE_DORSO: 'Tarjeta verde — dorso',
  VEHICULO_MARCA: 'Marca',
  VEHICULO_MODELO: 'Modelo',
  VEHICULO_COLOR: 'Color',
  VEHICULO_PATENTE: 'Patente',
  VEHICULO_ANIO: 'Año',
};

const CAMPOS_FOTO = new Set(['FOTO_PERFIL', 'FOTO_VEHICULO', 'FOTO_TARJETA_VERDE', 'FOTO_TARJETA_VERDE_DORSO']);

interface LoteAgrupado {
  actualizacionId: string;
  cadeteId: string;
  cadeteNombre: string;
  cadeteApellido: string;
  cadeteFotoUrl: string | null;
  campos: CampoPendienteAdmin[];
}

/** Revisión admin de "Actualizar mis datos" (mejora 2026-09-23) — aprobar/rechazar campo por campo. */
@Component({
  selector: 'app-revision-cadetes',
  imports: [FormsModule, RouterLink, EmptyStateComponent, LoadingSkeletonComponent],
  template: `
    <div class="bg-white rounded shadow-sm">
      <div class="bg-brand-600 text-white px-4 py-3 rounded-t flex items-center justify-between">
        <h1 class="font-semibold">Actualizaciones de cadetes</h1>
        <a routerLink="/cadetes" class="btn-action bg-red-500 hover:bg-red-600">↩ Volver</a>
      </div>

      <div class="p-4 flex flex-col gap-3">
        @if (service.loading()) {
          <app-loading-skeleton [filas]="4" />
        } @else {
          @for (lote of lotes(); track lote.actualizacionId) {
            <div class="border border-gray-200 rounded p-3 flex flex-col gap-2">
              <div class="flex items-center gap-2">
                @if (lote.cadeteFotoUrl) {
                  <img [src]="optimizar(lote.cadeteFotoUrl, 60)" class="w-8 h-8 rounded-full object-cover border" />
                }
                <span class="font-medium text-gray-700">{{ lote.cadeteNombre }} {{ lote.cadeteApellido }}</span>
              </div>

              @for (item of lote.campos; track item.campo.id) {
                <div class="border-t border-gray-100 pt-2 flex items-center justify-between gap-3 flex-wrap">
                  <div class="flex items-center gap-3">
                    <span class="text-sm font-medium text-gray-700" style="width: 10rem">{{ CAMPO_LABEL[item.campo.campo] }}</span>
                    @if (esFoto(item.campo.campo)) {
                      <div class="flex items-center gap-2">
                        @if (item.campo.valorAnterior) {
                          <button type="button" (click)="lightbox.abrir(item.campo.valorAnterior!)">
                            <img [src]="optimizar(item.campo.valorAnterior, 100)" class="w-14 h-14 object-cover rounded border" />
                          </button>
                        } @else {
                          <span class="text-xs text-gray-400">(sin foto)</span>
                        }
                        <span class="text-gray-400">→</span>
                        <button type="button" (click)="lightbox.abrir(item.campo.valorPropuesto)">
                          <img [src]="optimizar(item.campo.valorPropuesto, 100)" class="w-14 h-14 object-cover rounded border-2 border-emerald-400" />
                        </button>
                      </div>
                    } @else {
                      <span class="text-sm text-gray-600">{{ item.campo.valorAnterior ?? '—' }} → <strong>{{ item.campo.valorPropuesto }}</strong></span>
                    }
                  </div>
                  <div class="flex gap-2">
                    <button type="button" class="btn-mini bg-emerald-600 hover:bg-emerald-700" (click)="aprobar(item)">✔ Aprobar</button>
                    <button type="button" class="btn-mini bg-red-600 hover:bg-red-700" (click)="rechazar(item)">✖ Rechazar</button>
                  </div>
                </div>
              }
            </div>
          } @empty {
            <app-empty-state icono="🔄" mensaje="Sin actualizaciones pendientes." hint="Acá van a aparecer los cambios que los cadetes propongan desde la app." />
          }
        }
      </div>
    </div>

    @if (campoARechazar(); as item) {
      <div class="fixed inset-0 bg-black/40 flex items-center justify-center z-50 p-4" (click)="cerrarRechazar()">
        <div class="bg-white rounded-lg shadow-xl w-full max-w-sm overflow-hidden" (click)="$event.stopPropagation()">
          <div class="bg-red-600 text-white px-5 py-4">
            <h2 class="font-semibold">Rechazar {{ CAMPO_LABEL[item.campo.campo] }}</h2>
            <p class="text-red-50 text-sm">{{ item.cadeteNombre }} {{ item.cadeteApellido }}</p>
          </div>
          <div class="p-5 flex flex-col gap-2">
            <label class="flex flex-col gap-1">
              <span class="text-sm font-medium text-gray-700">Motivo (opcional)</span>
              <textarea class="input" rows="3" [(ngModel)]="motivoRechazoModal" name="motivoRechazo"></textarea>
            </label>
          </div>
          <div class="flex justify-end gap-2 px-5 py-3 border-t border-gray-200 bg-white">
            <button type="button" class="btn bg-gray-400 hover:bg-gray-500" (click)="cerrarRechazar()">Volver</button>
            <button type="button" class="btn bg-red-600 hover:bg-red-700" (click)="confirmarRechazar()">Rechazar</button>
          </div>
        </div>
      </div>
    }
  `,
  styles: [
    `
      .btn-action {
        color: white;
        font-size: 0.8125rem;
        font-weight: 500;
        padding: 0.375rem 0.75rem;
        border-radius: 0.25rem;
      }
      .btn-mini {
        font-size: 0.8125rem;
        font-weight: 600;
        padding: 0.35rem 0.75rem;
        border-radius: 0.3rem;
        display: inline-block;
        color: white;
      }
      .input {
        border: 1px solid #d1d5db;
        border-radius: 0.25rem;
        padding: 0.4rem 0.6rem;
        font-size: 0.8125rem;
      }
      textarea.input {
        font-family: inherit;
        resize: vertical;
      }
      .btn {
        color: white;
        font-size: 0.8125rem;
        font-weight: 500;
        padding: 0.5rem 1rem;
        border-radius: 0.25rem;
      }
    `,
  ],
})
export class RevisionCadetesComponent implements OnInit {
  readonly service = inject(CadeteActualizacionService);
  private readonly toast = inject(ToastService);
  readonly lightbox = inject(LightboxService);

  readonly CAMPO_LABEL = CAMPO_LABEL;
  readonly optimizar = optimizarImagen;

  readonly lotes = computed<LoteAgrupado[]>(() => {
    const porLote = new Map<string, LoteAgrupado>();
    for (const item of this.service.pendientes()) {
      let lote = porLote.get(item.actualizacionId);
      if (!lote) {
        lote = {
          actualizacionId: item.actualizacionId,
          cadeteId: item.cadeteId,
          cadeteNombre: item.cadeteNombre,
          cadeteApellido: item.cadeteApellido,
          cadeteFotoUrl: item.cadeteFotoUrl,
          campos: [],
        };
        porLote.set(item.actualizacionId, lote);
      }
      lote.campos.push(item);
    }
    return Array.from(porLote.values());
  });

  readonly campoARechazar = signal<CampoPendienteAdmin | null>(null);
  motivoRechazoModal = '';

  ngOnInit(): void {
    this.service.listar();
  }

  esFoto(campo: string): boolean {
    return CAMPOS_FOTO.has(campo);
  }

  aprobar(item: CampoPendienteAdmin): void {
    this.service.aprobar(item.campo.id, () => {
      this.toast.info('Campo aprobado.');
      this.service.listar();
    });
  }

  rechazar(item: CampoPendienteAdmin): void {
    this.motivoRechazoModal = '';
    this.campoARechazar.set(item);
  }

  cerrarRechazar(): void {
    this.campoARechazar.set(null);
  }

  confirmarRechazar(): void {
    const item = this.campoARechazar();
    if (!item) return;
    const motivo = this.motivoRechazoModal.trim();
    this.service.rechazar(item.campo.id, motivo || null, () => {
      this.toast.info('Campo rechazado.');
      this.service.listar();
    });
    this.campoARechazar.set(null);
  }
}
```

- [ ] **Step 2: Agregar la ruta**

En `src/app/app.routes.ts`, agregar junto a la ruta `cadetes/solicitudes` (línea ~65-69):

```typescript
      {
        path: 'cadetes/actualizaciones',
        loadComponent: () =>
          import('./features/cadetes/revision-cadetes.component').then((m) => m.RevisionCadetesComponent),
      },
```

- [ ] **Step 3: Agregar el link desde Cadetes**

En `src/app/features/cadetes/cadetes.component.ts`, línea 36, junto al botón existente
"📝 Solicitudes de alta":

```typescript
          <a routerLink="/cadetes/solicitudes" class="btn-action bg-indigo-600 hover:bg-indigo-700">📝 Solicitudes de alta</a>
          <a routerLink="/cadetes/actualizaciones" class="btn-action bg-purple-600 hover:bg-purple-700">🔄 Actualizaciones pendientes</a>
          <a routerLink="/cadetes/nuevo" class="btn-action bg-emerald-600 hover:bg-emerald-700">+ Nuevo cadete</a>
```

- [ ] **Step 4: Verificar que compila**

Run: `npm run build`
Expected: exit 0, sin errores de TypeScript.

- [ ] **Step 5: Verificación manual**

Con `npm start` corriendo y logueado como admin: entrar a `/cadetes`, click en
"🔄 Actualizaciones pendientes", confirmar que la pantalla carga (vacía está bien si no hay
datos de prueba todavía — eso se prueba de punta a punta en el Task 8).

- [ ] **Step 6: Commit**

```bash
git add src/app/features/cadetes/revision-cadetes.component.ts src/app/app.routes.ts src/app/features/cadetes/cadetes.component.ts
git commit -m "Pantalla de revision de actualizaciones de cadete, con link desde Cadetes"
```

---

## cadete-app

### Task 6: Mover los helpers de foto a un util compartido

**Files:**
- Create: `app/src/main/java/com/cadeteria/cadete/util/FotosCapturaUtil.kt`
- Modify: `app/src/main/java/com/cadeteria/cadete/ui/viaje/ViajeScreen.kt`

**Interfaces:**
- Produces: `crearArchivoFotoTemporal(context: Context): Pair<File, Uri>`,
  `corregirRotacionExif(archivo: File): Unit`, `decodificarFotoCorregida(archivo: File):
  Bitmap?` — funciones top-level públicas del paquete `com.cadeteria.cadete.util`. Las consume
  el Task 8 (PerfilScreen) además de `ViajeScreen.kt`, que ya las usa hoy.

- [ ] **Step 1: Crear el archivo compartido**

`app/src/main/java/com/cadeteria/cadete/util/FotosCapturaUtil.kt`:

```kotlin
package com.cadeteria.cadete.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import java.io.File
import java.io.FileOutputStream

/**
 * Compartido entre ViajeScreen (fotos de retiro/entrega) y PerfilScreen ("Actualizar mis
 * datos") — con TakePicture() + FileProvider en vez de TakePicturePreview() (el thumbnail de
 * baja resolución que ignoraba el tag EXIF de orientación, bug reportado 2026-09-23).
 */
fun crearArchivoFotoTemporal(context: Context): Pair<File, Uri> {
    val archivo = File(context.cacheDir, "foto_${System.currentTimeMillis()}.jpg")
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", archivo)
    return archivo to uri
}

/**
 * La cámara guarda el archivo con la orientación real del sensor marcada en el tag EXIF, sin
 * rotar los píxeles — hay que leer ese tag y rotar la imagen de verdad antes de mostrarla o
 * subirla, si no cualquier visor que no respete EXIF (o el que la reprocesa, como Cloudinary
 * en algunos casos) la muestra girada.
 */
fun corregirRotacionExif(archivo: File) {
    val grados = try {
        when (ExifInterface(archivo.absolutePath).getAttributeInt(
            ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL
        )) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90
            ExifInterface.ORIENTATION_ROTATE_180 -> 180
            ExifInterface.ORIENTATION_ROTATE_270 -> 270
            else -> 0
        }
    } catch (e: Exception) {
        0
    }
    if (grados == 0) return
    val original = BitmapFactory.decodeFile(archivo.absolutePath) ?: return
    val matriz = Matrix().apply { postRotate(grados.toFloat()) }
    val rotado = Bitmap.createBitmap(original, 0, 0, original.width, original.height, matriz, true)
    FileOutputStream(archivo).use { out -> rotado.compress(Bitmap.CompressFormat.JPEG, 90, out) }
    original.recycle()
    rotado.recycle()
}

/** Bitmap ya corregido, para la vista previa en pantalla — el archivo en disco es lo que se sube. */
fun decodificarFotoCorregida(archivo: File): Bitmap? {
    corregirRotacionExif(archivo)
    return BitmapFactory.decodeFile(archivo.absolutePath)
}
```

- [ ] **Step 2: Sacar las 3 funciones de `ViajeScreen.kt` y usar las del util**

En `app/src/main/java/com/cadeteria/cadete/ui/viaje/ViajeScreen.kt`:

1. Borrar las líneas 888 a 933 (el bloque completo: el comentario doc de
   `crearArchivoFotoTemporal` + las 3 funciones `private fun`, hasta la línea en blanco antes
   del cierre del archivo). La función `guardarBitmapTemporal` (líneas 882-886, justo antes)
   **no se toca** — es una función distinta, no forma parte de este movimiento.
2. Agregar estos imports (junto a los demás `import androidx.exifinterface...` que quedan
   ahora sin uso — sacarlos también: `android.graphics.BitmapFactory`,
   `android.graphics.Matrix`, `androidx.core.content.FileProvider`,
   `androidx.exifinterface.media.ExifInterface` ya no se usan en este archivo, sacar esas 4
   líneas de import):

```kotlin
import com.cadeteria.cadete.util.corregirRotacionExif
import com.cadeteria.cadete.util.crearArchivoFotoTemporal
import com.cadeteria.cadete.util.decodificarFotoCorregida
```

Los 4 call sites existentes (`decodificarFotoCorregida` en las líneas ~507 y ~616,
`crearArchivoFotoTemporal` en las líneas ~530 y ~643) no cambian — siguen llamándose igual,
ahora resueltos por el import en vez de ser funciones locales del archivo.

- [ ] **Step 3: Compilar**

Run: `gradlew.bat assembleDebug` (desde `cadete-app/`)
Expected: `BUILD SUCCESSFUL`, sin errores de "unresolved reference" ni warnings de "unused
import" para los 4 imports sacados.

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/cadeteria/cadete/util/FotosCapturaUtil.kt app/src/main/java/com/cadeteria/cadete/ui/viaje/ViajeScreen.kt
git commit -m "Mueve los helpers de captura/rotacion de fotos a un util compartido (los va a usar PerfilScreen tambien)"
```

---

### Task 7: DTOs, ApiService y CadeteRepository

**Files:**
- Modify: `app/src/main/java/com/cadeteria/cadete/data/remote/dto/CadeteDtos.kt`
- Modify: `app/src/main/java/com/cadeteria/cadete/data/remote/ApiService.kt`
- Modify: `app/src/main/java/com/cadeteria/cadete/data/repository/CadeteRepository.kt`

**Interfaces:**
- Consumes: endpoints del Task 3 (`POST/GET /api/cadetes/me/actualizaciones`).
- Produces: `CadeteRepository.crearActualizacion(req): Result<CadeteActualizacionDto>`,
  `CadeteRepository.misActualizaciones(): Result<List<CadeteActualizacionDto>>`, y los campos
  nuevos de `CadeteDto` (`vehiculoAnio`, `fotoTarjetaVerdeDorsoUrl`). Los consume el Task 8.

- [ ] **Step 1: Agregar los campos que faltan a `CadeteDto` y las nuevas DTOs**

En `app/src/main/java/com/cadeteria/cadete/data/remote/dto/CadeteDtos.kt`, dentro de
`CadeteDto` (agregar después de `fotoTarjetaVerdeUrl`, antes de `username` — el backend ya
manda estos 2 campos desde esta misma sesión, `CadeteResponse` los tiene, solo faltaba
espejarlos acá):

```kotlin
    val fotoTarjetaVerdeDorsoUrl: String?,
    val vehiculoAnio: Int?,
```

Al final del archivo (después de `CuentaRequest`), agregar las DTOs nuevas:

```kotlin
/** Espeja CadeteActualizacionDtos.ActualizacionCadeteRequest (POST /api/cadetes/me/actualizaciones). */
data class ActualizacionCadeteRequestDto(
    val fotoUrl: String? = null,
    val fotoVehiculoUrl: String? = null,
    val fotoTarjetaVerdeUrl: String? = null,
    val fotoTarjetaVerdeDorsoUrl: String? = null,
    val vehiculoMarca: String? = null,
    val vehiculoModelo: String? = null,
    val vehiculoColor: String? = null,
    val vehiculoPatente: String? = null,
    val vehiculoAnio: Int? = null,
)

/** Espeja CadeteActualizacionDtos.CampoResponse. */
data class CadeteActualizacionCampoDto(
    val id: String,
    val campo: String,
    val valorAnterior: String?,
    val valorPropuesto: String,
    val estado: String,
    val motivoRechazo: String?,
    val resueltoEn: String?,
    val resueltoPorUsername: String?,
)

/** Espeja CadeteActualizacionDtos.ActualizacionResponse (GET/POST /api/cadetes/me/actualizaciones). */
data class CadeteActualizacionDto(
    val id: String,
    val creadoEn: String,
    val estado: String,
    val campos: List<CadeteActualizacionCampoDto>,
)
```

- [ ] **Step 2: Agregar los endpoints a `ApiService`**

En `app/src/main/java/com/cadeteria/cadete/data/remote/ApiService.kt`, después de
`actualizarCuenta` (línea 66):

```kotlin
    @POST("api/cadetes/me/actualizaciones")
    suspend fun crearActualizacion(@Body req: ActualizacionCadeteRequestDto): CadeteActualizacionDto

    @GET("api/cadetes/me/actualizaciones")
    suspend fun misActualizaciones(): List<CadeteActualizacionDto>
```

- [ ] **Step 3: Agregar los métodos a `CadeteRepository`**

En `app/src/main/java/com/cadeteria/cadete/data/repository/CadeteRepository.kt`, después de
`actualizarCuenta` (línea 37) — agregar también el import que falta al tope del archivo:

```kotlin
import com.cadeteria.cadete.data.remote.dto.ActualizacionCadeteRequestDto
import com.cadeteria.cadete.data.remote.dto.CadeteActualizacionDto
```

```kotlin
    suspend fun crearActualizacion(req: ActualizacionCadeteRequestDto): Result<CadeteActualizacionDto> =
        runCatching { retrofitProvider.apiService().crearActualizacion(req) }

    suspend fun misActualizaciones(): Result<List<CadeteActualizacionDto>> =
        runCatching { retrofitProvider.apiService().misActualizaciones() }
```

- [ ] **Step 4: Compilar**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/com/cadeteria/cadete/data/remote/dto/CadeteDtos.kt \
        app/src/main/java/com/cadeteria/cadete/data/remote/ApiService.kt \
        app/src/main/java/com/cadeteria/cadete/data/repository/CadeteRepository.kt
git commit -m "DTOs y endpoints de actualizacion de datos del cadete en la app"
```

---

### Task 8: `PerfilViewModel` + sección "Actualizar mis datos" en `PerfilScreen`

**Files:**
- Modify: `app/src/main/java/com/cadeteria/cadete/ui/perfil/PerfilViewModel.kt`
- Modify: `app/src/main/java/com/cadeteria/cadete/ui/perfil/PerfilScreen.kt`

**Interfaces:**
- Consumes: `CadeteRepository.crearActualizacion/misActualizaciones` (Task 7),
  `app.cloudinaryUploader.subir(cloudName, uploadPreset, archivo)` (ya existe, `CadeteApp.kt`),
  `crearArchivoFotoTemporal`/`corregirRotacionExif` (Task 6).

- [ ] **Step 1: Extender `PerfilUiState` y `PerfilViewModel`**

En `app/src/main/java/com/cadeteria/cadete/ui/perfil/PerfilViewModel.kt`, reemplazar el bloque
completo del archivo por este (agrega `guardandoActualizacion`/`subiendoFoto` al state, el
`StateFlow` de `misActualizaciones`, y los métodos nuevos; todo lo demás queda igual):

```kotlin
package com.cadeteria.cadete.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cadeteria.cadete.CadeteApp
import com.cadeteria.cadete.data.remote.dto.ActualizacionCadeteRequestDto
import com.cadeteria.cadete.data.remote.dto.CadeteActualizacionCampoDto
import com.cadeteria.cadete.data.remote.dto.CadeteActualizacionDto
import com.cadeteria.cadete.data.remote.dto.CadeteConfigDto
import com.cadeteria.cadete.data.remote.dto.CadeteDto
import com.cadeteria.cadete.data.remote.dto.MiSemanaDto
import com.cadeteria.cadete.ui.theme.TemaApp
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

data class PerfilUiState(
    val guardandoPassword: Boolean = false,
    val guardandoTelefono: Boolean = false,
    val guardandoCuenta: Boolean = false,
    val guardandoActualizacion: Boolean = false,
    /** Qué campo de "Actualizar mis datos" está subiendo la foto ahora mismo (para el spinner puntual), o null. */
    val subiendoFoto: String? = null,
    val mensaje: String? = null,
    val error: String? = null,
)

class PerfilViewModel(private val app: CadeteApp) : ViewModel() {

    private val _cadete = MutableStateFlow<CadeteDto?>(null)
    val cadete: StateFlow<CadeteDto?> = _cadete

    private val _miSemana = MutableStateFlow<MiSemanaDto?>(null)
    val miSemana: StateFlow<MiSemanaDto?> = _miSemana

    /** Cuota semanal / % de comisión configurados — para mostrar "cuánto falta" en la ficha de pago (ronda 7). */
    private val _config = MutableStateFlow<CadeteConfigDto?>(null)
    val config: StateFlow<CadeteConfigDto?> = _config

    /** Historial de "Actualizar mis datos" (mejora 2026-09-23) — el más reciente primero. */
    private val _misActualizaciones = MutableStateFlow<List<CadeteActualizacionDto>>(emptyList())
    val misActualizaciones: StateFlow<List<CadeteActualizacionDto>> = _misActualizaciones

    private val _uiState = MutableStateFlow(PerfilUiState())
    val uiState: StateFlow<PerfilUiState> = _uiState

    val tema: StateFlow<TemaApp> = app.sessionManager.tema
        .stateIn(viewModelScope, SharingStarted.Eagerly, TemaApp.SISTEMA)

    init {
        cargar()
    }

    fun cambiarTema(nuevoTema: TemaApp) {
        viewModelScope.launch { app.sessionManager.setTema(nuevoTema) }
    }

    fun cargar() {
        viewModelScope.launch {
            app.cadeteRepository.miPerfil().onSuccess { _cadete.value = it }
            app.cadeteRepository.miPagoSemanal().onSuccess { _miSemana.value = it }
            app.cadeteRepository.miConfiguracion().onSuccess { _config.value = it }
            app.cadeteRepository.misActualizaciones().onSuccess { _misActualizaciones.value = it }
        }
    }

    fun cambiarPassword(actual: String, nueva: String, onOk: () -> Unit) {
        _uiState.value = _uiState.value.copy(guardandoPassword = true, error = null, mensaje = null)
        viewModelScope.launch {
            app.cadeteRepository.cambiarPassword(actual, nueva)
                .onSuccess {
                    _uiState.value = _uiState.value.copy(guardandoPassword = false, mensaje = "Contraseña actualizada.")
                    onOk()
                }
                .onFailure {
                    _uiState.value = _uiState.value.copy(guardandoPassword = false, error = "No se pudo cambiar la contraseña — revisá la actual.")
                }
        }
    }

    fun actualizarTelefono(telefono: String) {
        _uiState.value = _uiState.value.copy(guardandoTelefono = true, error = null, mensaje = null)
        viewModelScope.launch {
            app.cadeteRepository.actualizarTelefono(telefono)
                .onSuccess {
                    _cadete.value = it
                    _uiState.value = _uiState.value.copy(guardandoTelefono = false, mensaje = "Teléfono actualizado.")
                }
                .onFailure { _uiState.value = _uiState.value.copy(guardandoTelefono = false, error = "No se pudo actualizar el teléfono.") }
        }
    }

    fun actualizarCuenta(cbu: String, aliasCbu: String) {
        _uiState.value = _uiState.value.copy(guardandoCuenta = true, error = null, mensaje = null)
        viewModelScope.launch {
            app.cadeteRepository.actualizarCuenta(cbu.ifBlank { null }, aliasCbu.ifBlank { null })
                .onSuccess {
                    _cadete.value = it
                    _uiState.value = _uiState.value.copy(guardandoCuenta = false, mensaje = "Datos de cobro guardados.")
                }
                .onFailure { _uiState.value = _uiState.value.copy(guardandoCuenta = false, error = "No se pudieron guardar los datos de cobro.") }
        }
    }

    /** Sube la foto a Cloudinary (mismo flujo que las fotos de viaje) y manda la propuesta al toque. */
    fun proponerFoto(campo: String, archivo: File) {
        viewModelScope.launch {
            val cloudName = _config.value?.cloudinaryCloudName
            val uploadPreset = _config.value?.cloudinaryUploadPreset
            if (cloudName == null || uploadPreset == null) {
                _uiState.value = _uiState.value.copy(error = "Todavía no cargó la configuración — probá de nuevo en un segundo.")
                return@launch
            }
            _uiState.value = _uiState.value.copy(subiendoFoto = campo, error = null, mensaje = null)
            val resultado = app.cloudinaryUploader.subir(cloudName, uploadPreset, archivo)
            _uiState.value = _uiState.value.copy(subiendoFoto = null)
            val url = resultado.getOrNull()
            if (url == null) {
                _uiState.value = _uiState.value.copy(error = "No se pudo subir la foto — probá de nuevo.")
                return@launch
            }
            val req = when (campo) {
                "FOTO_PERFIL" -> ActualizacionCadeteRequestDto(fotoUrl = url)
                "FOTO_VEHICULO" -> ActualizacionCadeteRequestDto(fotoVehiculoUrl = url)
                "FOTO_TARJETA_VERDE" -> ActualizacionCadeteRequestDto(fotoTarjetaVerdeUrl = url)
                "FOTO_TARJETA_VERDE_DORSO" -> ActualizacionCadeteRequestDto(fotoTarjetaVerdeDorsoUrl = url)
                else -> return@launch
            }
            enviarActualizacion(req)
        }
    }

    fun proponerDatosVehiculo(marca: String, modelo: String, color: String, patente: String, anio: String) {
        viewModelScope.launch {
            enviarActualizacion(
                ActualizacionCadeteRequestDto(
                    vehiculoMarca = marca.ifBlank { null },
                    vehiculoModelo = modelo.ifBlank { null },
                    vehiculoColor = color.ifBlank { null },
                    vehiculoPatente = patente.ifBlank { null },
                    vehiculoAnio = anio.toIntOrNull(),
                ),
            )
        }
    }

    private suspend fun enviarActualizacion(req: ActualizacionCadeteRequestDto) {
        _uiState.value = _uiState.value.copy(guardandoActualizacion = true, error = null, mensaje = null)
        app.cadeteRepository.crearActualizacion(req)
            .onSuccess {
                _uiState.value = _uiState.value.copy(guardandoActualizacion = false, mensaje = "Enviado — queda pendiente de revisión del admin.")
                app.cadeteRepository.misActualizaciones().onSuccess { lista -> _misActualizaciones.value = lista }
            }
            .onFailure {
                _uiState.value = _uiState.value.copy(guardandoActualizacion = false, error = "No se pudo enviar la actualización — puede que ya tengas una pendiente de revisión.")
            }
    }

    /** Último estado (pendiente/rechazado) de un campo puntual, para el chip debajo de cada uno. */
    fun ultimoEstadoDe(campo: String): CadeteActualizacionCampoDto? =
        _misActualizaciones.value.firstOrNull()?.campos?.firstOrNull { it.campo == campo }

    fun hayAlgoPendiente(): Boolean =
        _misActualizaciones.value.firstOrNull()?.campos?.any { it.estado == "PENDIENTE" } ?: false
}
```

- [ ] **Step 2: Compilar (todavía sin tocar la UI) para confirmar que el ViewModel es válido**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 3: Agregar la sección "Actualizar mis datos" a `PerfilScreen.kt`**

3a. Agregar estos imports (junto a los que ya están, orden alfabético dentro de cada bloque
como el resto del archivo):

```kotlin
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.RoundedCornerShape
import com.cadeteria.cadete.data.remote.dto.CadeteActualizacionCampoDto
import com.cadeteria.cadete.util.corregirRotacionExif
import com.cadeteria.cadete.util.crearArchivoFotoTemporal
import java.io.File
```

3b. Dentro de `PerfilScreen`, junto a los demás `var ... by remember { mutableStateOf("") }`
(después de la línea de `aliasCbu`), agregar:

```kotlin
    var vehiculoMarca by remember { mutableStateOf("") }
    var vehiculoModelo by remember { mutableStateOf("") }
    var vehiculoColorNuevo by remember { mutableStateOf("") }
    var vehiculoPatenteNuevo by remember { mutableStateOf("") }
    var vehiculoAnioNuevo by remember { mutableStateOf("") }
```

3c. Dentro del `LaunchedEffect(cadete?.id) { cadete?.let { ... } }` ya existente, agregar estas
líneas junto a las de `telefono`/`cbu`/`aliasCbu`:

```kotlin
            vehiculoMarca = it.vehiculoMarca ?: ""
            vehiculoModelo = it.vehiculoModelo ?: ""
            vehiculoColorNuevo = it.vehiculoColor ?: ""
            vehiculoPatenteNuevo = it.vehiculoPatente ?: ""
            vehiculoAnioNuevo = it.vehiculoAnio?.toString() ?: ""
```

3d. Justo después de la `SeccionCard(titulo = "Datos de cobro", ...)` ya existente (y su
`Spacer(Modifier.height(16.dp))` de cierre), agregar la nueva sección:

```kotlin
            cadete?.let { c ->
                val esMoto = c.tipoVehiculo.id == "MOTO"
                SeccionCard(titulo = "Actualizar mis datos", icono = Icons.Filled.DirectionsBike) {
                    Text(
                        "Los cambios quedan pendientes de revisión del admin antes de aplicarse.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Gray500,
                    )
                    Spacer(Modifier.height(10.dp))

                    CampoFotoActualizable(
                        etiqueta = "Foto de perfil",
                        fotoActualUrl = c.fotoUrl,
                        estadoPendiente = vm.ultimoEstadoDe("FOTO_PERFIL"),
                        subiendo = state.subiendoFoto == "FOTO_PERFIL",
                        onFotoElegida = { vm.proponerFoto("FOTO_PERFIL", it) },
                    )

                    if (esMoto) {
                        CampoFotoActualizable(
                            etiqueta = "Foto del vehículo",
                            fotoActualUrl = c.fotoVehiculoUrl,
                            estadoPendiente = vm.ultimoEstadoDe("FOTO_VEHICULO"),
                            subiendo = state.subiendoFoto == "FOTO_VEHICULO",
                            onFotoElegida = { vm.proponerFoto("FOTO_VEHICULO", it) },
                        )
                        CampoFotoActualizable(
                            etiqueta = "Tarjeta verde — frente",
                            fotoActualUrl = c.fotoTarjetaVerdeUrl,
                            estadoPendiente = vm.ultimoEstadoDe("FOTO_TARJETA_VERDE"),
                            subiendo = state.subiendoFoto == "FOTO_TARJETA_VERDE",
                            onFotoElegida = { vm.proponerFoto("FOTO_TARJETA_VERDE", it) },
                        )
                        CampoFotoActualizable(
                            etiqueta = "Tarjeta verde — dorso",
                            fotoActualUrl = c.fotoTarjetaVerdeDorsoUrl,
                            estadoPendiente = vm.ultimoEstadoDe("FOTO_TARJETA_VERDE_DORSO"),
                            subiendo = state.subiendoFoto == "FOTO_TARJETA_VERDE_DORSO",
                            onFotoElegida = { vm.proponerFoto("FOTO_TARJETA_VERDE_DORSO", it) },
                        )

                        Spacer(Modifier.height(10.dp))
                        OutlinedTextField(value = vehiculoMarca, onValueChange = { vehiculoMarca = it }, label = { Text("Marca") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = vehiculoModelo, onValueChange = { vehiculoModelo = it }, label = { Text("Modelo") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = vehiculoColorNuevo, onValueChange = { vehiculoColorNuevo = it }, label = { Text("Color") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = vehiculoPatenteNuevo, onValueChange = { vehiculoPatenteNuevo = it }, label = { Text("Patente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(value = vehiculoAnioNuevo, onValueChange = { vehiculoAnioNuevo = it }, label = { Text("Año") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                        Spacer(Modifier.height(10.dp))
                        Button(
                            onClick = { vm.proponerDatosVehiculo(vehiculoMarca, vehiculoModelo, vehiculoColorNuevo, vehiculoPatenteNuevo, vehiculoAnioNuevo) },
                            enabled = !state.guardandoActualizacion && !vm.hayAlgoPendiente(),
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text(if (state.guardandoActualizacion) "Enviando…" else "Enviar datos del vehículo para revisión") }
                    }

                    if (vm.hayAlgoPendiente()) {
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Ya tenés una actualización esperando revisión — esperá a que se resuelva antes de mandar otra.",
                            style = MaterialTheme.typography.bodySmall,
                            color = Gray500,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
```

3e. Agregar el composable auxiliar `CampoFotoActualizable`, al final del archivo (después de
`DatoPerfil`):

```kotlin
@Composable
private fun CampoFotoActualizable(
    etiqueta: String,
    fotoActualUrl: String?,
    estadoPendiente: CadeteActualizacionCampoDto?,
    subiendo: Boolean,
    onFotoElegida: (File) -> Unit,
) {
    val context = LocalContext.current
    var archivoTemp by remember { mutableStateOf<File?>(null) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { exito ->
        if (exito) {
            archivoTemp?.let {
                corregirRotacionExif(it)
                onFotoElegida(it)
            }
        }
    }

    Column(Modifier.padding(vertical = 6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (fotoActualUrl != null) {
                AsyncImage(
                    model = optimizarImagen(fotoActualUrl, 160),
                    contentDescription = etiqueta,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)),
                )
            } else {
                Box(Modifier.size(56.dp).clip(RoundedCornerShape(8.dp)).background(Color.LightGray))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(etiqueta, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                estadoPendiente?.let {
                    when (it.estado) {
                        "PENDIENTE" -> Text("⏳ Pendiente de revisión", style = MaterialTheme.typography.bodySmall, color = Amber500)
                        "RECHAZADO" -> Text(
                            "❌ Rechazado${it.motivoRechazo?.let { m -> ": $m" } ?: ""}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                        else -> {}
                    }
                }
            }
            OutlinedButton(
                enabled = !subiendo,
                onClick = {
                    val (archivo, uri) = crearArchivoFotoTemporal(context)
                    archivoTemp = archivo
                    launcher.launch(uri)
                },
            ) { Text(if (subiendo) "Subiendo…" else "Cambiar") }
        }
    }
}
```

- [ ] **Step 4: Compilar**

Run: `gradlew.bat assembleDebug`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Instalar y probar en el celular/emulador de prueba**

Run: `adb install -r app/build/outputs/apk/debug/app-debug.apk`

Loguearse como cadete, ir a Perfil, confirmar:
- Se ve la nueva tarjeta "Actualizar mis datos".
- Con un cadete BICI, solo aparece "Foto de perfil" (sin foto de vehículo/tarjeta
  verde/datos del vehículo). Con un cadete MOTO, aparecen los 4 campos de foto + los 5 campos
  de texto del vehículo.
- Tocar "Cambiar" en una foto abre la cámara, saca la foto, y queda "Subiendo…" y después
  aparece "⏳ Pendiente de revisión" debajo de ese campo.
- Con algo pendiente, el botón "Enviar datos del vehículo para revisión" queda deshabilitado
  y aparece el aviso de "ya tenés una actualización esperando".
- Desde el panel admin (`/cadetes/actualizaciones`, Task 5), aprobar o rechazar ese campo y
  confirmar que, al volver a entrar a Perfil en la app, se refleja (foto nueva si se aprobó, o
  "❌ Rechazado: <motivo>" si se rechazó).

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/com/cadeteria/cadete/ui/perfil/PerfilViewModel.kt app/src/main/java/com/cadeteria/cadete/ui/perfil/PerfilScreen.kt
git commit -m "Seccion 'Actualizar mis datos' en Perfil: fotos + datos del vehiculo, con revision del admin"
```
