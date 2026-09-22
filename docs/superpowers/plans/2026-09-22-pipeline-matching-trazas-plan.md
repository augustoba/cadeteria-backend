# Pipeline de Matching de Trazas GPS Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Servicio Node.js standalone que, cada tanto, toma los pedidos `ENTREGADO` sin procesar, matchea su traza GPS (`pedido_ubicacion`) contra el motor OSRM propio (`routing/`, ver plan `2026-09-22-motor-ruteo-propio-plan.md`) y guarda distancia/duración real por calle en una tabla nueva — **en paralelo** al cálculo naive existente en `MetricasService`, sin que nadie más lo consuma todavía.

**Architecture:** Repo Git propio `traza-matching/` en `C:\proyectos\cadeteria\` (hermano de `cadeteria/`, `routing/`, `whatsapp-gateway/` — corrección 2026-09-22: **no** es un job embebido en el backend Spring). Se conecta **directo a la misma base MySQL** (`cadeteria`, mismas credenciales que ya usa el backend) para leer `pedido`/`pedido_ubicacion`/`cadete` y escribir en `pedido_ruta_matcheada`, tabla que este servicio crea y es dueño de ella. Mismo stack y estilo de código que `whatsapp-gateway/` (Node.js con ES modules, sin dependencias de más).

**Tech Stack:** Node.js 22 (ya instalado, confirmado), `mysql2` (única dependencia de producción), `node:test` + `node:assert` (built-in, sin dependencia extra) para los tests de la lógica de negocio, `fetch` global de Node para llamar al motor OSRM.

**Spec:** [`documentacion/spec-routing-propio.md`](../../../documentacion/spec-routing-propio.md) §4 (Subproyecto B) — vive en el repo del backend (`cadeteria/documentacion/`), es el único artefacto de este trabajo que sigue ahí.

## Global Constraints

- **Cero cambios en el repo del backend (`cadeteria/`).** Ni un archivo, ni un endpoint nuevo — este servicio lee/escribe la base directo, no vía HTTP contra el backend.
- Todo el código de este plan vive en `C:\proyectos\cadeteria\traza-matching\`, repo Git propio.
- Conexión a MySQL: mismos defaults que `cadeteria/src/main/resources/application.yml` — host `localhost`, puerto `3306`, base `cadeteria`, usuario/password desde `DB_USER`/`DB_PASSWORD` con default `root`/`root` (igual patrón `process.env.X || default` que ya usa `whatsapp-gateway/src/gateway.js`, sin `dotenv`).
- Perfil por pedido: `tipo_vehiculo_id === 'BICI'` → `bicycle` (puerto 5001), cualquier otro (`MOTO`) → `car` (puerto 5000) — misma regla que `RutaService` del backend.
- Nombres de tabla/columna reales (confirmados leyendo las entidades JPA del backend): `pedido` (`id`, `estado_id`, `cadete_asignado_id`, `finalizado_en`), `pedido_ubicacion` (`pedido_id`, `lat`, `lng`, `capturado_en`), `cadete` (`id`, `tipo_vehiculo_id`).
- El wrapper HTTP (`osrmMatchClient.js`) no lleva test automatizado — mismo criterio que el plan de Subproyecto A: se verifica llamándolo de verdad contra el motor local, no mockeando `fetch`.
- La lógica de negocio (`matching.js`) sí lleva TDD completo con `node:test`, inyectando un `pool` y un `osrmMatchClient` falsos (fakes de objeto plano, no una librería de mocking — no hace falta una dependencia nueva para esto).
- Estilo de código: ES modules, sin punto y coma, comillas simples — igual que `whatsapp-gateway/src/gateway.js`.

---

### Task 1: Estructura base del repo `traza-matching/`

**Files (todos relativos a `C:/proyectos/cadeteria/traza-matching`, la raíz de este repo nuevo):**
- Create: `package.json`
- Create: `.gitignore`
- Create: `README.md` (esqueleto, se completa en Task 5)

**Interfaces:**
- Produces: repo Git nuevo en `C:\proyectos\cadeteria\traza-matching\`, con `mysql2` instalado, listo para que las tasks siguientes agreguen código en `src/`.

- [ ] **Step 1: Crear la carpeta e inicializar el repo**

Run:
```bash
mkdir -p "C:/proyectos/cadeteria/traza-matching/src"
mkdir -p "C:/proyectos/cadeteria/traza-matching/test"
cd "C:/proyectos/cadeteria/traza-matching"
git init
```
Expected: `Initialized empty Git repository in .../traza-matching/.git/`. Confirmar con `git rev-parse --show-toplevel` que devuelve `.../traza-matching`, no `.../cadeteria`.

- [ ] **Step 2: Crear el `package.json`**

`package.json`:
```json
{
  "name": "traza-matching",
  "version": "0.1.0",
  "private": true,
  "type": "module",
  "scripts": {
    "start": "node src/index.js",
    "test": "node --test test/"
  },
  "dependencies": {
    "mysql2": "^3.11.0"
  }
}
```

- [ ] **Step 3: Instalar dependencias**

Run:
```bash
cd "C:/proyectos/cadeteria/traza-matching"
npm install
```
Expected: crea `node_modules/` y `package-lock.json` sin errores.

- [ ] **Step 4: Crear el `.gitignore`**

`.gitignore`:
```
node_modules/
```

- [ ] **Step 5: Crear el README con el esqueleto**

`README.md`:
```markdown
# traza-matching (prototipo)

Matchea la traza GPS de los cadetes (tabla `pedido_ubicacion` del backend) contra el motor
OSRM propio (`routing/`) para tener distancia/duración real por calle, en paralelo al cálculo
naive que ya hace `MetricasService` en el backend — sin tocar el backend para nada. Ver
`documentacion/spec-routing-propio.md` (repo `cadeteria/`) §4 para el diseño completo.

## Cómo correrlo

(completar en Task 5, luego de tener toda la lógica armada)
```

- [ ] **Step 6: Verificar**

Run:
```bash
cd "C:/proyectos/cadeteria/traza-matching"
ls
```
Expected: aparecen `package.json`, `package-lock.json`, `.gitignore`, `README.md`, `src/`, `test/`, `node_modules/`.

- [ ] **Step 7: Commit**

```bash
cd "C:/proyectos/cadeteria/traza-matching"
git add package.json package-lock.json .gitignore README.md
git commit -m "Crea estructura base de traza-matching/"
```

---

### Task 2: Conexión a MySQL y tabla `pedido_ruta_matcheada`

**Files (relativos a `C:/proyectos/cadeteria/traza-matching`):**
- Create: `src/db.js`
- Create: `src/schema.js`

**Interfaces:**
- Produces: `crearPool()` (función, devuelve un pool de `mysql2/promise` con interfaz `pool.query(sql, params)`), `asegurarTabla(pool)` (función async, crea `pedido_ruta_matcheada` si no existe). Los consume `src/index.js` (Task 5).

- [ ] **Step 1: Escribir `src/db.js`**

`src/db.js`:
```js
import mysql from 'mysql2/promise'

export function crearPool() {
  return mysql.createPool({
    host: process.env.DB_HOST || 'localhost',
    port: Number(process.env.DB_PORT || 3306),
    database: process.env.DB_NAME || 'cadeteria',
    user: process.env.DB_USER || 'root',
    password: process.env.DB_PASSWORD || 'root',
    waitForConnections: true,
    connectionLimit: 5,
  })
}
```

- [ ] **Step 2: Escribir `src/schema.js`**

`src/schema.js`:
```js
/**
 * pedido_id sin FK explícita a `pedido.id` a propósito: este servicio no toca el backend,
 * y una FK cross-schema-de-hecho entre dos codebases independientes es más frágil que útil acá.
 */
export async function asegurarTabla(pool) {
  await pool.query(`
    CREATE TABLE IF NOT EXISTS pedido_ruta_matcheada (
      pedido_id VARCHAR(255) NOT NULL PRIMARY KEY,
      distancia_m DOUBLE NOT NULL,
      duracion_s DOUBLE NOT NULL,
      geometria_geojson LONGTEXT,
      confianza DOUBLE,
      procesado_en DATETIME NOT NULL
    )
  `)
}
```

- [ ] **Step 3: Verificar que compila (Node no tiene "compilar", pero sí importar sin errores de sintaxis)**

Run:
```bash
cd "C:/proyectos/cadeteria/traza-matching"
node --check src/db.js
node --check src/schema.js
```
Expected: sin salida, exit code 0 (sintaxis válida). La verificación real contra una base viva pasa en el Task 5 (verificación end-to-end), no hace falta backend ni Docker corriendo para este paso.

- [ ] **Step 4: Commit**

```bash
cd "C:/proyectos/cadeteria/traza-matching"
git add src/db.js src/schema.js
git commit -m "Agrega conexion a MySQL y creacion de la tabla pedido_ruta_matcheada"
```

---

### Task 3: `osrmMatchClient.js` — wrapper del endpoint `/match`

**Files (relativos a `C:/proyectos/cadeteria/traza-matching`):**
- Create: `src/osrmMatchClient.js`

**Interfaces:**
- Produces: `osrmMatchClient.match(puntos, perfil)` (async, `puntos` = array de `{lat, lng, capturado_en}` como los devuelve `mysql2` de la tabla `pedido_ubicacion`; `perfil` = `'car'` o `'bicycle'`; devuelve `{distanciaM, duracionS, geometriaGeoJson, confianza}` o `null`). Lo consume `src/matching.js` (Task 4).

- [ ] **Step 1: Escribir la clase**

`src/osrmMatchClient.js`:
```js
const BASE_URLS = {
  car: 'http://localhost:5000',
  bicycle: 'http://localhost:5001',
}

/**
 * Wrapper del endpoint /match del motor OSRM propio (routing/, ver
 * documentacion/spec-routing-propio.md §3) -- "pega" una secuencia de puntos GPS a las calles
 * reales. Un puerto por perfil porque OSRM sirve un solo perfil por proceso
 * (routing/docker-compose.routing.yml). Sin test automatizado a proposito: mockear fetch no
 * aporta valor frente al mismo criterio ya aplicado en el plan de Subproyecto A -- se verifica
 * contra el motor real.
 */
export const osrmMatchClient = {
  async match(puntos, perfil) {
    const baseUrl = BASE_URLS[perfil]
    if (!baseUrl) return null

    try {
      const coords = puntos.map((p) => `${p.lng},${p.lat}`).join(';')
      const timestamps = puntos
        .map((p) => Math.floor(new Date(p.capturado_en).getTime() / 1000))
        .join(';')
      const url = `${baseUrl}/match/v1/driving/${coords}?timestamps=${timestamps}&overview=full&geometries=geojson`

      const resp = await fetch(url)
      if (!resp.ok) return null
      const data = await resp.json()

      const matchings = data.matchings
      if (!matchings || matchings.length === 0) return null

      const m = matchings[0]
      return {
        distanciaM: m.distance,
        duracionS: m.duration,
        geometriaGeoJson: JSON.stringify(m.geometry),
        confianza: m.confidence,
      }
    } catch (e) {
      console.error(`OSRM match no disponible: ${e.message}`)
      return null
    }
  },
}
```

- [ ] **Step 2: Verificar sintaxis**

Run:
```bash
cd "C:/proyectos/cadeteria/traza-matching"
node --check src/osrmMatchClient.js
```
Expected: sin salida, exit code 0.

- [ ] **Step 3: Verificación manual (requiere el motor propio del plan de Subproyecto A corriendo)**

Con `routing/docker-compose.routing.yml` arriba, probar el endpoint a mano antes de confiar en el wrapper:
```bash
curl "http://localhost:5000/match/v1/driving/-65.2226,-26.8083;-65.2200,-26.8150;-65.2176,-26.8241?timestamps=1700000000;1700000060;1700000120&overview=full&geometries=geojson"
```
Expected: JSON con `"code":"Ok"` y un array `matchings` no vacío, cada uno con `distance`, `duration`, `confidence` y `geometry`. Si el formato coincide con lo que lee `osrmMatchClient.match`, queda confirmado.

- [ ] **Step 4: Commit**

```bash
cd "C:/proyectos/cadeteria/traza-matching"
git add src/osrmMatchClient.js
git commit -m "Agrega osrmMatchClient, wrapper del endpoint /match del motor OSRM propio"
```

---

### Task 4: `matching.js` — la lógica principal, con TDD completo

**Files (relativos a `C:/proyectos/cadeteria/traza-matching`):**
- Create: `src/matching.js`
- Test: `test/matching.test.js`

**Interfaces:**
- Consumes: `pool.query(sql, params)` → `Promise<[rows, fields]>` (interfaz de `mysql2/promise`, inyectado, Task 2 lo produce para producción pero los tests usan un fake); `osrmMatchClient.match(puntos, perfil)` → `Promise<MatchResultado|null>` (Task 3, inyectado, los tests usan un fake).
- Produces: `elegirPerfil(tipoVehiculoId)` (función pura, `'BICI'` → `'bicycle'`, cualquier otra cosa → `'car'`), `procesarPendientes({ pool, osrmMatchClient, ventanaMs, log })` (async, devuelve la cantidad de pedidos matcheados con éxito). Los consume `src/index.js` (Task 5).

- [ ] **Step 1: Escribir el test completo primero**

`test/matching.test.js`:
```js
import { test } from 'node:test'
import assert from 'node:assert/strict'
import { elegirPerfil, procesarPendientes } from '../src/matching.js'

test('elegirPerfil devuelve bicycle para BICI', () => {
  assert.equal(elegirPerfil('BICI'), 'bicycle')
})

test('elegirPerfil devuelve car para MOTO o valores desconocidos', () => {
  assert.equal(elegirPerfil('MOTO'), 'car')
  assert.equal(elegirPerfil(null), 'car')
  assert.equal(elegirPerfil(undefined), 'car')
})

test('no procesa nada si no hay candidatos', async () => {
  const pool = { query: async () => [[]] }
  const osrmMatchClient = { match: async () => { throw new Error('no deberia llamarse') } }

  const procesados = await procesarPendientes({ pool, osrmMatchClient, ventanaMs: 1000 })

  assert.equal(procesados, 0)
})

test('salta pedidos con menos de 2 puntos de traza', async () => {
  const pool = {
    query: async (sql) => {
      if (sql.includes('FROM pedido p')) {
        return [[{ pedido_id: 'ped-1', tipo_vehiculo_id: 'MOTO' }]]
      }
      if (sql.includes('FROM pedido_ubicacion')) {
        return [[{ lat: -26.81, lng: -65.20, capturado_en: new Date() }]]
      }
      return [[]]
    },
  }
  const osrmMatchClient = { match: async () => { throw new Error('no deberia llamarse') } }

  const procesados = await procesarPendientes({ pool, osrmMatchClient, ventanaMs: 1000 })

  assert.equal(procesados, 0)
})

test('matchea con perfil car para MOTO y guarda el resultado', async () => {
  const inserts = []
  const pool = {
    query: async (sql, params) => {
      if (sql.includes('FROM pedido p')) {
        return [[{ pedido_id: 'ped-1', tipo_vehiculo_id: 'MOTO' }]]
      }
      if (sql.includes('FROM pedido_ubicacion')) {
        return [[
          { lat: -26.81, lng: -65.20, capturado_en: new Date() },
          { lat: -26.82, lng: -65.21, capturado_en: new Date() },
        ]]
      }
      if (sql.includes('INSERT INTO pedido_ruta_matcheada')) {
        inserts.push(params)
        return [{}]
      }
      return [[]]
    },
  }
  let perfilUsado
  const osrmMatchClient = {
    match: async (_puntos, perfil) => {
      perfilUsado = perfil
      return { distanciaM: 1500, duracionS: 300, geometriaGeoJson: '{}', confianza: 0.9 }
    },
  }

  const procesados = await procesarPendientes({ pool, osrmMatchClient, ventanaMs: 1000 })

  assert.equal(procesados, 1)
  assert.equal(perfilUsado, 'car')
  assert.equal(inserts.length, 1)
  assert.equal(inserts[0][0], 'ped-1')
})

test('matchea con perfil bicycle para BICI', async () => {
  const pool = {
    query: async (sql) => {
      if (sql.includes('FROM pedido p')) {
        return [[{ pedido_id: 'ped-1', tipo_vehiculo_id: 'BICI' }]]
      }
      if (sql.includes('FROM pedido_ubicacion')) {
        return [[
          { lat: -26.81, lng: -65.20, capturado_en: new Date() },
          { lat: -26.82, lng: -65.21, capturado_en: new Date() },
        ]]
      }
      if (sql.includes('INSERT INTO pedido_ruta_matcheada')) return [{}]
      return [[]]
    },
  }
  let perfilUsado
  const osrmMatchClient = {
    match: async (_puntos, perfil) => {
      perfilUsado = perfil
      return { distanciaM: 1500, duracionS: 500, geometriaGeoJson: '{}', confianza: 0.85 }
    },
  }

  await procesarPendientes({ pool, osrmMatchClient, ventanaMs: 1000 })

  assert.equal(perfilUsado, 'bicycle')
})

test('no guarda nada si OSRM no devuelve resultado', async () => {
  const pool = {
    query: async (sql) => {
      if (sql.includes('FROM pedido p')) {
        return [[{ pedido_id: 'ped-1', tipo_vehiculo_id: 'MOTO' }]]
      }
      if (sql.includes('FROM pedido_ubicacion')) {
        return [[
          { lat: -26.81, lng: -65.20, capturado_en: new Date() },
          { lat: -26.82, lng: -65.21, capturado_en: new Date() },
        ]]
      }
      if (sql.includes('INSERT INTO pedido_ruta_matcheada')) {
        throw new Error('no deberia insertar nada')
      }
      return [[]]
    },
  }
  const osrmMatchClient = { match: async () => null }

  const procesados = await procesarPendientes({ pool, osrmMatchClient, ventanaMs: 1000 })

  assert.equal(procesados, 0)
})

test('un fallo en un pedido no interrumpe al resto', async () => {
  const inserts = []
  const pool = {
    query: async (sql, params) => {
      if (sql.includes('FROM pedido p')) {
        return [[
          { pedido_id: 'ped-1', tipo_vehiculo_id: 'MOTO' },
          { pedido_id: 'ped-2', tipo_vehiculo_id: 'MOTO' },
        ]]
      }
      if (sql.includes('FROM pedido_ubicacion')) {
        if (params[0] === 'ped-1') throw new Error('boom')
        return [[
          { lat: -26.81, lng: -65.20, capturado_en: new Date() },
          { lat: -26.82, lng: -65.21, capturado_en: new Date() },
        ]]
      }
      if (sql.includes('INSERT INTO pedido_ruta_matcheada')) {
        inserts.push(params)
        return [{}]
      }
      return [[]]
    },
  }
  const osrmMatchClient = {
    match: async () => ({ distanciaM: 1500, duracionS: 300, geometriaGeoJson: '{}', confianza: 0.9 }),
  }

  const procesados = await procesarPendientes({ pool, osrmMatchClient, ventanaMs: 1000 })

  assert.equal(procesados, 1)
  assert.equal(inserts.length, 1)
  assert.equal(inserts[0][0], 'ped-2')
})
```

- [ ] **Step 2: Correr los tests y verificar que fallan por falta del módulo**

Run:
```bash
cd "C:/proyectos/cadeteria/traza-matching"
npm test
```
Expected: FAIL — `src/matching.js` no existe todavía (error al importar).

- [ ] **Step 3: Implementar `src/matching.js`**

`src/matching.js`:
```js
const MIN_PUNTOS_PARA_MATCHEAR = 2

export function elegirPerfil(tipoVehiculoId) {
  return tipoVehiculoId === 'BICI' ? 'bicycle' : 'car'
}

/**
 * Procesa los pedidos ENTREGADO recientes que todavia no tienen fila en
 * pedido_ruta_matcheada -- en PARALELO al calculo naive de MetricasService (backend), no lo
 * reemplaza. Devuelve cuantos matcheo con exito, para logging/verificacion manual.
 */
export async function procesarPendientes({ pool, osrmMatchClient, ventanaMs, log = console }) {
  const limite = new Date(Date.now() - ventanaMs)
  const [candidatos] = await pool.query(
    `SELECT p.id AS pedido_id, c.tipo_vehiculo_id AS tipo_vehiculo_id
     FROM pedido p
     LEFT JOIN cadete c ON c.id = p.cadete_asignado_id
     LEFT JOIN pedido_ruta_matcheada m ON m.pedido_id = p.id
     WHERE p.estado_id = 'ENTREGADO'
       AND p.finalizado_en >= ?
       AND m.pedido_id IS NULL`,
    [limite]
  )

  let procesados = 0
  for (const candidato of candidatos) {
    try {
      const matcheado = await procesarUno({ pool, osrmMatchClient, candidato })
      if (matcheado) procesados++
    } catch (e) {
      log.error(`No se pudo matchear el pedido ${candidato.pedido_id}: ${e.message}`)
    }
  }
  return procesados
}

async function procesarUno({ pool, osrmMatchClient, candidato }) {
  const [puntos] = await pool.query(
    `SELECT lat, lng, capturado_en FROM pedido_ubicacion WHERE pedido_id = ? ORDER BY capturado_en ASC`,
    [candidato.pedido_id]
  )
  if (puntos.length < MIN_PUNTOS_PARA_MATCHEAR) return false

  const perfil = elegirPerfil(candidato.tipo_vehiculo_id)
  const resultado = await osrmMatchClient.match(puntos, perfil)
  if (!resultado) return false

  await pool.query(
    `INSERT INTO pedido_ruta_matcheada
       (pedido_id, distancia_m, duracion_s, geometria_geojson, confianza, procesado_en)
     VALUES (?, ?, ?, ?, ?, NOW())`,
    [candidato.pedido_id, resultado.distanciaM, resultado.duracionS, resultado.geometriaGeoJson, resultado.confianza]
  )
  return true
}
```

- [ ] **Step 4: Correr los tests y verificar que pasan**

Run:
```bash
cd "C:/proyectos/cadeteria/traza-matching"
npm test
```
Expected: PASS, las 8 pruebas en verde (`# pass 8`, `# fail 0`).

- [ ] **Step 5: Commit**

```bash
cd "C:/proyectos/cadeteria/traza-matching"
git add src/matching.js test/matching.test.js
git commit -m "Agrega matching.js: procesa pedidos entregados contra el OSRM propio, con TDD"
```

---

### Task 5: `index.js` — arranque, polling, y verificación end-to-end

**Files (relativos a `C:/proyectos/cadeteria/traza-matching`):**
- Create: `src/index.js`
- Modify: `README.md`

**Interfaces:**
- Consumes: `crearPool()` (Task 2), `asegurarTabla(pool)` (Task 2), `osrmMatchClient` (Task 3), `procesarPendientes({...})` (Task 4).

- [ ] **Step 1: Escribir `src/index.js`**

`src/index.js`:
```js
import { crearPool } from './db.js'
import { asegurarTabla } from './schema.js'
import { osrmMatchClient } from './osrmMatchClient.js'
import { procesarPendientes } from './matching.js'

const INTERVALO_SEG = Number(process.env.TRAZA_MATCHING_INTERVALO_SEG || 600)
const VENTANA_DIAS = Number(process.env.TRAZA_MATCHING_VENTANA_DIAS || 3)

async function main() {
  const pool = crearPool()
  await asegurarTabla(pool)
  console.log(`traza-matching arrancado -- intervalo ${INTERVALO_SEG}s, ventana ${VENTANA_DIAS} dias`)

  const ventanaMs = VENTANA_DIAS * 24 * 60 * 60 * 1000

  const ciclo = async () => {
    try {
      const procesados = await procesarPendientes({ pool, osrmMatchClient, ventanaMs })
      if (procesados > 0) console.log(`Matcheados ${procesados} pedido(s) en este ciclo.`)
    } catch (e) {
      console.error(`Ciclo de matching fallo: ${e.message}`)
    }
  }

  await ciclo()
  setInterval(ciclo, INTERVALO_SEG * 1000)
}

main().catch((e) => {
  console.error(`traza-matching no pudo arrancar: ${e.message}`)
  process.exit(1)
})
```

- [ ] **Step 2: Verificar sintaxis**

Run:
```bash
cd "C:/proyectos/cadeteria/traza-matching"
node --check src/index.js
```
Expected: sin salida, exit code 0.

- [ ] **Step 3: Verificación end-to-end manual (requiere el motor del plan de Subproyecto A y el backend corriendo)**

Con `routing/docker-compose.routing.yml` arriba y el backend levantado normalmente (con MySQL corriendo en `localhost:3306`):
1. Generar un pedido de prueba con traza: crear un pedido, asignarlo a un cadete de prueba, moverlo (a mano o con una cuenta de prueba logueada en la app de cadetes) para que se generen varios puntos en `pedido_ubicacion`, y marcarlo `ENTREGADO`.
2. Correr este servicio con un intervalo bajo para no esperar los 10 minutos default:
```bash
cd "C:/proyectos/cadeteria/traza-matching"
TRAZA_MATCHING_INTERVALO_SEG=30 npm start
```
Expected: log `traza-matching arrancado -- intervalo 30s, ventana 3 dias`, y en el primer ciclo (corre inmediatamente al arrancar) un log `Matcheados 1 pedido(s) en este ciclo.` si el pedido de prueba calificaba.

- [ ] **Step 4: Verificar el resultado en la base**

```sql
SELECT * FROM pedido_ruta_matcheada WHERE pedido_id = '<id del pedido de prueba>';
```
Expected: una fila con `distancia_m`, `duracion_s`, `geometria_geojson` y `confianza` completos.

- [ ] **Step 5: Comparar contra el cálculo naive existente del backend**

Comparar `distancia_m` de esa fila contra el "km real" que calcula hoy `MetricasService` (backend, líneas ~190-198, suma de haversine entre puntos) para el mismo pedido — el valor matcheado debería ser igual o mayor (la línea recta entre puntos GPS nunca sobreestima respecto a seguir la calle real).

- [ ] **Step 6: Completar el README con instrucciones reales**

`README.md` (reemplaza el esqueleto del Task 1):
```markdown
# traza-matching (prototipo)

Matchea la traza GPS de los cadetes (tabla `pedido_ubicacion` del backend) contra el motor
OSRM propio (`routing/`) para tener distancia/duración real por calle, en paralelo al cálculo
naive que ya hace `MetricasService` en el backend — sin tocar el backend para nada. Ver
`documentacion/spec-routing-propio.md` (repo `cadeteria/`) §4 para el diseño completo.

## Requisitos

- El motor OSRM propio (`routing/`) tiene que estar corriendo (`docker compose -f
  docker-compose.routing.yml up -d` desde `routing/`).
- MySQL de la cadetería accesible (mismo que usa el backend).

## Variables de entorno (todas opcionales, con default)

| Variable | Default | Qué es |
|---|---|---|
| `DB_HOST` | `localhost` | Host de MySQL |
| `DB_PORT` | `3306` | Puerto de MySQL |
| `DB_NAME` | `cadeteria` | Base de datos |
| `DB_USER` | `root` | Usuario |
| `DB_PASSWORD` | `root` | Password |
| `TRAZA_MATCHING_INTERVALO_SEG` | `600` | Cada cuánto corre el ciclo de matching |
| `TRAZA_MATCHING_VENTANA_DIAS` | `3` | Cuántos días hacia atrás busca pedidos ENTREGADO sin procesar |

## Cómo correrlo

```bash
npm install
npm start
```

## Tests

```bash
npm test
```

## Estado

No está conectado al backend (ni por código ni por API) — solo comparte la base de datos.
Es la implementación del Subproyecto B de `documentacion/spec-routing-propio.md`.
```

- [ ] **Step 7: Commit**

```bash
cd "C:/proyectos/cadeteria/traza-matching"
git add src/index.js README.md
git commit -m "Agrega arranque con polling y completa el README de traza-matching"
```

---

## Hito final

`pedido_ruta_matcheada` se puebla sola con cada pedido entregado que tenga traza GPS, corriendo
como proceso Node.js standalone, sin que el repo del backend tenga ni un solo cambio. Datos reales
disponibles para decidir más adelante, con volumen en mano, si vale la pena linkear esto al backend
(Fase C de `documentacion/spec-routing-propio.md`).
