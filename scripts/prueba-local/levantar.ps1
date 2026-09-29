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
    [switch]$SinCompilar,
    # Cloudinary de PRUEBA (cuenta de otro proyecto, ya anotada en documentacion/despliegue.md):
    # se carga sola si la base no tiene una. Producción usa una cuenta propia de Cadem.
    [string]$CloudinaryCloud = "jitutkbc",
    [string]$CloudinaryPreset = "estilospequenos",
    # JSON de la cuenta de servicio de Firebase (push a la app). NO va en el repo: se copia a mano.
    [string]$FirebaseJson = $(if ($env:FCM_CREDENTIALS_PATH) { $env:FCM_CREDENTIALS_PATH } else { Join-Path $HOME "secretos\firebase-cadeteria.json" })
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
$conPush = Test-Path $FirebaseJson
if ($conPush) { $env:FCM_CREDENTIALS_PATH = $FirebaseJson }

$java = Start-Process java -ArgumentList "-jar", "`"$($jar.FullName)`"" -NoNewWindow -PassThru
try {
    # --- 4. Cloudinary de prueba (sin esto no se pueden finalizar pedidos: la foto es obligatoria) ---
    $url = "http://localhost:$Puerto"
    $token = $null
    for ($i = 0; $i -lt 90 -and -not $java.HasExited; $i++) {
        Start-Sleep -Seconds 2
        try {
            $token = (Invoke-RestMethod -Method POST -Uri "$url/api/auth/login/admin" -ContentType "application/json" `
                -Body '{"username":"admin","password":"cambiar123"}').token
            break
        } catch { }
    }
    if ($java.HasExited) { throw "El backend se cerró al arrancar: mirá el error de arriba." }
    $cloudinary = "no se pudo cargar (el admin no es admin/cambiar123): cargalo en Configuración"
    if ($token) {
        $h = @{ Authorization = "Bearer $token" }
        $config = Invoke-RestMethod -Uri "$url/api/admin/configuracion" -Headers $h
        if ([string]::IsNullOrWhiteSpace($config.valores.cloudinary_cloud_name)) {
            foreach ($par in @(@("cloudinary_cloud_name", $CloudinaryCloud), @("cloudinary_upload_preset", $CloudinaryPreset))) {
                $cuerpo = @{ clave = $par[0]; valor = $par[1] } | ConvertTo-Json -Compress
                Invoke-RestMethod -Method PUT -Uri "$url/api/admin/configuracion" -Headers $h -ContentType "application/json" -Body $cuerpo | Out-Null
            }
            $cloudinary = "cargado ($CloudinaryCloud, de prueba)"
        } else {
            $cloudinary = "ya tenía uno ($($config.valores.cloudinary_cloud_name)), no se tocó"
        }
    }

    $ips = @(Get-NetIPAddress -AddressFamily IPv4 -ErrorAction SilentlyContinue |
        Where-Object { $_.IPAddress -notlike "127.*" -and $_.IPAddress -notlike "169.254.*" -and $_.PrefixOrigin -ne "WellKnown" } |
        ForEach-Object { $_.IPAddress })

    Write-Host ""
    Write-Host "Backend en http://localhost:$Puerto  (base '$Base')" -ForegroundColor Green
    Write-Host "  Panel:    admin / cambiar123"
    Write-Host "  Cadetes:  30111222 (Juan Pérez, moto)  30222333 (Marcos Gómez, bici)  30333444 (Ana Díaz, moto)"
    Write-Host "            contraseña de los tres: cadete123"
    Write-Host "  Emulador: apunta solo a http://10.0.2.2:$Puerto (si el puerto es 8080)"
    foreach ($ip in $ips) { Write-Host "  Celular en la misma WiFi: 'Cambiar servidor' -> http://${ip}:$Puerto" }
    Write-Host "  Cloudinary (fotos): $cloudinary"
    if ($conPush) { Write-Host "  Push (Firebase): con $FirebaseJson" }
    else { Write-Host "  Push (Firebase): APAGADO, no está $FirebaseJson (ver LEEME)" -ForegroundColor Yellow }
    Write-Host "  Escenario de avisos de la calle: .\scripts\prueba-local\escenario-avisos.ps1"
    Write-Host "Ctrl+C para parar."
    Write-Host ""
    Wait-Process -Id $java.Id
} finally {
    if (-not $java.HasExited) { Stop-Process -Id $java.Id -Force }
}
