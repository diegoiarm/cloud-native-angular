# Documento 03, seccion 9: comprobar CURRENT-OFFSET, LOG-END-OFFSET y LAG.
# Repita el consumo con otro grupo (por ejemplo grupo-auditoria-s12) para
# demostrar lectura independiente.
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/06_verificar_grupo.ps1
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/06_verificar_grupo.ps1 -Grupo grupo-monitor-s13
#   (agregar -Cluster si se usa kafka-lab/compose.cluster.yaml)

param(
    [string]$Grupo = "grupo-monitor-s12",
    [switch]$Cluster
)

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$composeFile = if ($Cluster) { "kafka-lab/compose.cluster.yaml" } else { "kafka-lab/compose.yaml" }
$compose = Join-Path $repo $composeFile
$servicio = if ($Cluster) { "kafka1" } else { "kafka" }
$bootstrap = if ($Cluster) { "kafka1:19092" } else { "kafka:9093" }

Write-Host "Estado del consumer group $Grupo..."
docker compose -f $compose exec -T $servicio `
    /opt/kafka/bin/kafka-consumer-groups.sh `
    --bootstrap-server $bootstrap `
    --describe --group $Grupo
