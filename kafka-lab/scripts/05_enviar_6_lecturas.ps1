# Documento 01 S13, seccion 4, paso 7: enviar seis lecturas por REST al productor.
# Requiere ms-productor-sensores en http://localhost:8085 (o el puerto indicado).
# Las lecturas con temperatura >= UMBRAL_ALERTA_C (30.0 por defecto) tambien
# generan un evento en el segundo topic, sensores-alertas.
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/05_enviar_6_lecturas.ps1

param(
    [string]$Url = "http://localhost:8085/api/lecturas"
)

$ErrorActionPreference = "Stop"

$lecturas = @(
    @{ sensorId = "sensor-A"; temperatura = 21.5; unidad = "C"; secuencia = 1 },
    @{ sensorId = "sensor-B"; temperatura = 22.1; unidad = "C"; secuencia = 2 },
    @{ sensorId = "sensor-A"; temperatura = 23.4; unidad = "C"; secuencia = 3 },
    @{ sensorId = "sensor-B"; temperatura = 31.2; unidad = "C"; secuencia = 4 },
    @{ sensorId = "sensor-A"; temperatura = 24.0; unidad = "C"; secuencia = 5 },
    @{ sensorId = "sensor-B"; temperatura = 32.8; unidad = "C"; secuencia = 6 }
)

foreach ($lectura in $lecturas) {
    $body = $lectura | ConvertTo-Json
    Write-Host "POST $Url -> $body"
    $respuesta = Invoke-RestMethod -Method Post -Uri $Url -ContentType "application/json" -Body $body
    $respuesta | ConvertTo-Json -Compress
}

Write-Host ""
Write-Host "Las respuestas 202 con particion y offset confirman la publicacion en el broker."
Write-Host "Consulte el monitor en http://localhost:8086/api/monitor/lecturas"
