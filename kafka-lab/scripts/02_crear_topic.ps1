# Documentos 03 y 05: crear los topics del laboratorio y de EP5.
#
#   sensores-temperatura  -> topic principal de lecturas (3 particiones)
#   sensores-alertas      -> segundo topic de negocio exigido por EP5
#   *.DLT                 -> dead letter topics para retry/DLT
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/02_crear_topic.ps1
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/02_crear_topic.ps1 -Particiones 3 -Replicas 1

param(
    [int]$Particiones = 3,
    [int]$Replicas = 1,
    [long]$RetencionMs = 86400000
)

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$compose = Join-Path $repo "kafka-lab/compose.yaml"
$topicos = @("sensores-temperatura", "sensores-alertas")
$dlts = @("sensores-temperatura.DLT", "sensores-alertas.DLT")

foreach ($topico in $topicos) {
    Write-Host "Creando $topico (particiones=$Particiones, replicas=$Replicas, retention.ms=$RetencionMs)..."
    docker compose -f $compose exec -T kafka /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server kafka:9093 --create --if-not-exists `
        --topic $topico --partitions $Particiones --replication-factor $Replicas `
        --config retention.ms=$RetencionMs
    if ($LASTEXITCODE -ne 0) { Write-Error "No se pudo crear el topic $topico." }
}

foreach ($dlt in $dlts) {
    Write-Host "Creando $dlt (retencion de 7 dias para diagnostico)..."
    docker compose -f $compose exec -T kafka /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server kafka:9093 --create --if-not-exists `
        --topic $dlt --partitions $Particiones --replication-factor $Replicas `
        --config retention.ms=604800000
    if ($LASTEXITCODE -ne 0) { Write-Error "No se pudo crear el topic $dlt." }
}

Write-Host ""
Write-Host "Topics disponibles:"
docker compose -f $compose exec -T kafka /opt/kafka/bin/kafka-topics.sh `
    --bootstrap-server kafka:9093 --list

Write-Host ""
Write-Host "Detalle esperado: Partitions=3, ReplicationFactor=$Replicas."
foreach ($topico in ($topicos + $dlts)) {
    docker compose -f $compose exec -T kafka /opt/kafka/bin/kafka-topics.sh `
        --bootstrap-server kafka:9093 --describe --topic $topico
}
