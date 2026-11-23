# EP6: verificar la topologia de 3 ZooKeeper + 3 brokers y RF3.
# Muestra el rol de cada nodo ZooKeeper (leader/follower), los brokers
# registrados, el controller, los topics y las particiones sub-replicadas.
# No crea ni borra nada.
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/08_verificar_cluster.ps1

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$compose = Join-Path $repo "kafka-lab/compose.cluster.yaml"

docker compose -f $compose ps

Write-Host ""
Write-Host "--- Ensemble ZooKeeper (debe haber 1 leader y 2 followers) ---"
foreach ($zk in @("zookeeper1", "zookeeper2", "zookeeper3")) {
    $modo = docker compose -f $compose exec -T $zk bash -c "exec 3<>/dev/tcp/127.0.0.1/2181; echo srvr >&3; cat <&3" |
        Select-String "Mode:"
    Write-Host "$zk -> $modo"
}

Write-Host ""
Write-Host "--- Brokers registrados en ZooKeeper (/brokers/ids, debe ser [1, 2, 3]) ---"
docker compose -f $compose exec -T zookeeper1 zookeeper-shell localhost:2181 ls /brokers/ids 2>$null |
    Select-Object -Last 1

Write-Host ""
Write-Host "--- Controller elegido (/controller) ---"
docker compose -f $compose exec -T zookeeper1 zookeeper-shell localhost:2181 get /controller 2>$null |
    Select-String "brokerid"

Write-Host ""
Write-Host "--- Topics: particiones, lider y replicas ---"
docker compose -f $compose exec -T kafka1 kafka-topics `
    --bootstrap-server kafka1:19092 --describe --exclude-internal

Write-Host ""
Write-Host "--- Particiones sub-replicadas (debe estar vacio en operacion normal) ---"
docker compose -f $compose exec -T kafka1 kafka-topics `
    --bootstrap-server kafka1:19092 --describe --under-replicated-partitions

Write-Host ""
Write-Host "Kafbat UI del cluster: http://localhost:8091"
