# Optimización de datos e imágenes — Plan de implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Dejar de servir las fotos originales de Cloudinary en miniaturas, y sacar del listado de cadetes los campos que no se usan.

**Architecture:** Cloudinary transforma por URL — se inserta una transformación (`f_auto,q_auto,c_limit,w_N`) en la URL antes de usarla. No hay migración ni re-subida: **arregla las fotos que ya están subidas**. Un helper puro por repo (panel y app), aplicado en los puntos de uso. Aparte, un `fromListado()` en el DTO de cadetes que sigue el patrón de `PedidoResponse.fromResumen()`.

**Tech Stack:** Angular 19 standalone + Karma/Jasmine · Kotlin + Compose + Coil + JUnit 4 · Spring Boot 3.3.5 + JUnit 5 + Mockito.

**Spec:** [`spec-optimizacion-datos.md`](./spec-optimizacion-datos.md) — leerlo antes de empezar. El spec viaja con el plan.

## Global Constraints

- **No re-subir ni migrar fotos.** La solución es construir distinto la URL.
- **El helper nunca rompe una URL que hoy funciona**: si no es de Cloudinary, o ya tiene una transformación, se devuelve tal cual. Un helper que falla es peor que el problema.
- **Anchos por caso de uso** (del spec, sección 2): miniatura de listado `120` · avatar `240` · lightbox / "ver foto" `1600` · firma `800`.
- **Cuatro repos/áreas**: `admin-front` (Angular), `cadete-app` (Android), `cadeteria` (backend). Cada uno con su propio commit.
- **Backend usa JUnit 5** (`org.junit.jupiter.api`), **la app Android usa JUnit 4** (`junit:junit:4.13.2`, ya declarado en `app/build.gradle.kts:117`). No mezclar.
- **Lo que ya está hecho y NO se toca**: `PedidoResponse.fromResumen()`, gzip, el parche incremental por WebSocket, `CadeteResumen`.

---

### Task 1: Helper `optimizarImagen` en el panel

**Files:**
- Create: `admin-front/src/app/core/utils/imagen.util.ts`
- Test: `admin-front/src/app/core/utils/imagen.util.spec.ts`

**Interfaces:**
- Consumes: nada.
- Produces: `optimizarImagen(url: string | null | undefined, ancho: number): string | null | undefined` — usada por la Task 2.

- [ ] **Step 1: Escribir el test que falla**

Crear `admin-front/src/app/core/utils/imagen.util.spec.ts`:

```ts
import { optimizarImagen } from './imagen.util';

describe('optimizarImagen', () => {
  const CON_VERSION = 'https://res.cloudinary.com/demo/image/upload/v1234567/foto.jpg';

  it('inserta la transformación antes de la versión', () => {
    expect(optimizarImagen(CON_VERSION, 120)).toBe(
      'https://res.cloudinary.com/demo/image/upload/f_auto,q_auto,c_limit,w_120/v1234567/foto.jpg',
    );
  });

  it('no duplica si la URL ya trae una transformación', () => {
    const ya = 'https://res.cloudinary.com/demo/image/upload/f_auto,q_auto/v1234567/foto.jpg';
    expect(optimizarImagen(ya, 120)).toBe(ya);
  });

  it('deja igual una URL que no es de Cloudinary', () => {
    const otra = 'https://ejemplo.com/foto.jpg';
    expect(optimizarImagen(otra, 120)).toBe(otra);
  });

  it('deja igual una URL de Cloudinary sin versión ni transformación', () => {
    // Sin un segmento reconocible después de /upload/, insertar sería adivinar.
    const rara = 'https://res.cloudinary.com/demo/image/upload/foto.jpg';
    expect(optimizarImagen(rara, 120)).toBe(rara);
  });

  it('devuelve null, undefined y vacío tal cual', () => {
    expect(optimizarImagen(null, 120)).toBeNull();
    expect(optimizarImagen(undefined, 120)).toBeUndefined();
    expect(optimizarImagen('', 120)).toBe('');
  });

  it('respeta el ancho que se le pasa', () => {
    expect(optimizarImagen(CON_VERSION, 1600)).toContain('w_1600');
    expect(optimizarImagen(CON_VERSION, 240)).toContain('w_240');
  });
});
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd admin-front && npx ng test --watch=false --browsers=ChromeHeadless`
Expected: FAIL — `Cannot find module './imagen.util'`.

- [ ] **Step 3: Implementar el helper**

Crear `admin-front/src/app/core/utils/imagen.util.ts`:

```ts
/**
 * Inserta una transformación de Cloudinary en una URL para no servir el original.
 *
 * El problema que resuelve: las fotos se suben desde el celular y se guardan/sirven
 * crudas. Una miniatura de 56 px estaba bajando una foto de 2-5 MB — el navegador tira
 * el 99,9% de esos bytes al renderizar.
 *
 * Cloudinary transforma POR URL, así que esto arregla también las fotos YA subidas: no
 * hay migración, no se re-sube nada.
 *
 * Si la URL no es de Cloudinary, o ya trae una transformación, se devuelve tal cual —
 * romper una URL que hoy funciona sería peor que el problema que esto arregla.
 */
const MARCA = '/image/upload/';

export function optimizarImagen(url: string | null | undefined, ancho: number): string | null | undefined {
  if (!url || !url.includes('res.cloudinary.com')) return url;

  const i = url.indexOf(MARCA);
  if (i === -1) return url;

  const resto = url.slice(i + MARCA.length);
  const barra = resto.indexOf('/');
  if (barra === -1) return url;

  const primero = resto.slice(0, barra);
  // "v1234567" es la versión del asset. Cualquier otro segmento con "_" o "," ya es una
  // transformación puesta por otro lado.
  const esVersion = /^v\d+$/.test(primero);
  if (!esVersion && /[_,]/.test(primero)) return url;

  const transformacion = `f_auto,q_auto,c_limit,w_${ancho}`;
  return `${url.slice(0, i + MARCA.length)}${transformacion}/${resto}`;
}
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd admin-front && npx ng test --watch=false --browsers=ChromeHeadless`
Expected: PASS — 6 specs, 0 failures.

- [ ] **Step 5: Commit**

```bash
cd admin-front
git add src/app/core/utils/imagen.util.ts src/app/core/utils/imagen.util.spec.ts
git commit -m "Helper para transformar imagenes de Cloudinary en la entrega"
```

---

### Task 2: Aplicar el helper en los puntos de uso del panel

**Files:**
- Modify: `admin-front/src/app/features/registro-cadete/solicitudes-cadete.component.ts`
- Modify: `admin-front/src/app/features/seguimiento/seguimiento.component.ts`
- Modify: `admin-front/src/app/shared/image-upload.component.ts`
- Modify: `admin-front/src/app/shared/lightbox.component.ts`
- Modify: `admin-front/src/app/features/chat/chat.component.ts`

**Interfaces:**
- Consumes: `optimizarImagen(url, ancho)` de la Task 1.
- Produces: nada.

**Nota sobre templates Angular:** una función no es visible desde el template de un
componente standalone. Se expone como propiedad de la clase:

```ts
readonly optimizar = optimizarImagen;
```

y se usa `[src]="optimizar(s.fotoUrl, 120)"`.

- [ ] **Step 1: `solicitudes-cadete.component.ts` (el caso peor — 4 fotos en 56 px)**

Importar el helper, agregar `readonly optimizar = optimizarImagen;` a la clase, y en las 4
imágenes de las líneas 78-87 cambiar `[src]="s.fotoUrl"` → `[src]="optimizar(s.fotoUrl, 120)"`
(una por cada: `fotoUrl`, `fotoVehiculoUrl`, `fotoCarnetUrl`, `fotoTarjetaVerdeUrl`).

Las 4 son `w-14 h-14` = 56 px. Con `w_120` sobra para pantallas retina.

- [ ] **Step 2: `seguimiento.component.ts` (página pública — la ve el cliente)**

- Línea 126, avatar del cadete (`w-16 h-16` = 64 px): `[src]="optimizar(s.cadete.fotoUrl, 240)"`
- Línea 166, foto de entrega (`max-h-64`): `[src]="optimizar(s.entregaFotoUrl, 1600)"`

Ojo: esta página la abre un cliente en un celular con datos móviles. Es de las que más se
beneficia.

- [ ] **Step 3: `image-upload.component.ts` (línea 11, `w-16 h-16`)**

`[src]="optimizar(value, 240)"`.

- [ ] **Step 4: `lightbox.component.ts` (línea 15 — acá SÍ se quiere grande)**

`[src]="optimizar(url, 1600)"`.

Los llamadores siguen pasando la URL cruda (`lightbox.abrir(s.fotoUrl!)`): la miniatura usa
`w_120` y el lightbox `w_1600`, cada uno con su ancho. Es el comportamiento correcto — no hay
que tocar los llamadores.

- [ ] **Step 5: `chat.component.ts` (línea 59, `max-h-48`)**

`[src]="optimizar(m.imagenUrl, 1200)"`.

- [ ] **Step 6: Verificar que compila**

Run: `cd admin-front && npx tsc --noEmit && npx ng build`
Expected: sin errores. (`tsc --noEmit` es el chequeo que ya usa el proyecto.)

- [ ] **Step 7: Verificar a ojo en el navegador**

Levantar backend + panel, abrir la pantalla de **solicitudes de cadete** con al menos una
postulante con fotos, y en la pestaña Network confirmar que las 4 imágenes pesan **KB, no
MB**. Sin este paso no está verificado — el ahorro es el punto entero de la tarea.

- [ ] **Step 8: Commit**

```bash
cd admin-front
git add src/app/features src/app/shared
git commit -m "Miniaturas del panel usan transformacion de Cloudinary (de MB a KB)"
```

---

### Task 3: Helper de imágenes en la app del cadete

**Files:**
- Create: `cadete-app/app/src/main/java/com/cadeteria/cadete/util/Imagenes.kt`
- Test: `cadete-app/app/src/test/java/com/cadeteria/cadete/util/ImagenesTest.kt`
- Modify: `cadete-app/app/src/main/java/com/cadeteria/cadete/ui/perfil/PerfilScreen.kt:295`
- Modify: `cadete-app/app/src/main/java/com/cadeteria/cadete/ui/chat/ChatScreen.kt:195`

**Interfaces:**
- Consumes: nada.
- Produces: `optimizarImagen(url: String?, ancho: Int): String?` — misma semántica que la del
  panel.

**Nota:** `app/src/test/` **no existe todavía** — hay que crearlo. `testImplementation("junit:junit:4.13.2")`
ya está declarado en `app/build.gradle.kts:117`, así que no hace falta tocar el build. JUnit
**4**, no 5.

- [ ] **Step 1: Escribir el test que falla**

Crear `cadete-app/app/src/test/java/com/cadeteria/cadete/util/ImagenesTest.kt`:

```kotlin
package com.cadeteria.cadete.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ImagenesTest {

    private val conVersion = "https://res.cloudinary.com/demo/image/upload/v1234567/foto.jpg"

    @Test
    fun `inserta la transformacion antes de la version`() {
        assertEquals(
            "https://res.cloudinary.com/demo/image/upload/f_auto,q_auto,c_limit,w_120/v1234567/foto.jpg",
            optimizarImagen(conVersion, 120),
        )
    }

    @Test
    fun `no duplica si ya trae transformacion`() {
        val ya = "https://res.cloudinary.com/demo/image/upload/f_auto,q_auto/v1234567/foto.jpg"
        assertEquals(ya, optimizarImagen(ya, 120))
    }

    @Test
    fun `deja igual una url que no es de cloudinary`() {
        val otra = "https://ejemplo.com/foto.jpg"
        assertEquals(otra, optimizarImagen(otra, 120))
    }

    @Test
    fun `devuelve null tal cual`() {
        assertNull(optimizarImagen(null, 120))
    }
}
```

- [ ] **Step 2: Correr el test y verificar que falla**

Run: `cd cadete-app && ./gradlew :app:testDebugUnitTest --tests "*ImagenesTest*"`
(En este entorno no hay `gradlew` versionado: usar la distribución cacheada de Gradle 8.7 con
`ANDROID_HOME` seteado — ver `cadete-app/README.md`, sección "Primer build".)
Expected: FAIL — no compila, `optimizarImagen` no existe.

- [ ] **Step 3: Implementar**

Crear `cadete-app/app/src/main/java/com/cadeteria/cadete/util/Imagenes.kt`:

```kotlin
package com.cadeteria.cadete.util

private const val MARCA = "/image/upload/"

/**
 * Inserta una transformación de Cloudinary en una URL para no bajar el original.
 *
 * Las fotos se suben desde el celular y se sirven crudas: un avatar de 64 px bajaba varios
 * MB. Cloudinary transforma por URL, así que esto arregla también lo ya subido.
 *
 * Si la URL no es de Cloudinary, o ya trae una transformación, se devuelve tal cual —
 * romper una URL que hoy funciona sería peor que el problema.
 */
fun optimizarImagen(url: String?, ancho: Int): String? {
    if (url.isNullOrBlank() || !url.contains("res.cloudinary.com")) return url

    val i = url.indexOf(MARCA)
    if (i == -1) return url

    val resto = url.substring(i + MARCA.length)
    val barra = resto.indexOf('/')
    if (barra == -1) return url

    val primero = resto.substring(0, barra)
    // "v1234567" es la versión del asset; cualquier otro segmento con "_" o "," ya es una
    // transformación puesta por otro lado.
    val esVersion = Regex("^v\\d+$").matches(primero)
    if (!esVersion && (primero.contains('_') || primero.contains(','))) return url

    return url.substring(0, i + MARCA.length) + "f_auto,q_auto,c_limit,w_$ancho/" + resto
}
```

- [ ] **Step 4: Correr el test y verificar que pasa**

Run: `cd cadete-app && ./gradlew :app:testDebugUnitTest --tests "*ImagenesTest*"`
Expected: PASS — 4 tests.

- [ ] **Step 5: Aplicar en las dos pantallas que muestran fotos con Coil**

- `ui/perfil/PerfilScreen.kt:295` — `AsyncImage(model = optimizarImagen(url, 240), ...)`
- `ui/chat/ChatScreen.kt:195` — imagen del chat: `optimizarImagen(url, 1200)`

Coil además cachea en disco, así que el ahorro se multiplica entre pantallas.

- [ ] **Step 6: Verificar que compila**

Run: `cd cadete-app && ./gradlew :app:assembleDebug`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
cd cadete-app
git add app/src/main/java/com/cadeteria/cadete/util app/src/test app/src/main/java/com/cadeteria/cadete/ui
git commit -m "La app pide las fotos de Cloudinary transformadas en vez del original"
```

---

### Task 4: Listado de cadetes — sacar el N+1 y los 6 campos que no se usan

**Files:**
- Modify: `cadeteria/src/main/java/com/cadeteria/backend/repository/PedidoRepository.java`
- Modify: `cadeteria/src/main/java/com/cadeteria/backend/dto/CadeteDtos.java`
- Modify: `cadeteria/src/main/java/com/cadeteria/backend/service/CadeteService.java:113-120`
- Modify: `cadeteria/src/main/java/com/cadeteria/backend/controller/CadeteController.java:57`
- Test: `cadeteria/src/test/java/com/cadeteria/backend/dto/CadeteResponseTest.java`

**Interfaces:**
- Consumes: nada.
- Produces:
  - `CadeteResponse.fromListado(Cadete c, Double calificacionPromedio, long calificacionCantidad): CadeteResponse`
  - `CadeteService.listarParaPanel(): List<CadeteResponse>`
  - `PedidoRepository.calificacionPorCadete(): List<Object[]>`

**Dos cosas en una tarea, a propósito**: las dos tocan la misma llamada
(`CadeteController.list()` → `CadeteService`) y ninguna se entiende sola. Un revisor no
debería aprobar una y rechazar la otra — el listado queda raro si solo se hace la mitad.

**Contexto (spec, sección 3 — leerla antes):** el plan original era crear un DTO "resumen" y
**estaba mal**: el listado usa DNI, teléfono, usuario, calificación y datos de pago de
verdad. Lo que no usa son 4 URLs de fotos + `cbu` + `aliasCbu`.

**Y el N+1**: `CadeteService.toResponse()` hace una query **por cadete** para la
calificación. 30 cadetes = 30 queries por carga. Se reemplaza por una sola query agrupada.

**Contexto (del spec, sección 3 — leerla antes):** el plan original era crear un DTO "resumen"
y **estaba mal**: el listado usa DNI, teléfono, usuario, calificación y datos de pago de
verdad. Lo que no usa son 4 URLs de fotos + `cbu` + `aliasCbu`. Se sacan solo esos.

- [ ] **Step 1: Confirmar que esos 6 campos no se usan en el listado**

**Cuidado con el alcance del grep** (corregido 2026-09-20 — la versión original de este paso
barría `features/cadetes/` entero y **nunca podía pasar**): `cadete-form.component.ts` vive
en esa carpeta y **sí usa los 6 campos**, porque es el formulario de alta/edición. Pero carga
por `cadetes.get(id)` → `GET /api/admin/cadetes/{id}` (`cadete.service.ts:25-26`), que pega al
endpoint **singular** y sigue mandando todo — no es el listado. Hay que excluirlo.

Run:
```bash
cd admin-front && grep -rn "fotoUrl\|fotoVehiculoUrl\|fotoCarnetUrl\|fotoTarjetaVerdeUrl\|cbu\|aliasCbu" \
  src/app/features/cadetes/cadetes.component.ts src/app/features/dashboard
```
Expected: **sin resultados**, salvo `dashboard.component.ts:1353` — que es un payload de
**pedido** (`pedidos.finalizar(..., { fotoUrl: null })`), otro DTO y una escritura, no una
lectura del listado de cadetes. Cualquier otro hit: **parar** y revisar.

(La ficha del cadete y la página de seguimiento sí los usan — pero esas pegan a
`GET /api/cadetes/{id}`, que sigue completo.)

- [ ] **Step 2: Escribir el test que falla**

Agregar a `cadeteria/src/test/java/com/cadeteria/backend/dto/CadeteResponseTest.java` (crear):

```java
package com.cadeteria.backend.dto;

import com.cadeteria.backend.model.Cadete;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * El listado del panel no muestra fotos ni datos de cobro, así que no tienen por qué viajar
 * en cada carga. `fromListado` los vacía; el detalle (`from`) los sigue mandando porque la
 * ficha sí los muestra.
 */
class CadeteResponseTest {

    /**
     * Setea los SEIS campos que `fromListado` vacía, no solo algunos: si el fixture deja uno
     * en null, la aserción correspondiente se cumple sola y el test no puede fallar. La
     * versión original de este plan seteaba 3 y aseveraba 4 — una de esas aserciones
     * (`aliasCbu`) era vacua, y dos campos no se aseveraban en ningún lado.
     */
    private Cadete cadeteConTodo() {
        Cadete c = new Cadete();
        c.setId("c1");
        c.setNombre("Juan");
        c.setApellido("Perez");
        c.setDni("30111222");
        c.setFotoUrl("https://res.cloudinary.com/demo/image/upload/v1/foto.jpg");
        c.setFotoVehiculoUrl("https://res.cloudinary.com/demo/image/upload/v1/vehiculo.jpg");
        c.setFotoCarnetUrl("https://res.cloudinary.com/demo/image/upload/v1/carnet.jpg");
        c.setFotoTarjetaVerdeUrl("https://res.cloudinary.com/demo/image/upload/v1/tarjeta.jpg");
        c.setCbu("0170099220000067797370");
        c.setAliasCbu("juan.moto.cadete");
        return c;
    }

    @Test
    void fromListadoVaciaFotosYDatosDeCobro() {
        CadeteResponse r = CadeteResponse.fromListado(cadeteConTodo(), null, 0);
        assertNull(r.fotoUrl());
        assertNull(r.fotoVehiculoUrl());
        assertNull(r.fotoCarnetUrl());
        assertNull(r.fotoTarjetaVerdeUrl());
        assertNull(r.cbu());
        assertNull(r.aliasCbu());
    }

    @Test
    void fromListadoConservaLoQueElListadoSiUsa() {
        CadeteResponse r = CadeteResponse.fromListado(cadeteConTodo(), null, 0);
        assertNotNull(r.nombre());
        assertNotNull(r.apellido());
        assertNotNull(r.dni());
        assertNotNull(r.id());
    }

    @Test
    void fromSigueMandandoTodo() {
        CadeteResponse r = CadeteResponse.from(cadeteConTodo());
        assertNotNull(r.fotoUrl());
        assertNotNull(r.cbu());
    }
}
```

- [ ] **Step 3: Correr el test y verificar que falla**

Run: `cd cadeteria && ./mvnw -o test -Dtest=CadeteResponseTest`
Expected: FAIL — `fromListado` no existe.

- [ ] **Step 4: Implementar `fromListado`**

En `CadeteDtos.java`, dentro del record `CadeteResponse`, al lado del `from` que ya existe.
Los **43 campos** van en el orden exacto de la declaración del record; **solo 6 van en `null`**:

```java
/**
 * Para el listado del panel: el listado no muestra fotos ni datos de cobro, así que no
 * tienen por qué viajar en cada carga. Mismo criterio que {@code PedidoResponse.fromResumen()},
 * que saca las paradas del listado de pedidos.
 *
 * Para la ficha ({@code GET /api/cadetes/{id}}) y el perfil propio
 * ({@code GET /api/cadetes/me}) se sigue usando {@link #from}, que manda todo — ahí las
 * fotos y el CBU sí se muestran.
 */
public static CadeteResponse fromListado(Cadete c, Double calificacionPromedio, long calificacionCantidad) {
    CadeteResponse r = from(c, calificacionPromedio, calificacionCantidad);
    return new CadeteResponse(
            r.id(), r.nombre(), r.apellido(), r.dni(), r.telefono(), r.email(),
            null,                                    // fotoUrl
            r.tipoVehiculo(), r.vehiculoColor(), r.vehiculoPatente(),
            r.vehiculoMarca(), r.vehiculoModelo(), r.vehiculoAnio(),
            null,                                    // fotoVehiculoUrl
            null,                                    // fotoCarnetUrl
            null,                                    // fotoTarjetaVerdeUrl
            r.username(), r.activo(), r.estado(),
            r.lat(), r.lng(), r.ubicacionActualizadaEn(), r.zonaActual(),
            r.montoMaximoTransportado(), r.maxViajesSimultaneos(),
            r.maxViajesDiarios(), r.maxViajesSemanales(), r.ordenColaEspera(),
            null,                                    // cbu
            null,                                    // aliasCbu
            r.turnoInicio(), r.turnoFin(),
            r.calificacionPromedio(), r.calificacionCantidad(),
            r.modalidadPago(), r.habilitadoPago(), r.pagoSemanalMontoPagado(), r.pagoSemanalVenceEn(),
            r.montoSemanalActual(),
            r.creditoDisponible(), r.notasInternas(),
            r.ultimaVersionApp(), r.ultimaVersionAppEn());
}
```

- [ ] **Step 5: Agregar la query agrupada al repositorio**

En `PedidoRepository`, junto a las otras queries:

```java
/**
 * Calificación agrupada por cadete — una sola query para todo el listado del panel. Antes
 * era una query POR cadete (`CadeteService.toResponse`), o sea N queries por carga.
 */
@Query("""
        select p.cadeteAsignado.id, avg(p.calificacionEstrellas), count(p)
        from Pedido p
        where p.calificacionEstrellas is not null and p.cadeteAsignado is not null
        group by p.cadeteAsignado.id
        """)
List<Object[]> calificacionPorCadete();
```

- [ ] **Step 6: Agregar `listarParaPanel` al service**

En `CadeteService`, al lado de `toResponse` (que se deja intacto — lo siguen usando la ficha y
las mutaciones, y ahí es un solo cadete):

```java
/**
 * Listado del panel: una sola query para las calificaciones (antes era una por cadete) y el
 * DTO sin las fotos ni los datos de cobro, que el listado no muestra.
 */
@Transactional(readOnly = true)
public List<CadeteResponse> listarParaPanel() {
    Map<String, double[]> calificaciones = new HashMap<>();
    for (Object[] fila : pedidoRepo.calificacionPorCadete()) {
        calificaciones.put((String) fila[0],
                new double[]{ ((Number) fila[1]).doubleValue(), ((Number) fila[2]).longValue() });
    }
    return repo.findAll().stream()
            .map(c -> {
                double[] cal = calificaciones.get(c.getId());
                return CadeteResponse.fromListado(c,
                        cal == null ? null : cal[0],
                        cal == null ? 0L : (long) cal[1]);
            })
            .toList();
}
```

- [ ] **Step 7: Correr los tests**

Run: `cd cadeteria && ./mvnw -o test -Dtest=CadeteResponseTest`
Expected: PASS — 3 tests.

Run: `cd cadeteria && ./mvnw -o test`
Expected: PASS — los 14 que ya existían + los 3 nuevos. **Si algo más se rompe, es que otro
endpoint dependía de `toResponse` para el listado** — revisar antes de seguir.

- [ ] **Step 8: Usar `listarParaPanel` en el controller**

`CadeteController.java:57` pasa de:
```java
return service.findAll().stream().map(service::toResponse).toList();
```
a:
```java
return service.listarParaPanel();
```

- [ ] **Step 9: Verificar el N+1 con SQL (no confiar en la lectura del código)**

Prender momentáneamente `logging.level.org.hibernate.SQL=debug` en `application.yml`,
levantar el backend y pegarle a `GET /api/admin/cadetes`.
Expected: **1 query** a `pedido` para las calificaciones, no una por cadete. Después apagar el
logging.

- [ ] **Step 10: Verificar que el listado se ve igual**

Con backend + panel levantados, abrir la pantalla **Cadetes** y confirmar que la tabla se ve
igual que antes: Nombre, DNI, Teléfono, Usuario, App, Vehículo, Calificación, Estado, Pago.
**Si alguna celda quedó vacía, era un campo que el listado sí usaba** — volver al Step 1 y
agregarlo a la lista de "conservar".

- [ ] **Step 11: Commit**

```bash
cd cadeteria
git add src/main/java/com/cadeteria/backend/dto/CadeteDtos.java \
        src/main/java/com/cadeteria/backend/repository/PedidoRepository.java \
        src/main/java/com/cadeteria/backend/service/CadeteService.java \
        src/main/java/com/cadeteria/backend/controller/CadeteController.java \
        src/test/java/com/cadeteria/backend/dto/CadeteResponseTest.java
git commit -m "Listado de cadetes: una query de calificaciones en vez de N, y sin fotos ni CBU"
```

---

## Lo que este plan NO hace

- **Auditoría del resto de los endpoints** (sección 3 del spec, fase 4). Requiere medir
  primero con `logging.level.org.hibernate.SQL=debug`; hacerlo sin números es adivinar.
  **Plan aparte cuando haya medición.**
- **Comprimir en la subida.** El tráfico de subida va a Cloudinary, no al backend — es otro
  problema (tiempo de subida del cadete) y va después.
- **Pasar las fotos a otro proveedor.** El diseño actual ("Cloudinary directo desde el
  cliente, el backend nunca ve el archivo") está bien y no se toca.

## Cómo se mide que sirvió

Sin números esto es opinión. Antes y después, misma pantalla:

| Qué | Antes (esperado) | Después (esperado) |
| --- | --- | --- |
| Solicitudes de cadete, 4 fotos en 56 px | ~12 MB | ~40 KB |
| `GET /api/admin/cadetes` con 30 cadetes — queries | 31 (1 + una por cadete) | **2** |
| `GET /api/admin/cadetes` con 30 cadetes — campos | 43 × 30 | 37 × 30 |

Herramienta: pestaña Network del navegador para el tráfico, `logging.level.org.hibernate.SQL=debug`
para las queries (Steps 9). Ya está Actuator en el backend (2026-09-20) por si hace falta mirar
del lado del servidor.
