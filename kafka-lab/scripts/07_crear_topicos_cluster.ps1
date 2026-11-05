# EP6 (documento 05 S12/S13): crear los dos topics con 3 particiones y RF3
# en el cluster de tres brokers.
#
# Uso desde la raiz del repositorio:
#   docker compose -f kafka-lab/compose.cluster.yaml up -d
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/07_crear_topicos_cluster.ps1

param(
    [int]$Particiones = 3,
    [int]$Replicas = 3,
    [long]$RetencionMs = 86400000
)

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$compose = Join-Path $repo "kafka-lab/compose.cluster.yaml"
$topicos = @("sensores-temperatura", "sensores-alertas")
$dlts = @("sensores-temperatura.DLT", "sensores-alertas.DLT")

foreach ($topico in ($topicos + $dlts)) {
    $retencion = if ($dlts -contains $topico) { 604800000 } else { $RetencionMs }
    Write-Host "Creando $topico (particiones=$Particiones, replicas=$Replicas, retention.ms=$retencion)..."
    docker compose -f $compose exec -T kafka1 /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server kafka1:19092 --create --if-not-exists `
        --topic $topico --partitions $Particiones --replication-factor $Replicas `
        --config retention.ms=$retencion
    if ($LASTEXITCODE -ne 0) { Write-Error "No se pudo crear el topic $topico." }
}

Write-Host ""
Write-Host "Topics del cluster:"
docker compose -f $compose exec -T kafka1 /opt/kafka/bin/kafka-topics.sh `
    --bootstrap-server kafka1:19092 --list

foreach ($topico in ($topicos + $dlts)) {
    docker compose -f $compose exec -T kafka1 /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server kafka1:19092 --describe --topic $topico
}
