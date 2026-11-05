# EP5: demostrar reintentos y DLT de forma controlada.
#
# El monitor trata sensorId="sensor-fallo" como error de negocio: el
# DefaultErrorHandler hace 1 intento + 2 reintentos (1 s de espera) y luego
# publica el registro en sensores-temperatura.DLT.
#
# Requiere ms-productor-sensores y ms-monitor-sensores en ejecucion.
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/09_probar_dlt.ps1

param(
    [string]$Url = "http://localhost:8081/api/lecturas"
)

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$compose = Join-Path $repo "kafka-lab/compose.yaml"

$body = @{
    sensorId    = "sensor-fallo"
    temperatura = 99.9
    unidad      = "C"
    secuencia   = 999
} | ConvertTo-Json

Write-Host "Publicando lectura con sensorId=sensor-fallo..."
Invoke-RestMethod -Method Post -Uri $Url -ContentType "application/json" -Body $body | ConvertTo-Json -Compress

Write-Host "Esperando los 2 reintentos (1 s c/u) y la publicacion en la DLT..."
Start-Sleep -Seconds 6

Write-Host ""
Write-Host "Contenido de sensores-temperatura.DLT:"
docker compose -f $compose exec -T kafka `
    /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server kafka:9093 `
    --topic sensores-temperatura.DLT `
    --from-beginning --max-messages 1 --timeout-ms 10000 `
    --property 'print.key=true' `
    --property 'print.partition=true' `
    --property 'print.offset=true'
