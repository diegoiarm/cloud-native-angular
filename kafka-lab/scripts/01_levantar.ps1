# Documento 03, seccion 4: levantar Kafka local + Kafbat UI.
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/01_levantar.ps1

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$compose = Join-Path $repo "kafka-lab/compose.yaml"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker no esta disponible. Abra Docker Desktop antes de continuar."
}

Write-Host "Levantando Kafka KRaft y Kafbat UI..."
docker compose -f $compose up -d
if ($LASTEXITCODE -ne 0) { Write-Error "No se pudo levantar kafka-lab/compose.yaml." }

docker compose -f $compose ps

function Esperar-Healthy($contenedor) {
    for ($i = 0; $i -lt 36; $i++) {
        $estado = docker inspect -f '{{.State.Health.Status}}' $contenedor 2>$null
        if ($estado -eq "healthy") { return }
        Start-Sleep -Seconds 5
    }
    Write-Error "$contenedor no quedo healthy en 3 minutos (estado: $estado)."
}

Esperar-Healthy "pedidos360-kafka"

Write-Host ""
Write-Host "Kafka listo:"
Write-Host "  Bootstrap desde Windows : localhost:9092"
Write-Host "  Bootstrap entre Docker  : kafka:9093"
Write-Host "  Kafbat UI               : http://localhost:8090"
Write-Host ""
Write-Host "Siguiente paso: kafka-lab/scripts/02_crear_topic.ps1"
