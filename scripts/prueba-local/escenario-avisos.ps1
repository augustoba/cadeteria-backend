<#
.SYNOPSIS
  Arma el escenario de "Avisos de la calle" contra un backend levantado con levantar.ps1 (2026-09-29).

.DESCRIPTION
  Pone a los 3 cadetes demo en Libre y cerca de la plaza Independencia, y Juan (30111222) avisa un
  control. Con -Votar además corre "¿Sigue ahí?" completo y muestra cada resultado:
    Marcos contesta SIGUE           -> no cambia: un aviso nuevo dura 60 min y SIGUE extiende a 30 desde ahora (nunca acorta)
    Juan contesta su propio aviso   -> 400
    Marcos cambia a YA_NO_ESTA y Ana YA_NO_ESTA -> el aviso se baja
    Marcos contesta otra vez        -> 409 (ya no está activo)
  Ojo: cada cadete puede avisar 5 veces por hora; correrlo más seguido da 429.

.EXAMPLE
  .\scripts\prueba-local\escenario-avisos.ps1
.EXAMPLE
  .\scripts\prueba-local\escenario-avisos.ps1 -Votar -Url http://localhost:8081
#>
param(
    [string]$Url = "http://localhost:8080",
    [switch]$Votar
)

$ErrorActionPreference = "Stop"

function Llamar([string]$Metodo, [string]$Ruta, [string]$Token, $Cuerpo) {
    $headers = @{}
    if ($Token) { $headers["Authorization"] = "Bearer $Token" }
    $json = $null
    if ($null -ne $Cuerpo) { $json = $Cuerpo | ConvertTo-Json -Compress }
    $r = Invoke-WebRequest -UseBasicParsing -Method $Metodo -Uri "$Url$Ruta" -Headers $headers -ContentType "application/json; charset=utf-8" -Body $json
    # PowerShell 5.1 lee la respuesta como Latin-1 ("AsunciÃ³n"): se decodifica a mano en UTF-8.
    $texto = [Text.Encoding]::UTF8.GetString($r.RawContentStream.ToArray())
    if ($texto) { $texto | ConvertFrom-Json }
}

# Devuelve el código HTTP de una llamada que se espera que falle (0 si no falló).
function CodigoDe([scriptblock]$Llamada) {
    try { & $Llamada | Out-Null; return 0 }
    catch {
        if ($_.Exception.Response) { return [int]$_.Exception.Response.StatusCode }
        throw
    }
}

function Login([string]$Dni) {
    (Llamar POST "/api/auth/login/cadete" $null @{ username = $Dni; password = "cadete123" }).token
}

# Plaza Independencia (San Miguel de Tucumán); cada cadete a unos 100-200 m.
$cadetes = @(
    @{ dni = "30111222"; nombre = "Juan";   lat = -26.8305; lng = -65.2038 },
    @{ dni = "30222333"; nombre = "Marcos"; lat = -26.8315; lng = -65.2050 },
    @{ dni = "30333444"; nombre = "Ana";    lat = -26.8295; lng = -65.2025 }
)

foreach ($c in $cadetes) {
    try { $c.token = Login $c.dni }
    catch { throw "No pude entrar con $($c.dni). ¿Está levantado el backend en $Url? ¿La base es nueva (levantar.ps1 -Limpia)? $_" }
    try { Llamar PATCH "/api/cadetes/me/estado" $c.token @{ estadoId = "LIBRE" } | Out-Null }
    catch { Write-Host "  $($c.nombre): no pasó a Libre ($($_.Exception.Message)); sigo igual." -ForegroundColor Yellow }
    Llamar PATCH "/api/cadetes/me/ubicacion" $c.token @{ lat = $c.lat; lng = $c.lng; precision = 10 } | Out-Null
    Write-Host "$($c.nombre) ($($c.dni)): Libre en $($c.lat), $($c.lng)"
}
$juan = $cadetes[0]; $marcos = $cadetes[1]; $ana = $cadetes[2]

$aviso = Llamar POST "/api/cadetes/me/avisos-calle" $juan.token @{ tipo = "CONTROL"; lat = $juan.lat; lng = $juan.lng; precision = 10 }
Write-Host ""
Write-Host "Juan avisó: $($aviso.tipoTexto) en $($aviso.calle) (id $($aviso.id), vence $($aviso.venceEn))" -ForegroundColor Green
Write-Host "Tiene que verse en el Mapa del panel y en el 'Mapa de la calle' de la app."

if (-not $Votar) {
    Write-Host ""
    Write-Host "Para probar '¿Sigue ahí?' por API volvé a correrlo con -Votar."
    Write-Host "Para la APK: entrá con 30222333 o 30333444 y mandá la posición del emulador cerca:"
    Write-Host "  adb emu geo fix $($juan.lng) $($juan.lat)"
    return
}

Write-Host ""
$r = Llamar POST "/api/cadetes/me/avisos-calle/$($aviso.id)/voto" $marcos.token @{ voto = "SIGUE" }
Write-Host "Marcos: SIGUE        -> vence $($r.venceEn) (antes $($aviso.venceEn); igual: 60 min > 30 de SIGUE)"

$codigo = CodigoDe { Llamar POST "/api/cadetes/me/avisos-calle/$($aviso.id)/voto" $juan.token @{ voto = "SIGUE" } }
Write-Host "Juan (su aviso)      -> $codigo (tiene que ser 400)"

$r = Llamar POST "/api/cadetes/me/avisos-calle/$($aviso.id)/voto" $marcos.token @{ voto = "YA_NO_ESTA" }
Write-Host "Marcos: YA_NO_ESTA   -> vence $($r.venceEn) (sigue activo: falta otro cadete)"

$r = Llamar POST "/api/cadetes/me/avisos-calle/$($aviso.id)/voto" $ana.token @{ voto = "YA_NO_ESTA" }
Write-Host "Ana: YA_NO_ESTA      -> vence $($r.venceEn) (bajado)"

$codigo = CodigoDe { Llamar POST "/api/cadetes/me/avisos-calle/$($aviso.id)/voto" $marcos.token @{ voto = "SIGUE" } }
Write-Host "Marcos otra vez      -> $codigo (tiene que ser 409)"

$activos = @(Llamar GET "/api/cadetes/me/avisos-calle" $marcos.token $null)
$sigue = @($activos | Where-Object { $_.id -eq $aviso.id }).Count -gt 0
Write-Host "¿Sigue en la lista de activos? $sigue (tiene que ser False)"
