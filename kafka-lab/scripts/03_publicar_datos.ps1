# Documento 03, seccion 7: publicar las seis lecturas con clave.
# Cada linea tiene el formato CLAVE|JSON; parse.key=true conserva la clave para
# que los eventos del mismo sensor mantengan su orden dentro de una particion.
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/03_publicar_datos.ps1

$ErrorActionPreference = "Stop"

$repo = Split-Path -Parent (Split-Path -Parent $PSScriptRoot)
$compose = Join-Path $repo "kafka-lab/compose.yaml"
$datos = Join-Path $repo "kafka-lab/datos/lecturas_con_clave.txt"

if (-not (Test-Path $datos)) { Write-Error "No existe $datos." }

Write-Host "Publicando las seis lecturas en sensores-temperatura..."
Get-Content -Encoding UTF8 $datos | `
    docker compose -f $compose exec -T kafka `
    /opt/kafka/bin/kafka-console-producer.sh `
    --bootstrap-server kafka:9093 `
    --topic sensores-temperatura `
    --property 'parse.key=true' `
    --property 'key.separator=|'
if ($LASTEXITCODE -ne 0) { Write-Error "Fallo la publicacion de datos." }

Write-Host "Listo. Revise Topics -> sensores-temperatura -> Messages en http://localhost:8090"
