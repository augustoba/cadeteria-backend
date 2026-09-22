# Geocoding y cache de direcciones — Documento de diseño

> **Estado:** Fase 1 (cache + punto único, §11) implementada 2026-09-21, sin Google. Fase 2
> (bot), 2b (ecommerce) y 3 (GPS) siguen pendientes.
> **Alcance:** convertir **direcciones escritas** ("Av. Aconquija 1500") en **coordenadas**
> (lat/lng) para poder cotizar por distancia — **gratis o casi gratis**, rápido, y **compartido**
> por todos los que geocodifican: página `/pedir`, panel admin, bot de WhatsApp y el **backend del
> ecommerce** (consumidor externo por endpoint interno, ver §6.1).
> **Complementa a** [`spec-whatsapp-bot-ia.md`](./spec-whatsapp-bot-ia.md): el bot usa el
> geocoding como herramienta; este documento define cómo funciona ese geocoding por debajo.
> **Complementa a** [`spec-infraestructura-despliegue.md`](./spec-infraestructura-despliegue.md):
> ahí está dónde corre todo y con qué presupuesto; acá, cómo se comparte la cache entre sistemas.

---

## 1. El problema

- El cliente **siempre escribe una dirección en palabras**. Nunca da lat/lng.
- El endpoint que calcula el precio, `GET /api/publico/cotizar` (`CotizacionController`),
  **necesita coordenadas** (`origenLat`, `origenLng`, `destinoLat`, `destinoLng`).
- En el medio hace falta un **geocodificador**: `texto → coordenadas`.

```
"Av. Aconquija 1500"  ──geocodificar──►  {lat, lng}  ──cotizar──►  $X
   (lo que escribe el cliente)              (coordenadas)          (backend, gratis)
```

Cada cotización geocodifica **2 direcciones** (origen y destino).

> **Volumen real (actualizado 2026-09-21):** la demanda **bajó** — hoy son **~70 viajes/día**
> (no los ~300 que se suponían al principio). Eso son **~140 geocodes/día** como piso, ~200-250 con
> cotizaciones y reintentos. A este volumen **hasta el free tier de Google (10.000/mes) alcanzaría
> para cubrir todo gratis** (ver §4). **Ese es el único punto del sistema que podría costar plata** —
> de ahí todo este documento.

---

## 2. Qué ya existe en el código

| Pieza | Archivo | Qué hace hoy |
|---|---|---|
| Proxy de geocoding server-side | `service/GeocodingProxyService.java` | Busca direcciones con **Nominatim + Geoapify**, sesgado a Tucumán. Devuelve candidatos con flag `approximate`. También `reverse(lat,lng)`. |
| Pool de API keys | `service/ApiKeyPoolService.java` | Rota entre **varias keys gratuitas** por proveedor (Geoapify, GraphHopper, OpenRouteService); ante un 429/403 pasa a la siguiente; estima cupo restante y avisa al panel. |
| Cálculo de precio | `service/CotizacionService.java` + `CotizacionController` | Precio por distancia/zona a partir de coordenadas. **Público.** |
| Distancia / zona | `service/GeocodingService.java` | `distanciaKm(haversine)` y `resolverZona(lat,lng)`. |
| Ruteo | `service/RutaService.java` | Rutas reales (GraphHopper/OpenRouteService) — usa el mismo pool de keys. |
| Geocoding del front | `cadeteria-frontend/src/app/core/services/geocoding.service.ts` | Llama **directo desde el navegador** a Nominatim, Photon, Geoapify y LocationIQ. **Tiene las keys hardcodeadas en el bundle.** |
| Rate limit | `service/RateLimitService.java` + `RateLimitFilter` | Limita llamadas **entrantes** por IP a `/api/publico/**`. **No** limita las salientes a los proveedores. |

**Conclusión del relevamiento:** ya hay un geocoding multi-proveedor gratuito funcionando. Lo que
**falta** es: (1) la **cache**, (2) que sea **un único punto compartido** por front + panel + bot,
y (3) cerrar los problemas de **keys expuestas** y **throttle a Nominatim**.

---

## 3. Proveedores de geocoding — opciones, pros y contras

### 3.1 Pagos

**Google Geocoding API**
- ✅ El más preciso, mejor cobertura de calles/alturas en Argentina.
- ✅ Uso comercial permitido y claro.
- ✅ **Free tier de 10.000/mes** (~333/día). **Al volumen actual (~70 viajes/día = ~140-250
  geocodes/día = ~4.000-7.500/mes) entra entero en el free tier → $0.**
- ⚠️ Después de los 10.000/mes cobra **$5 por cada 1.000**.
- ❌ **Obliga a cargar tarjeta / habilitar facturación** incluso para el free tier. Si el consumo se
  pasa del tope y no hay control, **cobra solo**.
- ✅ **Mitigación obligatoria (el "tope duro"):** configurar en la consola de Google Cloud un
  **límite de cuota diario** (p.ej. 300 requests/día) al Geocoding API. Con eso **físicamente no
  puede facturar**: al llegar al tope devuelve error y el sistema salta al siguiente proveedor
  gratuito. El $0 queda garantizado por configuración, no por la lógica de rebalse.
- **Estrategia acordada:** usarlo **primero** (por precisión/cobertura) **con tope duro**, y que las
  gratuitas hagan de **rebalse** cuando Google llega a su tope diario o al free tier mensual (ver §10).

> **Nota histórica:** con el supuesto viejo de ~300 solicitudes/día (~21.000 llamadas/mes) Google
> costaba **~$55/mes** y por eso se había descartado. Al bajar la demanda a ~70 viajes/día, **Google
> pasa a ser viable gratis** dentro de su free tier. La decisión "gratis sí o sí" ya no obliga a
> excluirlo.

### 3.2 Gratuitos que SÍ permiten uso comercial (freemium) ⭐

**Geoapify**
- ✅ **3.000 búsquedas/día gratis**, sin tarjeta. Permite uso comercial dentro del cupo.
- ✅ Buena precisión, devuelve campo `street`/`housenumber` (sirve para la clave canónica).
- ✅ Ya integrado (`GeocodingProxyService`, `ApiKeyPoolService`).
- ❌ Sobre el cupo hay que pagar o rotar de cuenta.

**LocationIQ**
- ✅ **5.000 búsquedas/día gratis**. Permite uso comercial dentro del cupo.
- ✅ Basado en OSM, buena cobertura.
- ❌ Solo está en el front hoy; habría que sumarlo al backend.

> **Entre los dos = 8.000 geocodes/día gratis y legales**, contra los ~600-1.000/día que se
> necesitan. **Sobra margen ~10x sin gastar nada y sin violar términos.** Esta es la base
> recomendada.

### 3.3 Gratuitos SIN key, pero con uso comercial restringido (respaldo)

**Nominatim (OpenStreetMap público)**
- ✅ Gratis, sin key, ya integrado.
- ❌ Su política **prohíbe uso comercial automatizado intensivo** y exige **≤1 petición/segundo**.
  Una cadetería haciendo 700 geocodes automáticos/día es justo lo que piden que autoalojes.
  **Pueden bloquear la IP.**
- **Uso:** solo **último respaldo**, con **throttle de 1 req/seg** del lado saliente (hoy no existe).

**Photon (Komoot, OSM)**
- ✅ Gratis, sin key, ya usado en el front.
- ❌ Mismas restricciones de etiquette que Nominatim (instancia pública, uso comercial limitado).
- **Uso:** respaldo, igual que Nominatim.

### 3.4 Autoalojado

**Pelias / Nominatim propio (en tu servidor)**
- ✅ **Gratis y sin límites** legales ni de tasa; control total.
- ❌ Hay que instalarlo y mantenerlo (correr un extracto del mapa de Argentina, índice, updates).
- ❌ Trabajo de infra inicial y continuo.
- **Cuándo:** si se quiere "costo cero" total y sin depender de terceros, aceptando el mantenimiento.

### 3.5 Descartado: scrapear Google Maps web

Se evaluó "que el bot busque en la página de Google Maps como si fuera una persona".
- ❌ **Viola los ToS de Google** (prohibido el scraping automatizado).
- ❌ **No es "sin límites":** Google tiene anti-bot agresivo (CAPTCHA, bloqueo de IP, análisis de
  comportamiento). 700 búsquedas/día desde una IP fija se bloquean en horas.
- ❌ Para evadirlo hay que pagar **proxies residenciales + resolvedores de CAPTCHA + fleet de
  navegadores** → **más caro** que la API oficial.
- ❌ **Frágil:** se rompe cada vez que Google cambia el marcado.
- **Veredicto: descartado.** Es más caro, más frágil y legalmente peor que los gratuitos legítimos.

---

## 4. Costos (números verificados 2026-09)

**Volumen real actual:** ~70 viajes/día × 2 direcciones = **~140 geocodes/día**; con cotizaciones
sueltas y reintentos, **~200-250/día** → **~6.000-7.500/mes**. (La demanda bajó; antes se suponían
~300/día, ver nota en §3.1.)

**Google Geocoding** (10.000/mes gratis, luego $5/1.000):
| Escenario | Llamadas/mes | ¿Entra en free tier? | Costo |
|---|---|---|---|
| **Actual (~70 viajes/día)** | ~6.000-7.500 | **Sí** | **$0** |
| Con cache caliente (repetidas) | ~3.000-4.000 | Sí, holgado | **$0** |
| Si la demanda vuelve a ~300/día | ~21.000 | No (11.000 billable) | ~$55/mes |

> O sea: **al volumen de hoy, Google sale $0** (entra en el free tier), y con el **tope duro** de la
> consola (§3.1) **no puede** pasar a cobrarse aunque algo falle.

**Geoapify + LocationIQ (gratuitos):** 8.000/día disponibles vs ~140-250/día necesarios → **$0**, con
**~30x de margen** a este volumen.

> La **cache** (§5) reduce las llamadas reales y hace que crezcan **más lento** que los pedidos
> (las direcciones repetidas no se vuelven a geocodificar). A este volumen la cache **no es
> necesaria para el costo** (todo entra gratis igual), pero **conviene** por velocidad, por proteger
> los límites ante picos, y porque deja armada la base de direcciones que se reutiliza siempre.

---

## 5. La cache de direcciones (el corazón del diseño)

**Por qué:**
1. **Ahorro:** cada dirección repetida es una llamada menos al proveedor.
2. **Velocidad:** respuesta inmediata al cliente (importante en el bot).
3. **Protección:** los 600-1.000/día bajan mucho → margen de sobra contra los límites gratuitos.
4. **Menos dependencia:** si un proveedor se cae, la cache sigue respondiendo lo ya conocido.

En una cadetería la **repetición es altísima**: el origen es la casa del cliente (los recurrentes
piden siempre desde ahí) y los destinos se concentran (terminal, centro, shoppings, farmacias,
hospitales, barrios).

### 5.1 Insight clave: cachear por CUADRA, no por número exacto

El precio se calcula por **distancia**. Dos direcciones puerta con puerta (1500 y 1502) están a
~10 m → **distancia y precio idénticos** a efectos prácticos. Por eso **no hace falta** la
coordenada exacta de cada número: alcanza con la de la **cuadra**.

- Si se cachea por número exacto, "1502" es un miss aunque "1500" esté cacheado. **Mal.**
- Si se cachea por **cuadra** (bloque de centena: 1500-1599), el 1502 **pega** en la cache del
  1500. **Bien.**

La cuadra natural en Argentina comparte el rango de centena de la altura. Con redondear el número
a la centena ya está la clave. Error máximo ~100-150 m, que en el precio no mueve la aguja — y
recordar que `cotizar` devuelve un precio **sugerido, no vinculante** (el admin lo ajusta).

### 5.2 Insight clave: la clave es CANÓNICA, no el texto crudo

"av aconquija", "aconquija", "avenida aconquija" son **la misma calle**. Si la clave fuera el
texto crudo, la cache se fragmenta. La solución son **dos capas**:

**Capa 1 — Normalización local (sin llamar a nadie):** minúsculas, sin acentos, espacios
colapsados, y quitar genéricos (`av`, `avenida`, `calle`, `presidente`, `gral`, `san/santa`, `ruta`).
Esto hace colapsar la mayoría de las variantes.

**Capa 2 — Clave canónica del geocodificador (la autoritativa):** cuando hay que geocodificar, el
proveedor **devuelve el nombre oficial** de la calle (Geoapify: `street`; Nominatim: `road`). La
cache se guarda con **ese** nombre canónico + cuadra + localidad. Así, cualquier variante que
geocodifique a la misma calle oficial pega en la misma entrada. **No se adivina la calle: se usa
la que dijo el geocoder.**

### 5.3 Tabla de alias/variantes (la que propone el dueño)

Para no volver a geocodificar una variante ya vista, se guarda **cómo la escribió el cliente**:

```
direccion_alias
  variante_norm     "av peron"            ← texto normalizado (casing/acentos/espacios), SIN quitar genéricos
  localidad         "smt"
  calle_canonica    "presidente peron"    ← la que devolvió el geocoder la primera vez
```

**Separación importante:** el **alias** resuelve *cómo le dicen a la calle* (muchas variantes → 1
canónica); las **coordenadas** se guardan aparte por *calle canónica + cuadra* (1 fila compartida
por todas las variantes). Así no se duplican coordenadas.

```
cuadra_coords
  calle_canonica    "presidente peron"
  localidad         "smt"
  cuadra            1200            ← bloque (1200-1299)
  lat, lng          -26.81xx, -65.20xx   ← punto representativo (ESTIMATIVO)
  approximate       true/false
  proveedor         "geoapify"
  confirmaciones    N               ← cuántas veces se usó/validó (útil a futuro)
  creada_en         timestamp
```

**Flujo de lookup:**
1. Llega "av perón 1202". Normalizar → `"av peron"` + número 1202.
2. Buscar `"av peron"` en `direccion_alias` → **hit** → canónica "presidente peron". (0 llamadas)
3. Buscar "presidente peron" + cuadra 1200 en `cuadra_coords` → **hit** → coordenadas. (0 llamadas)
4. Si algo **miss**: geocodificar **una vez**, guardar la variante nueva en `direccion_alias`
   (apuntando a la canónica que devolvió el geocoder) y la cuadra en `cuadra_coords`. La próxima
   vez ya es gratis.

El sistema **aprende solo**: cada forma nueva de escribir una calle se paga **una única vez**.

### 5.4 Detalles y cuidados

- **Incluir la localidad** en las claves de alias y de cuadra (si no, "San Martín 500" de dos
  ciudades chocan).
- **Negative cache:** cachear también los "no encontrado" por unas horas, para no re-martillar una
  dirección mala.
- **Sin TTL para coordenadas:** las direcciones no se mueven; una entrada cacheada sirve para
  siempre. (Solo re-geocodificar si era `approximate` y después se consigue precisión.)
- **Conservador al normalizar:** la normalización es solo la capa rápida; la **autoritativa es la
  canónica del geocoder**, que no se equivoca de calle.
- **Alias solo de resultados reales:** no inventar la canónica; guardar la que el geocoder devolvió.

---

## 6. Cache COMPARTIDA: un único punto de geocoding

**Problema detectado:** hoy hay geocoding en **dos lugares** que no comparten nada:
- El front (`geocoding.service.ts`) llama **directo** a los proveedores desde el navegador.
- El backend (`GeocodingProxyService`) solo para `/pedir`.

Si el cliente cotiza en `/pedir` (2 geocodes) y después pregunta por WhatsApp (otros 2), son **4
llamadas por el mismo viaje**. Desperdicio evitable.

**Solución:**

> **Un único servicio de geocoding en el backend, dueño de la cache, y que TODOS lo consuman:
> la página `/pedir`, el panel admin y el bot de WhatsApp.**

- El primero que geocodifica una dirección **llena la cache para todos**.
- Cuando el cliente vuelve por otro canal, es **hit** → 0 llamadas.
- Como se guardan **la variante escrita (alias) y la canónica + cuadra**, incluso si la escribe
  distinto en el otro canal, la cache por cuadra canónica igual pega.

**Cambio concreto:**
- Migrar el **front** (`/pedir` y el panel) a llamar al **endpoint de geocoding del backend**, en
  vez de a los proveedores directo.
- **Bonus de seguridad:** al pasar todo por el backend, **desaparecen las keys expuestas** en el
  bundle del front (hoy `GEOAPIFY_API_KEY` y `LOCATIONIQ_API_KEY` están hardcodeadas y visibles).
- El **bot** llama al **mismo servicio en proceso** (no por HTTP): no pasa por el rate-limit por IP
  y obtiene la lista de candidatos + flags `approximate` para la desambiguación.

### 6.1 Consumidor externo: el ecommerce (decisión 2026-09-21)

El ecommerce es un **backend Spring Boot separado** (ver
[`spec-infraestructura-despliegue.md`](./spec-infraestructura-despliegue.md) §4) con su **propio
schema MySQL**. Aun así, **no debe tener su propia cache de direcciones**: si los dos sistemas
cachearan por separado, cada uno tendría ~la mitad del hit rate y se duplicaría el gasto de API —
y se partiría en dos el activo que después se quiere vender como SaaS.

> **Regla:** la cache es una sola y la dueña es la cadetería. El ecommerce es un consumidor más,
> igual que `/pedir`, el panel y el bot.

**Alternativa evaluada y descartada — acceso directo cross-schema.** Como ambos schemas vivirían en
el **mismo contenedor MySQL**, alcanzaría con un `GRANT SELECT, INSERT, UPDATE ON
cadeteria.geo_cache TO 'ecommerce_user'@'%'` y consultar con nombre calificado, sin segundo
datasource. Funciona, y es menos código hoy. Se descarta porque:

- **Acopla al schema:** renombrar una columna rompe el ecommerce en runtime, sin señal en
  compilación.
- **Muere el día que se separen los servidores** — que es el plan declarado en cuanto haya ingresos.
- Obliga a poner la normalización en un *stored function* de MySQL para que no diverja, y eso es
  incómodo de versionar y de testear.
- El usuario MySQL del ecommerce queda con acceso a otro schema (mayor superficie si se filtra la
  credencial).

El endpoint cuesta ~1 hora de trabajo y sobrevive la separación. Gana.

**Contrato propuesto — lectura Y escritura:**

Si solo se expusiera el `insert`, el ecommerce igual necesitaría **leer** la cache, y terminaríamos
con los dos acoplamientos a la vez (lee por DB, escribe por HTTP). Con las dos operaciones el
ecommerce deja de saber que existe la base de la cadetería.

```
GET  /api/interno/geo/buscar?texto=av%20peron%201500
     → { lat, lng, cuadra, calleCanonica, approximate, hit }

POST /api/interno/geo/aporte
     { variantes: ["av perón 1500", "perón 1500"],
       lat, lng, approximate, fuente }
     → { calleCanonica, cuadra, creado }
```

Detalles no negociables:

| Punto | Por qué |
|---|---|
| El aporte recibe **texto crudo**, nunca claves ya normalizadas | Si el ecommerce canonicaliza de su lado, volvimos a tener **dos implementaciones** que derivan. Canonicaliza siempre el dueño de la tabla |
| Ruta `/api/interno/`, **no** `/api/publico/` | Es otro nivel de auth: token compartido en header, guardado en Configuración y rotable. No pasa por el `RateLimitFilter` público |
| **Idempotente**: upsert sobre el unique constraint `(calle_canonica, cuadra, localidad)` | El ecommerce puede reintentar sin duplicar. Nada de *check-then-insert* — dos pedidos simultáneos crean duplicados |
| Devolver `creado: true/false` | El contador de **hit/miss ponderado por tráfico** sale gratis y en un solo lugar |

**Si la cadetería está caída**, el ecommerce pierde la cache pero no la función: cae a su propia
cadena de proveedores y simplemente no aporta ni lee. **Degrada sin romper**, igual que
`RutaService` y `FcmService`.

### 6.2 De quién es la key de geocoding (decisión 2026-09-21)

Hoy los dos sistemas son de la misma familia (cadetería del padre, ecommerce de la hermana), así que
cada uno geocodifica **con su propia key** y aporta el resultado a la cache común. Eso incluye la
posibilidad de que el ecommerce use **Google con la tarjeta de la hermana**: al volumen actual el
free tier de 10.000/mes lo cubre entero → $0.

> **Poner hard cap en la consola de Google Cloud también en la cuenta del ecommerce**, igual que en
> la de la cadetería. Si su página pública de cotización recibe scripting, la factura le llega a
> ella. Dos minutos de configuración.

**Descartado: BYOK de comercios terceros.** Se evaluó pedirle a cada cliente del futuro SaaS que
cree su cuenta de Google (configurada en su PC, en el local) y nos pase la key, para poblar la cache
sin costo. Se descarta por dos razones:

1. **Onboarding inviable:** la Geocoding API exige cuenta con *billing* configurado — o sea tarjeta
   cargada aunque nunca facture — más habilitar la API, crear la key y restringirla. Un dueño de bar
   no hace eso. Y con **nuestra** key el comercio no toca Google en absoluto: instala y funciona. El
   objetivo ("que no carguen tarjeta") se cumple mejor con el diseño opuesto.
2. **ToS:** consolidar en una base propia los resultados obtenidos bajo las cuotas gratuitas de
   decenas de terceros es exactamente lo que las cláusulas anti-circumvención apuntan. Entre
   empresas de la misma familia, cada una con su cuenta y para sus propios pedidos, el perfil de
   riesgo es otro y es aceptable; a escala SaaS con clientes ajenos, no.

**Y no hace falta:** con Geoapify (3.000/día) + LocationIQ (5.000/día), incluso sumando 20 comercios
a ~50 geocodes/día cada uno (~1.250/día en total) **entra en una sola key propia**. El esquema BYOK
no compra ningún ahorro.

> **Cuando escale de verdad, se paga.** Nominatim y Photon **prohíben uso comercial automatizado
> pesado** (§3.3), así que no son opción para un producto vendido. En ese punto un proveedor de
> $20-50/mes es **costo de la mercadería** que se traslada al precio del SaaS — se cubre con el
> primer cliente.

### 6.3 Expectativa de crecimiento: cobertura ≠ hit rate

Con los dos sistemas aportando, hasta fin de 2026 (~100 días) y a ~70 viajes/día más el tráfico del
ecommerce, descontando repeticiones, se capturan del orden de **1.500–2.500 cuadras nuevas**.

Eso **no cubre la ciudad** — San Miguel y el gran San Miguel tienen bastante más cuadras que eso.
Cubre **las cuadras que generan pedidos**, que es lo que importa: la demanda está concentrada, así
que 2.000 cuadras bien puestas pueden dar el 80% de los aciertos.

> **La métrica correcta es hit rate ponderado por tráfico, no cantidad de filas.** Se puede tener
> 10.000 filas y 40% de aciertos, o 1.500 filas y 85%. Por eso el flag `creado` del endpoint de
> aporte (§6.1) y el contador de hit/miss se implementan **desde el día 1**: es el único número que
> dice si el flywheel funciona y cuándo haría falta pagar un proveedor.

---

## 7. Precisión: estimativa vs. exacta

**Decisión acordada:** la coordenada del geocoder es **siempre estimativa** (nivel cuadra).

| | Cotizar | Entregar (el cadete va) |
|---|---|---|
| Precisión necesaria | Aproximada (cuadra) | Dirección exacta |
| Fuente | Cache por cuadra (gratis) | **Dirección escrita + referencias** |
| Quién la usa | Backend (`cotizar`) | El cadete, guiándose por el texto |

- **El cadete NO navega con la coordenada estimativa.** Se guía por la **dirección escrita exacta**
  + **referencias** ("entre qué calles", color de la casa, piso/depto) + **teléfono del cliente**.
- Si alguna vez se muestra un pin, **rotularlo "zona aproximada — guiáte por la dirección"**, para
  que nadie confíe ciego en un punto que puede estar ~100 m corrido.
- **Pin del cliente por WhatsApp** (opcional): si el cliente comparte su ubicación, llega lat/lng
  **exacta y gratis** → se guarda como coordenada precisa del pedido. Bonus, no requisito.

---

## 8. Pool de API keys (`ApiKeyPoolService`) — pros y contras

**Cómo funciona hoy:** permite cargar **varias keys gratuitas** por proveedor (una cuenta nueva por
key) y rota a la siguiente ante un 429/403 o al llegar al cupo diario estimado. Estado en memoria
(se reinicia con el backend, pero los límites son diarios así que no importa).

- ✅ Da resiliencia: si una cuenta se queda sin cupo, siguen las otras.
- ✅ Avisa al panel cuando el pool se agota o una key cae al 10% del cupo.
- ❌ **Riesgo de ToS:** crear **varias cuentas gratuitas** de un mismo proveedor para multiplicar el
  cupo suele violar sus términos (un free tier por organización). Si lo detectan, pueden banear
  **todas** las keys juntas.
- ⚠️ **Estado en memoria:** tras un reinicio, el contador `usadasHoy` se pierde y se podría
  sobrepasar el cupo real de una key (lo cubre el manejo de 429, pero es un borde).
- ⚠️ **Falta contador MENSUAL para Google:** el pool hoy cuenta cupo **diario** y se resetea cada
  día. El free tier de Google es **mensual** (10.000/mes). Si se usa Google, hay que agregarle un
  **presupuesto mensual** que se reinicie una vez al mes, para que el rebalse salte cuando corresponde.

> **Recomendación:** con el volumen actual **no hace falta el truco de las múltiples cuentas**: una
> key de Geoapify (3.000/día) + una de LocationIQ (5.000/día) + el free tier de Google cubren
> ~140-250 geocodes/día con **~30x de margen**. Dejar el pool multi-cuenta como **contingencia de
> emergencia**, no como plan principal. Así se evita el riesgo de ToS sin perder capacidad.

---

## 9. Pendientes técnicos detectados

1. **Throttle saliente a Nominatim/Photon:** no existe. Si se usan como respaldo, hay que
   serializar sus llamadas a **≤1 req/seg** (etiquette obligatorio). Geoapify/LocationIQ usan cupo
   diario, no esta restricción dura.
2. **Keys expuestas en el front:** se resuelven al migrar el front al endpoint del backend (§6).
3. **Sumar LocationIQ al backend:** hoy solo está en el front; agregarlo a `GeocodingProxyService`
   como primario junto a Geoapify.
4. **Interfaz intercambiable de proveedor:** dejar el geocoding detrás de una interfaz para poder
   pasar de Geoapify/LocationIQ a Google o a Pelias autoalojado **tocando una sola clase**.
5. **Contador mensual** en el pool para el free tier de Google (el pool hoy solo cuenta diario, §8).
6. **Tope duro de Google** en la consola de Google Cloud (límite de cuota diario) → garantiza $0.
7. **Rate limit + presupuesto diario blando** para la página pública de cotización (ver §10.1).

---

## 10. Estrategia recomendada (resumen)

**Cadena de proveedores (en orden), todos detrás de la cache y de una interfaz intercambiable:**

```
1. CACHE (si es hit, no se llama a nadie)         ← siempre primero
2. Google Geocoding  (con TOPE DURO en la consola) ← precisión/cobertura; $0 dentro del free tier
3. Geoapify          (3.000/día, gratis, comercial)
4. LocationIQ        (5.000/día, gratis, comercial)
5. Nominatim / Photon (respaldo best-effort, throttle 1 req/seg)
```

1. **Cache por cuadra + alias** (§5), compartida por front + panel + bot (§6). Va **delante de todo**.
2. **Google primero, con tope duro:** al volumen actual (~70 viajes/día) entra en el free tier → $0.
   El tope de cuota en la consola garantiza que **nunca** facture; si se pasa, rebalsa a Geoapify.
3. **Geoapify + LocationIQ de rebalse** (gratuitos, uso comercial permitido, ~30x de margen).
4. **Nominatim/Photon** como último respaldo, con throttle de 1 req/seg.
5. **Coordenada siempre estimativa** para cotizar; entrega por dirección escrita + referencias (§7).
6. **No depender del pool multi-cuenta** (riesgo de ToS); con una key por proveedor + el free tier de
   Google alcanza de sobra (§8).

> **Alternativa más simple (sin tarjeta):** empezar **directo con Geoapify + LocationIQ** (no piden
> tarjeta ni riesgo de facturación) y **prender Google después** detrás de la misma interfaz si se ve
> que le erran mucho a las direcciones. Las dos vías son válidas; la diferencia es si se quiere la
> cobertura de Google desde el día 1 (con tope duro, sin riesgo) o se prefiere no cargar tarjeta.

**Resultado: geocoding gratis, rápido y a prueba de límites, sin zonas grises legales.**

### 10.1 Sobre "abrir la página pública de cotización" (decisión 2026-09-21)

Se evaluó **no publicar** la página `/pedir` para que los clientes coticen solos, por miedo a que
(siendo la cache fría al principio) agoten el cupo de geocoding. **Conclusión: el miedo al cupo no
justifica cerrarla** — a ~70 viajes/día, ni la página abierta se acerca a saturar la cadena, y con el
tope duro de Google "agotar" solo significa **caer a otro proveedor gratuito**, nunca pagar ni romperse.

Lo que **sí** hay que poner (controles, no cerrar):
- **Rate limit por IP** en el endpoint de cotizar/geocodificar (ya existe `RateLimitFilter` para
  `/api/publico/**`; ajustarlo y aplicarlo a la búsqueda de direcciones). Esto frena el único riesgo
  real: **abuso** (un script martillando el endpoint), no el uso normal de clientes.
- **Presupuesto diario blando con prioridad interna:** si el total de geocodes/día supera un umbral N,
  la **página pública se degrada** ("cotización no disponible ahora, escribinos por WhatsApp") pero el
  **bot y el admin siguen con prioridad**. Protege lo interno sin cerrar nada.

**Secuencia correcta** (importante): la cache **solo se calienta si primero se construye y se rutea
todo por el backend**. Hoy no existe cache y el panel geocodifica desde el front (no alimenta nada).
Entonces:
1. Construir cache + punto único de geocoding en el backend (§6).
2. Que **admin y bot** pasen por ahí → la cache se calienta sola con los ~70 pedidos/día reales
   (**no hace falta la página pública para calentarla**).
3. **Después** abrir la página, ya con rate limit + presupuesto blando.

> Retener la página tiene sentido por **madurez del producto** (no publicar antes de que el flujo
> geocoding+cache+cotización esté construido y probado), **no por el cupo**. Y ojo con el costo de
> oportunidad: con la demanda baja, la página de cotización es una herramienta de **captación**; no
> conviene frenarla mucho tiempo.

---

## 11. Fases de implementación sugeridas

**Fase 1 — Cache + punto único**
- [x] Tablas `direccion_alias` y `cuadra_coords` (+ normalizador de texto + clave por cuadra) —
      2026-09-21: `DireccionAlias`/`CuadraCoords` (Hibernate `ddl-auto: update` las crea solas,
      sin migración manual) + `DireccionUtils.normalizar/cuadra`. **Sin Google todavía** (decisión
      del dueño 2026-09-21: no cargar tarjeta por ahora) — la cache se llena solo con lo que ya
      devuelven Nominatim/Geoapify.
      - `direccion_alias` (clave `variante_norm`, SIN localidad): resuelve cómo le dicen a la
        calle ("rivadavia" / "avenida rivadavia" / "av rivadavia" → misma canónica). No necesita
        localidad porque una forma de escritura no depende de en qué pueblo esté la calle.
      - `cuadra_coords` (clave `calle_canonica` + `localidad` + `cuadra`): la localidad SÍ es
        parte de la clave acá, porque la calle sí depende del pueblo ("Rivadavia" existe en San
        Miguel de Tucumán y en Lules, con coordenadas distintas). Si al buscar una calle+cuadra
        hay más de una localidad cacheada, se trata como miss (se geocodifica en vivo) en vez de
        devolver una localidad al azar — la localidad la sabe el geocoder sobre el resultado que
        confirma, no un dato que se le pueda arrancar al texto de la búsqueda.
- [x] Integrar la cache en `GeocodingProxyService` (lookup antes de llamar; guardado después) —
      2026-09-21: en `buscar()`, hit de cache = 0 llamadas salientes; miss = geocodifica como antes
      y guarda el mejor resultado.
- [x] Sumar **LocationIQ** al backend — 2026-09-21: `GeocodingProxyService.queryLocationIq`
      reutiliza el parseo de Nominatim (mismo formato de respuesta) + key legado movida desde el
      front (`app.maps.location-iq-key` / env `LOCATIONIQ_KEY`), rotable con `ApiKeyPoolService`
      igual que Geoapify. No quedó como "primario" separado: las tres fuentes (Nominatim, Geoapify,
      LocationIQ) se consultan igual que antes, solo que ahora son tres en vez de dos.
- [x] Exponer endpoint de geocoding del backend y **migrar el front** (`/pedir` + panel) a usarlo —
      2026-09-21: `/pedir` ya pasaba por acá desde 2026-09-17; se agregó el panel admin
      (`nuevo-pedido.component.ts`, 3 `address-picker`) sacando el flag `modoPublico` — ahora
      `AddressPickerComponent` usa un solo servicio (`GeocodingPublicoService`) siempre, no dos.
      También se migró `cadetes-libres.component.ts` (reverse geocoding de la ubicación del
      cadete) al mismo servicio.
- [x] Quitar las keys hardcodeadas del front — 2026-09-21: se borró
      `admin-front/.../geocoding.service.ts` entero (el que llamaba directo a Nominatim/Photon/
      Geoapify/LocationIQ desde el navegador con las keys de Geoapify y LocationIQ en el código);
      su interfaz `GeoAddress` se movió a `core/models/geo-address.model.ts`. Verificado con
      `ng build`: ninguna de las dos keys aparece en el bundle generado.
- [x] **Hito:** front y backend geocodifican por el mismo punto cacheado; sin keys expuestas.

**Fase 2 — Bot**
- [ ] Que el bot consuma el mismo servicio en proceso (herramienta `geocodificar`).
- [ ] Throttle saliente a Nominatim/Photon.
- [ ] Interfaz intercambiable de proveedor.
- [ ] **Hito:** el bot geocodifica con cache compartida.

**Fase 2b — Ecommerce como consumidor externo** (requiere Fase 1; ver §6.1)
- [ ] `GET /api/interno/geo/buscar` y `POST /api/interno/geo/aporte`.
- [ ] Nivel de auth `/api/interno/` con token compartido en Configuración (rotable), fuera del
      `RateLimitFilter` público.
- [ ] Unique constraint `(calle_canonica, cuadra, localidad)` + upsert idempotente.
- [ ] Contador de hit/miss alimentado por el flag `creado` del aporte.
- [ ] En el ecommerce: cliente HTTP con fallback a su propia cadena de proveedores si la cadetería
      no responde (degradar sin romper).
- [ ] Hard cap en la consola de Google Cloud de la cuenta del ecommerce (si usa Google).
- [ ] **Hito:** los dos sistemas pueblan y leen la misma cache; una sola implementación de la
      normalización.

**Fase 3 (opcional) — Precisión por GPS del cadete**
- [ ] Persistir el GPS del cadete en "recibir"/"finalizar" por pedido.
- [ ] Stop-points + validación contra la zona esperada → puntos confirmados.
- [ ] Agregar por repetición (cluster) → direcciones **precisas** gratis.
- [ ] (Opcional) traces agregados/anónimos → tiempos reales de viaje → mejor ETA y cotización.

---

## 12. Mejora futura: los cadetes como mapeadores (GPS)

**Idea:** 30 cadetes (expansión a 70) andan todo el día con GPS. Sus recorridos pueden alimentar
gratis una base de coordenadas y tiempos.

> **Versión simple implementada 2026-09-21 (`MapeoCallesCadetesService`):** sin stop-points ni
> validación de zona todavía — cada `mapeo_calles_cadetes_intervalo_seg` segundos (configurable
> desde el panel, 0 = apagado; default de producción 1200 seg = 20 min) reverse-geocodea la
> ÚLTIMA posición conocida de cada cadete **activo** con ping reciente (usa lo que ya guarda
> `CadeteService.actualizarUbicacion`, no pide nada nuevo a la app) y, si `GeocodingProxyService.reverse`
> devuelve una altura conocida, alimenta `cuadra_coords` — con un alias "identidad" (la propia
> calle canónica apuntando a sí misma), ya que acá no hay texto de cliente para guardar como
> variante real.
>
> **Por qué NO se reacciona a cada ping** (cada ~45 seg, `frecuencia_ubicacion_seg`) en vez de
> muestrear: con 20 cadetes ya son ~0.44 llamadas/seg sostenidas a Nominatim solo de esto (a 70
> cadetes, ~1.5/seg), pisando el límite de cortesía de Nominatim (~1 req/seg) compartido con las
> búsquedas reales de clientes — y encima solo cubriría a un cadete por vez, no a toda la flota.
> Muestrear cada tanto desacopla el volumen de la frecuencia de ping y cubre a todos a la vez.
> Hay un piso duro de 10 seg **no configurable** en el código (`INTERVALO_MINIMO_SEG`) para que
> nadie pueda tipear un número peligrosamente bajo desde el panel.
>
> **Para "sembrar" la cache antes de tener cadetes reales:** cuentas de prueba (propias/de
> conocidos) logueadas en la app de cadetes con ubicación activa cuentan igual que un cadete
> real — no hace falta que tengan un pedido asignado. Bajar el intervalo a algo como 30 seg
> mientras hay pocas cuentas de prueba conectadas es seguro (la cuenta que importa es *cadetes
> activos / intervalo*); hay que volver a subirlo antes de tener muchos cadetes reales
> conectados a la vez, para no acercarse al límite de Nominatim.
>
> Pendiente (fuera de esta versión simple): stop-points ligados a un pedido real (más confiables,
> ver Beneficio 1 abajo) y todo el Beneficio 2 (tiempos de viaje, red de calles).

**Beneficio 1 — precisión de direcciones (no requiere GPS todo el día):**
- **Stop-points:** cuando el cadete asignado a un pedido con destino "Perón 1202" **se queda quieto**
  cerca de esa cuadra, esa parada es un **punto confirmado** de "Perón 1202" (ligado a la dirección
  **porque viene del pedido**). Mejor que confiar en el botón "finalizado" (lo marcan antes/después).
- Validar la parada contra la zona esperada y agregar por repetición; filtrar outliers.

**Beneficio 2 — tiempos de viaje y red de calles (sí usa el trace continuo):**
- Tiempos reales por tramo, calles transitables, cortes → mejor **ETA** y mejor **cotización**
  (vs. la distancia en línea recta de hoy), y menos dependencia de las APIs de ruteo.

**Validación del "finalizado" por GPS (sin Google):**
- Al finalizar, comparar el **GPS del celular** con la **coordenada del destino** ya guardada. Si
  está dentro de ~X metros, permitir; si no, avisar "no estás en la zona". **Cero créditos** (es una
  resta de coordenadas). Con **umbral generoso** (100-150 m, el GPS falla) y **override con motivo**
  para no trabar entregas legítimas.

**Cuidados:**
- ❗ **No se puede** usar la cuenta de Google del cadete para validar/devolver datos: las llamadas
  API de **tu app** se facturan a **tu** key, y abrir la app de Maps del cadete es **una sola vía**
  (no devuelve nada). La validación se hace con el **GPS del celular**, no con Google.
- **Privacidad/laboral:** rastrear todo el día es sensible. Lo defendible es rastrear **solo durante
  pedidos activos**, ser transparente, y para tiempos de viaje usar traces **agregados/anónimos**.
- **Batería:** GPS continuo drena el celular del cadete → muestrear o solo en viaje.
- **Map-matching:** para el beneficio 2 hay que pegar los puntos a las calles (GraphHopper/Valhalla/OSM).
- **Rendimientos decrecientes:** al principio suma mucho; a los meses las direcciones frecuentes ya
  están confirmadas.

> **Ubicación en el plan:** Fase 3, **sobre** el bot funcionando. El resultado estratégico: una base
> propia de direcciones precisas + tiempos reales de Tucumán, gratis, que ningún proveedor vende.

---

## 13. Decisiones abiertas

1. **¿Arrancar con Google primero o con las gratuitas primero?** Al volumen actual (~70 viajes/día)
   Google entra en el free tier → $0, y con el **tope duro** no hay riesgo de facturación. Las dos
   vías son válidas (ver §10):
   - *Google primero (con tope duro):* mejor cobertura/precisión desde el día 1, pero pide cargar tarjeta.
   - *Gratuitas primero (Geoapify+LocationIQ):* sin tarjeta, sin riesgo de facturación, suficiente para
     cotizar por cuadra; Google se prende después detrás de la misma interfaz si hace falta.
   **Pendiente de confirmar con el dueño.**
2. **¿Autoalojar Pelias** para "costo cero" total sin depender de terceros? Solo si molestan los
   cupos o se quiere control total; suma mantenimiento. A ~70 viajes/día, **no hace falta**.
3. **Tamaño de la "cuadra"** para la clave (centena fija vs. cuadra real de la ciudad).
4. **Política de negative cache** (cuánto tiempo recordar un "no encontrado").
5. **Fase 3 (GPS):** confirmación de que los pings de cadete **se persisten** por pedido (hoy el
   mapa en vivo quizá solo los muestra) y acuerdo de privacidad con los cadetes.
6. **Umbral del presupuesto diario blando** (N) y comportamiento exacto de la página pública cuando
   se degrada (ver §10.1).
7. **Cuándo publicar la página `/pedir`:** por madurez del producto (no por cupo). Sugerido: después
   de Fase 1 (cache + punto único) probada y con rate limit activo.
8. **Con qué key geocodifica el ecommerce:** ¿su propia cuenta de Google (tarjeta de la hermana,
   free tier la cubre entera al volumen actual) o la cadena gratuita propia? Ambas aportan a la
   misma cache; ver §6.2.
9. **Token del nivel `/api/interno/`:** dónde se guarda, cómo se rota y qué pasa si el ecommerce lo
   manda mal (¿401 seco o degradación?).
10. **Si algún día se separan en servidores distintos,** el endpoint interno sigue igual pero hay que
    decidir exposición (¿VPN/tailnet, o HTTPS público con token?). Hoy comparten host → red interna
    de Docker.
