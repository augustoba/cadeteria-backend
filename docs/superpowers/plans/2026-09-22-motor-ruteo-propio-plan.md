# Motor de Ruteo Propio (OSRM auto-hospedado) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Levantar un motor de ruteo OSRM propio (perfiles `car` y `bicycle`) sobre un extracto de la provincia de Tucumán, corriendo en Docker Desktop, totalmente standalone y sin tocar el backend Java.

**Architecture:** Repo Git propio en `C:\proyectos\cadeteria\routing\` (hermano de `cadeteria/` y `whatsapp-gateway/`, **no** dentro del repo del backend — corrección 2026-09-22) con scripts bash que descargan el extracto de Argentina (Geofabrik), lo recortan a Tucumán con `osmium` (vía contenedor descartable, sin instalar nada en el host), y preparan dos instancias OSRM separadas (una por perfil, porque OSRM sirve un solo perfil por proceso) levantadas con `docker-compose.routing.yml` en los puertos 5000 (car) y 5001 (bicycle).

**Working directory:** todos los comandos de este plan (salvo el Task 1, que es a nivel del sistema) asumen `cd "C:/proyectos/cadeteria/routing"` como directorio de trabajo — es la raíz de este repo nuevo, hermano del repo del backend, no una subcarpeta suya.

**Tech Stack:** Docker Desktop (WSL2), imagen oficial `osrm/osrm-backend`, `osmium-tool` (vía contenedor `debian:bookworm-slim` + apt), bash scripts.

**Spec:** [`documentacion/spec-routing-propio.md`](../../../documentacion/spec-routing-propio.md) §3 (Subproyecto A) — vive en el repo del backend (`cadeteria/documentacion/`), es el único artefacto de este trabajo que sigue ahí.

## Global Constraints

- Cero cambios en código Java / `RutaService.java` ni en ningún archivo del repo del backend (`cadeteria/`) — este plan es 100% infraestructura aislada en su propio repo.
- Motor: OSRM (no Valhalla) — mismo formato de respuesta que ya consume `RutaService` hoy.
- Cobertura: provincia de Tucumán completa (no solo Gran San Miguel) — decisión del dueño porque se recorren localidades.
- Perfiles: `car` y `bicycle`, replicando MOTO→driving / BICI→cycling de `RutaService`.
- Todo lo generado en `data/` es pesado (GB) y va gitignoreado — nunca se commitea.

---

### Task 1: Instalar y verificar Docker Desktop

**Files:** ninguno (acción de entorno, no de código).

**Interfaces:**
- Produces: `docker` y `docker compose` disponibles en PATH, Docker Desktop corriendo, para que Tasks 2-8 puedan usarlos.

- [ ] **Step 1: Intentar instalar vía winget**

Run (PowerShell, requiere permisos de administrador):
```powershell
winget install -e --id Docker.DockerDesktop
```
Expected: descarga e instala Docker Desktop. **Puede pedir reiniciar Windows** (para habilitar WSL2) — si lo pide, reiniciar antes de seguir.

- [ ] **Step 2: Si winget falla o no está disponible, instalar manualmente**

Si el Step 1 no funcionó: descargar el instalador desde `https://www.docker.com/products/docker-desktop/` y correrlo a mano. Aceptar la licencia la primera vez que abre. Confirmar que "Use WSL 2 instead of Hyper-V" quede tildado durante la instalación (opción por defecto).

- [ ] **Step 3: Abrir Docker Desktop y esperar a que arranque**

Docker Desktop debe quedar con el ícono en verde/"Running" en la bandeja del sistema antes de seguir — puede tardar 1-2 minutos la primera vez.

- [ ] **Step 4: Verificar la instalación**

Run:
```powershell
docker --version
docker compose version
docker run --rm hello-world
```
Expected: las tres corren sin error; el último imprime el mensaje "Hello from Docker!".

---

### Task 2: Repo propio `routing/` en la raíz del workspace

**Files:**
- Create: `C:\proyectos\cadeteria\routing\.gitignore`
- Create: `C:\proyectos\cadeteria\routing\README.md` (esqueleto, se completa en Task 8)

**Interfaces:**
- Produces: repo Git nuevo en `C:\proyectos\cadeteria\routing\`, hermano del repo del backend, con carpeta `data/` ignorada por git donde van a caer los `.pbf` y `.osrm*` generados en las tasks siguientes.

- [ ] **Step 1: Crear la carpeta e inicializar el repo**

Run:
```bash
mkdir -p "C:/proyectos/cadeteria/routing"
cd "C:/proyectos/cadeteria/routing"
git init
```
Expected: `Initialized empty Git repository in .../routing/.git/`. **No** debe quedar como subcarpeta del repo del backend — confirmar con `git rev-parse --show-toplevel` que devuelve `.../routing`, no `.../cadeteria`.

- [ ] **Step 2: Crear el .gitignore**

`routing/.gitignore`:
```
data/
```

- [ ] **Step 3: Crear el README con el esqueleto**

`routing/README.md`:
```markdown
# Motor de ruteo propio (OSRM)

Motor OSRM auto-hospedado con el extracto de la provincia de Tucumán, standalone —
**no está linkeado al backend**. Ver `documentacion/spec-routing-propio.md` (repo `cadeteria/`)
para el diseño completo.

## Cómo levantarlo desde cero

(completar en Task 8, luego de tener los scripts y el compose armados)
```

- [ ] **Step 4: Verificar**

Run (desde `C:/proyectos/cadeteria/routing`):
```bash
ls
```
Expected: aparecen `.gitignore` y `README.md`.

- [ ] **Step 5: Commit**

```bash
cd "C:/proyectos/cadeteria/routing"
git add .gitignore README.md
git commit -m "Crea estructura base de routing/ para el motor OSRM propio"
```

---

### Task 3: Script de descarga del extracto de Argentina

**Files (todos relativos a `C:/proyectos/cadeteria/routing`, la raíz de este repo):**
- Create: `scripts/01-descargar-extracto.sh`

**Interfaces:**
- Produces: `data/argentina-latest.osm.pbf`, que consume el Task 4.

- [ ] **Step 1: Escribir el script**

`scripts/01-descargar-extracto.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
mkdir -p data

if [ -f data/argentina-latest.osm.pbf ]; then
  echo "Ya existe data/argentina-latest.osm.pbf, no se vuelve a descargar."
  exit 0
fi

echo "Descargando extracto de Argentina desde Geofabrik (varios cientos de MB, puede tardar)..."
curl -L -o data/argentina-latest.osm.pbf "https://download.geofabrik.de/south-america/argentina-latest.osm.pbf"
echo "Listo:"
ls -lh data/argentina-latest.osm.pbf
```

- [ ] **Step 2: Dar permisos de ejecución**

Run:
```bash
cd "C:/proyectos/cadeteria/routing"
chmod +x scripts/01-descargar-extracto.sh
```

- [ ] **Step 3: Ejecutar y verificar**

Run:
```bash
cd "C:/proyectos/cadeteria/routing"
./scripts/01-descargar-extracto.sh
```
Expected: termina imprimiendo el tamaño del archivo (varios cientos de MB), sin error. Correrlo una segunda vez debe imprimir "Ya existe..." y salir al toque (idempotente).

- [ ] **Step 4: Commit**

```bash
cd "C:/proyectos/cadeteria/routing"
git add scripts/01-descargar-extracto.sh
git commit -m "Agrega script de descarga del extracto OSM de Argentina"
```

---

### Task 4: Script de recorte a la provincia de Tucumán

**Files (relativos a `C:/proyectos/cadeteria/routing`):**
- Create: `scripts/02-recortar-tucuman.sh`

**Interfaces:**
- Consumes: `data/argentina-latest.osm.pbf` (Task 3).
- Produces: `data/tucuman.osm.pbf`, que consume el Task 5.

- [ ] **Step 1: Escribir el script**

`scripts/02-recortar-tucuman.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

# left,bottom,right,top -- provincia de Tucuman con margen (cubre localidades del interior)
BBOX="-66.4,-28.0,-64.15,-25.85"

if [ ! -f data/argentina-latest.osm.pbf ]; then
  echo "Falta data/argentina-latest.osm.pbf -- corre primero 01-descargar-extracto.sh" >&2
  exit 1
fi

echo "Recortando a la provincia de Tucuman (bbox $BBOX)..."
docker run --rm -v "$(pwd)/data:/data" debian:bookworm-slim bash -c "
  apt-get update -qq && apt-get install -y -qq osmium-tool >/dev/null &&
  osmium extract -b $BBOX /data/argentina-latest.osm.pbf -o /data/tucuman.osm.pbf --overwrite
"
echo "Listo:"
ls -lh data/tucuman.osm.pbf
```

- [ ] **Step 2: Dar permisos de ejecución**

Run:
```bash
cd "C:/proyectos/cadeteria/routing"
chmod +x scripts/02-recortar-tucuman.sh
```

- [ ] **Step 3: Ejecutar y verificar**

Run:
```bash
cd "C:/proyectos/cadeteria/routing"
./scripts/02-recortar-tucuman.sh
```
Expected: termina imprimiendo el tamaño de `tucuman.osm.pbf` — debe ser **mucho menor** al de `argentina-latest.osm.pbf` (una provincia vs. el país entero).

- [ ] **Step 4: Commit**

```bash
cd "C:/proyectos/cadeteria/routing"
git add scripts/02-recortar-tucuman.sh
git commit -m "Agrega script de recorte del extracto a la provincia de Tucuman"
```

---

### Task 5: Preparar el perfil `car`

**Files (relativos a `C:/proyectos/cadeteria/routing`):**
- Create: `scripts/03-preparar-perfil.sh`

**Interfaces:**
- Consumes: `data/tucuman.osm.pbf` (Task 4).
- Produces: `data/car/tucuman.osrm*`, que consume el Task 7 (docker-compose).

- [ ] **Step 1: Escribir el script (parametrizado por perfil, se reusa en Task 6)**

`scripts/03-preparar-perfil.sh`:
```bash
#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."

PERFIL="${1:?Uso: 03-preparar-perfil.sh car|bicycle}"
if [ "$PERFIL" != "car" ] && [ "$PERFIL" != "bicycle" ]; then
  echo "Perfil invalido: $PERFIL (debe ser car o bicycle)" >&2
  exit 1
fi

if [ ! -f data/tucuman.osm.pbf ]; then
  echo "Falta data/tucuman.osm.pbf -- corre primero 02-recortar-tucuman.sh" >&2
  exit 1
fi

mkdir -p "data/$PERFIL"
cp data/tucuman.osm.pbf "data/$PERFIL/tucuman.osm.pbf"

echo "Preparando perfil $PERFIL (extract + partition + customize)..."
docker run --rm -v "$(pwd)/data/$PERFIL:/data" osrm/osrm-backend osrm-extract -p "/opt/$PERFIL.lua" /data/tucuman.osm.pbf
docker run --rm -v "$(pwd)/data/$PERFIL:/data" osrm/osrm-backend osrm-partition /data/tucuman.osrm
docker run --rm -v "$(pwd)/data/$PERFIL:/data" osrm/osrm-backend osrm-customize /data/tucuman.osrm

echo "Listo, archivos generados:"
ls "data/$PERFIL/" | grep tucuman.osrm
```

- [ ] **Step 2: Dar permisos de ejecución**

Run:
```bash
cd "C:/proyectos/cadeteria/routing"
chmod +x scripts/03-preparar-perfil.sh
```

- [ ] **Step 3: Ejecutar para `car` y verificar**

Run:
```bash
cd "C:/proyectos/cadeteria/routing"
./scripts/03-preparar-perfil.sh car
```
Expected: las tres etapas (`osrm-extract`, `osrm-partition`, `osrm-customize`) corren sin error; al final `ls data/car/` muestra `tucuman.osrm`, `tucuman.osrm.partition`, `tucuman.osrm.mldgr`, etc.

- [ ] **Step 4: Commit**

```bash
cd "C:/proyectos/cadeteria/routing"
git add scripts/03-preparar-perfil.sh
git commit -m "Agrega script de preparacion OSRM por perfil y prepara el perfil car"
```

---

### Task 6: Preparar el perfil `bicycle`

**Files:** ninguno nuevo — reusa `scripts/03-preparar-perfil.sh` del Task 5.

**Interfaces:**
- Consumes: `data/tucuman.osm.pbf` (Task 4), script del Task 5.
- Produces: `data/bicycle/tucuman.osrm*`, que consume el Task 7.

- [ ] **Step 1: Ejecutar el script con el perfil bicycle**

Run:
```bash
cd "C:/proyectos/cadeteria/routing"
./scripts/03-preparar-perfil.sh bicycle
```
Expected: igual que en Task 5 pero para bicicleta; `ls data/bicycle/` muestra los mismos archivos `tucuman.osrm*`.

- [ ] **Step 2: No hace falta commit** (no se generó ningún archivo versionable — todo cae en `data/`, gitignoreado).

---

### Task 7: `docker-compose.routing.yml` — levantar ambos perfiles

**Files (relativos a `C:/proyectos/cadeteria/routing`):**
- Create: `docker-compose.routing.yml`

**Interfaces:**
- Consumes: `data/car/tucuman.osrm*` (Task 5), `data/bicycle/tucuman.osrm*` (Task 6).
- Produces: dos servicios OSRM corriendo — `car` en `localhost:5000`, `bicycle` en `localhost:5001`.

- [ ] **Step 1: Escribir el compose**

`docker-compose.routing.yml`:
```yaml
services:
  osrm-car:
    image: osrm/osrm-backend
    command: osrm-routed --algorithm mld /data/tucuman.osrm
    volumes:
      - ./data/car:/data
    ports:
      - "5000:5000"
    restart: unless-stopped

  osrm-bicycle:
    image: osrm/osrm-backend
    command: osrm-routed --algorithm mld /data/tucuman.osrm
    volumes:
      - ./data/bicycle:/data
    ports:
      - "5001:5000"
    restart: unless-stopped
```

- [ ] **Step 2: Levantarlo**

Run:
```bash
cd "C:/proyectos/cadeteria/routing"
docker compose -f docker-compose.routing.yml up -d
```
Expected: los dos contenedores quedan `Up` (`docker compose -f docker-compose.routing.yml ps`).

- [ ] **Step 3: Verificar el perfil car con una consulta real**

Run (coordenadas de ejemplo dentro de San Miguel de Tucumán — lng,lat en ese orden, como pide OSRM):
```bash
curl "http://localhost:5000/route/v1/driving/-65.2226,-26.8083;-65.2176,-26.8241?overview=false"
```
Expected: JSON con `"code":"Ok"` y una ruta con `distance` y `duration` en metros/segundos, valores razonables (unos pocos km).

- [ ] **Step 4: Verificar el perfil bicycle**

Run:
```bash
curl "http://localhost:5001/route/v1/driving/-65.2226,-26.8083;-65.2176,-26.8241?overview=false"
```
Expected: igual que el Step 3, `"code":"Ok"` con distancia/duración (la duración debería ser mayor que en auto, mismo trayecto en bici).

- [ ] **Step 5: Commit**

```bash
cd "C:/proyectos/cadeteria/routing"
git add docker-compose.routing.yml
git commit -m "Agrega docker-compose para levantar el motor OSRM propio (car + bicycle)"
```

---

### Task 8: Comparar contra OSRM público y completar el README

**Files (relativos a `C:/proyectos/cadeteria/routing`):**
- Modify: `README.md`

**Interfaces:** ninguna — task de verificación/documentación final.

- [ ] **Step 1: Comparar las mismas coordenadas contra el servidor público**

Run:
```bash
curl "http://router.project-osrm.org/route/v1/driving/-65.2226,-26.8083;-65.2176,-26.8241?overview=false"
```
Expected: la `distance` debe ser muy similar (mismo motor OSRM, mismos datos de calles de OSM) a la del Step 3 del Task 7 — confirma que el motor propio está bien armado.

- [ ] **Step 2: Completar el README con instrucciones reales**

`README.md` (reemplaza el esqueleto del Task 2):
```markdown
# Motor de ruteo propio (OSRM)

Motor OSRM auto-hospedado con el extracto de la provincia de Tucumán, standalone —
**no está linkeado al backend**. Ver `documentacion/spec-routing-propio.md` (repo `cadeteria/`)
para el diseño completo.

## Cómo levantarlo desde cero

```bash
./scripts/01-descargar-extracto.sh
./scripts/02-recortar-tucuman.sh
./scripts/03-preparar-perfil.sh car
./scripts/03-preparar-perfil.sh bicycle
docker compose -f docker-compose.routing.yml up -d
```

## Cómo probarlo

- Auto/moto: `http://localhost:5000/route/v1/driving/{lng1},{lat1};{lng2},{lat2}`
- Bicicleta: `http://localhost:5001/route/v1/driving/{lng1},{lat1};{lng2},{lat2}`
- Map-matching de una traza GPS: `http://localhost:5000/match/v1/driving/{lng1},{lat1};{lng2},{lat2};...?timestamps=t1;t2;...&overview=full&geometries=geojson`

## Cómo reconstruirlo (si cambia el mapa de OSM o hace falta reprocesar)

Borrar `data/` y volver a correr los 4 comandos de arriba desde cero.

## Estado

No está conectado al backend todavía. Es la base para `documentacion/spec-routing-propio.md`
§4 (Subproyecto B — pipeline de matching de trazas GPS de los cadetes).
```

- [ ] **Step 3: Commit**

```bash
cd "C:/proyectos/cadeteria/routing"
git add README.md
git commit -m "Completa el README de routing/ con instrucciones de uso y verificacion"
```

---

## Hito final

Motor OSRM propio corriendo en Docker, perfiles `car` (5000) y `bicycle` (5001), verificado contra
coordenadas conocidas y comparado contra el servidor OSRM público. Listo como base para el plan de
`documentacion/spec-routing-propio.md` §4 (pipeline de matching de trazas GPS).
