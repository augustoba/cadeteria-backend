<#
.SYNOPSIS
  Levanta el backend con una base de prueba lista para usar, en cualquier PC (2026-09-29).

.DESCRIPTION
  Crea la base (si no existe), compila y arranca el backend con la carga demo prendida. En una
  base vacía el backend siembra solo: admin, 3 cadetes, zona, pedidos de ejemplo, chat, etc.
  (ver LEEME.md). WhatsApp queda simulado. Ctrl+C lo para.

.EXAMPLE
  .\scripts\prueba-local\levantar.ps1 -Limpia
  Borra la base de prueba y la arma de cero (lo recomendado la primera vez).

.EXAMPLE
  .\scripts\prueba-local\levantar.ps1 -Puerto 8081 -SinCompilar
#>
param(
    [string]$Base = "cadeteria_prueba",
    [string]$MysqlUsuario = "root",
    [string]$MysqlClave = "root",
    [string]$MysqlHost = "localhost",
    [int]$MysqlPuerto = 3306,
    [int]$Puerto = 8080,
    # Borra la base y la arma de cero (la carga demo solo siembra en una base vacía).
    [switch]$Limpia,
    # Usa el .jar que ya está en target\ (más rápido si no cambió el código).
    [switch]$SinCompilar
)

$ErrorActionPreference = "Stop"
$repo = (Resolve-Path (Join-Path $PSScriptRoot "..\..")).Path

function Buscar-Mysql {
    $cmd = Get-Command mysql -ErrorAction SilentlyContinue
    if ($cmd) { return $cmd.Source }
    $candidatos = @(Get-ChildItem "C:\Program Files\MySQL\*\bin\mysql.exe" -ErrorAction SilentlyContinue)
    if ($candidatos.Count -gt 0) { return $candidatos[0].FullName }
    throw "No encontré mysql.exe. Instalá MySQL 8 o agregá su carpeta bin al PATH."
}

# --- 1. Base de datos ---
if ($Limpia -and $Base -notlike "*prueba*") {
    throw "Por seguridad -Limpia solo borra bases con 'prueba' en el nombre (pediste '$Base')."
}
$mysql = Buscar-Mysql
$env:MYSQL_PWD = $MysqlClave   # así mysql no avisa por la clave en la línea de comandos
$sql = ""
if ($Limpia) { $sql += "DROP DATABASE IF EXISTS ``$Base``; " }
$sql += "CREATE DATABASE IF NOT EXISTS ``$Base`` CHARACTER SET utf8mb4;"
& $mysql "-h$MysqlHost" "-P$MysqlPuerto" "-u$MysqlUsuario" -e $sql
if ($LASTEXITCODE -ne 0) { throw "No se pudo crear la base '$Base'. ¿Está prendido MySQL? ¿Usuario y clave bien?" }
Remove-Item Env:MYSQL_PWD
if ($Limpia) { Write-Host "Base '$Base' borrada y creada de cero." -ForegroundColor Yellow }
else { Write-Host "Base '$Base' lista (si ya tenía datos, se usan esos)." }

# --- 2. Compilar ---
if (-not $SinCompilar) {
    Write-Host "Compilando el backend (sin tests)..."
    Push-Location $repo
    try {
        & (Join-Path $repo "mvnw.cmd") -q -DskipTests package
        if ($LASTEXITCODE -ne 0) { throw "Falló la compilación. El backend necesita JDK 21 (JAVA_HOME)." }
    } finally { Pop-Location }
}
$jar = Get-ChildItem (Join-Path $repo "target\*.jar") | Where-Object { $_.Name -notlike "*plain*" } | Select-Object -First 1
if (-not $jar) { throw "No hay .jar en target\. Corré el script sin -SinCompilar." }

# --- 3. Arrancar ---
$env:SPRING_DATASOURCE_URL = "jdbc:mysql://${MysqlHost}:${MysqlPuerto}/${Base}?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=UTF-8"
$env:DB_USER = $MysqlUsuario
$env:DB_PASSWORD = $MysqlClave
$env:SPRING_DATASOURCE_USERNAME = $MysqlUsuario
$env:SPRING_DATASOURCE_PASSWORD = $MysqlClave
$env:SERVER_PORT = "$Puerto"
$env:SEED_ENABLED = "true"
$env:DEMO_ENABLED = "true"
$env:WHATSAPP_MODO_SIMULADO = "true"
$env:CORS_ORIGINS = "http://localhost:4200,http://localhost:4201"

Write-Host ""
Write-Host "Backend en http://localhost:$Puerto  (base '$Base')" -ForegroundColor Green
Write-Host "  Panel:    admin / cambiar123"
Write-Host "  Cadetes:  30111222 (Juan Pérez, moto)  30222333 (Marcos Gómez, bici)  30333444 (Ana Díaz, moto)"
Write-Host "            contraseña de los tres: cadete123"
Write-Host "  App en el emulador: apunta sola a http://10.0.2.2:8080"
Write-Host "  Escenario de avisos de la calle: .\scripts\prueba-local\escenario-avisos.ps1"
Write-Host "Ctrl+C para parar."
Write-Host ""
& java -jar $jar.FullName
