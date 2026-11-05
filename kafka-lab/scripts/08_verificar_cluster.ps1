# EP6: verificar la topologia de 3 brokers y RF3.
# Muestra brokers, controller, topics, factor de replicacion y particiones
# sub-replicadas. No crea ni borra nada.
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/08_verificar_cluster.ps1

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$compose = Join-Path $repo "kafka-lab/compose.cluster.yaml"

docker compose -f $compose ps

Write-Host "--- Brokers y controller ---"
docker compose -f $compose exec -T kafka1 /opt/kafka/bin/kafka-broker-api-versions.sh `
    --bootstrap-server kafka1:19092

Write-Host ""
Write-Host "--- Topics: particiones, lider y replicas ---"
docker compose -f $compose exec -T kafka1 /opt/kafka/bin/kafka-topics.sh `
    --bootstrap-server kafka1:19092 --describe

Write-Host ""
Write-Host "--- Particiones sub-replicadas (debe estar vacio en operacion normal) ---"
docker compose -f $compose exec -T kafka1 /opt/kafka/bin/kafka-topics.sh `
    --bootstrap-server kafka1:19092 --describe --under-replicated-partitions

Write-Host ""
Write-Host "Kafbat UI del cluster: http://localhost:8091"
