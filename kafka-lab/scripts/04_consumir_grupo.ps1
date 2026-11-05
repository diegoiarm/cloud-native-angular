# Documento 03, seccion 8: consumir y examinar clave, particion y offset.
# El grupo es nuevo para que --from-beginning entregue las seis lecturas.
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/04_consumir_grupo.ps1

param(
    [string]$Grupo = "grupo-monitor-s12",
    [string]$Topico = "sensores-temperatura",
    [int]$MaxMensajes = 6
)

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$compose = Join-Path $repo "kafka-lab/compose.yaml"

Write-Host "Consumiendo $Topico con el grupo $Grupo..."
docker compose -f $compose exec -T kafka `
    /opt/kafka/bin/kafka-console-consumer.sh `
    --bootstrap-server kafka:9093 `
    --topic $Topico `
    --group $Grupo `
    --from-beginning `
    --max-messages $MaxMensajes `
    --property 'print.key=true' `
    --property 'print.partition=true' `
    --property 'print.offset=true'
if ($LASTEXITCODE -ne 0) { Write-Error "Fallo el consumo con el grupo $Grupo." }
