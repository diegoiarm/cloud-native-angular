# Documento 03, seccion 9: comprobar CURRENT-OFFSET, LOG-END-OFFSET y LAG.
# Repita el consumo con otro grupo (por ejemplo grupo-auditoria-s12) para
# demostrar lectura independiente.
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/06_verificar_grupo.ps1
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/06_verificar_grupo.ps1 -Grupo grupo-monitor-s13

param(
    [string]$Grupo = "grupo-monitor-s12"
)

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$compose = Join-Path $repo "kafka-lab/compose.yaml"

Write-Host "Estado del consumer group $Grupo..."
docker compose -f $compose exec -T kafka `
    /opt/kafka/bin/kafka-consumer-groups.sh `
    --bootstrap-server kafka:9093 `
    --describe --group $Grupo
