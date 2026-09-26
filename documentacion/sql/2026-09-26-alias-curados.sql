-- Unifica nombres de la misma calle en la cache de direcciones (pendientes.md 3k, 2026-09-26).
-- Google (teléfono) y OpenStreetMap (Nominatim) nombran distinto la misma calle y la cache las
-- guardaba separadas. Lista curada a mano (con otra IA, Kimi) y verificada por coordenadas contra
-- la base: 23 variantes -> 20 canónicas, nombre CORTO (es como busca el cliente).
--
-- NO van (verificar en el mapa antes): combate de san lorenzo / san lorenzo, eduardo bulnes /
-- avenida bulnes, las piedras / combate de las piedras.
-- Trampas, NO fusionar: camino del peru / peru, bernabe araoz / (araoz de) lamadrid,
-- general paz / marcos paz, juan luis nougues / pasaje ambrosio nougues.
--
-- Correr con el BACKEND PARADO y con respaldo en archivo hecho antes (backups\LEEME.txt):
--   mysql -uroot -proot --default-character-set=utf8mb4 cadeteria < 2026-09-26-alias-curados.sql
-- Deja copias en la misma base: bak_cuadra_coords_20260926 y bak_direccion_alias_20260926.

CREATE TABLE bak_cuadra_coords_20260926 AS SELECT * FROM cuadra_coords;
CREATE TABLE bak_direccion_alias_20260926 AS SELECT * FROM direccion_alias;

-- Columna nueva del backend (CuadraCoords.muestras); Hibernate la crearía sola, pero el merge la usa.
ALTER TABLE cuadra_coords ADD COLUMN muestras INT NULL;

CREATE TEMPORARY TABLE mapeo_alias (variante VARCHAR(255) PRIMARY KEY, canonica VARCHAR(255) NOT NULL);
INSERT INTO mapeo_alias (variante, canonica) VALUES
    ('general lamadrid', 'lamadrid'),
    ('araoz de lamadrid gregorio', 'lamadrid'),
    ('avenida manuel belgrano', 'avenida belgrano'),
    ('avenida presidente nestor kirchner', 'avenida nestor kirchner'),
    ('avenida general roca', 'avenida nestor kirchner'),
    ('avenida roca julio argentino', 'avenida nestor kirchner'),
    ('provincia de mendoza', 'mendoza'),
    ('juan gregorio de las heras', 'las heras'),
    ('batalla de ayacucho', 'ayacucho'),
    ('avenida ernesto padilla', 'ernesto padilla'),
    ('viamonte juan jose', 'viamonte'),
    ('a lincoln', 'abraham lincoln'),
    ('avenida leandro n alem', 'avenida alem'),
    ('jose i thames', 'jose ignacio thames'),
    ('manuel m alberti', 'manuel alberti'),
    ('calle federico helguera', 'federico helguera'),
    ('avenida estado de israel', 'estado de israel'),
    ('republica de ecuador', 'ecuador'),
    ('republica de paraguay', 'paraguay'),
    ('9 de julio de 1816', '9 de julio'),
    ('general jose de san martin', 'san martin'),
    ('juan bautista alberdi', 'alberdi'),
    ('pasaje bolougne sur mer', 'boulogne sur mer');

START TRANSACTION;

-- 1. Alias que apuntaban a una variante (incluye los "identidad" variante -> variante) -> a la canónica.
UPDATE direccion_alias a JOIN mapeo_alias m ON a.calle_canonica = m.variante
SET a.calle_canonica = m.canonica;

-- 2. Cada variante y cada canónica con su alias (para que el buscador encuentre las dos formas).
INSERT INTO direccion_alias (id, variante_norm, localidad, calle_canonica, creado_en)
SELECT UUID(), m.variante, 'San Miguel de Tucumán', m.canonica, NOW(6) FROM mapeo_alias m
WHERE NOT EXISTS (SELECT 1 FROM direccion_alias a WHERE a.variante_norm = m.variante);

INSERT INTO direccion_alias (id, variante_norm, localidad, calle_canonica, creado_en)
SELECT UUID(), c.canonica, 'San Miguel de Tucumán', c.canonica, NOW(6)
FROM (SELECT DISTINCT canonica FROM mapeo_alias) c
WHERE NOT EXISTS (SELECT 1 FROM direccion_alias a WHERE a.variante_norm = c.canonica);

-- 3. Plan de re-key: por cada cuadra (canónica + localidad + cuadra) queda UNA fila, elegida con la
--    misma regla de confianza del código (DireccionCacheService.confianza): manual > cadete_gps /
--    google_link > buscadores y teléfono > google; a igual confianza, la que no vence (el teléfono
--    y Google vencen a los 30 días); después, la más vieja. Confirmaciones se suman; muestras = filas.
CREATE TEMPORARY TABLE plan_rekey AS
SELECT c.id,
       COALESCE(m.canonica, c.calle_canonica) AS canon,
       ROW_NUMBER() OVER w AS rn,
       SUM(c.confirmaciones) OVER (PARTITION BY COALESCE(m.canonica, c.calle_canonica), c.localidad, c.cuadra) AS conf_total,
       COUNT(*) OVER (PARTITION BY COALESCE(m.canonica, c.calle_canonica), c.localidad, c.cuadra) AS filas
FROM cuadra_coords c
LEFT JOIN mapeo_alias m ON m.variante = c.calle_canonica
WINDOW w AS (
    PARTITION BY COALESCE(m.canonica, c.calle_canonica), c.localidad, c.cuadra
    ORDER BY CASE c.proveedor WHEN 'manual' THEN 4 WHEN 'cadete_gps' THEN 3 WHEN 'google_link' THEN 3
                              WHEN 'google' THEN 0 ELSE 1 END DESC,
             CASE WHEN c.proveedor IN ('android_geocoder', 'google') THEN 1 ELSE 0 END,
             c.creada_en);

-- Primero se borran las que sobran (si no, renombrar choca con la clave única calle+localidad+cuadra).
DELETE c FROM cuadra_coords c JOIN plan_rekey p ON p.id = c.id WHERE p.rn > 1;

UPDATE cuadra_coords c JOIN plan_rekey p ON p.id = c.id
SET c.calle_canonica = p.canon,
    c.confirmaciones = p.conf_total,
    c.muestras = p.filas
WHERE p.rn = 1 AND (c.calle_canonica <> p.canon OR p.filas > 1);

COMMIT;

-- Control: no tiene que quedar ninguna fila con nombre de variante.
SELECT COUNT(*) AS filas_con_variante_que_quedaron
FROM cuadra_coords c JOIN mapeo_alias m ON m.variante = c.calle_canonica;
SELECT (SELECT COUNT(*) FROM bak_cuadra_coords_20260926) AS antes, (SELECT COUNT(*) FROM cuadra_coords) AS despues;
