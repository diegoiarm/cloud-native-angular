# Documentos 03 y 04 - Politica de retencion selectiva.
#
# TTL 120000 ms y max-length 200 SOLO sobre las tres colas principales
#   ^pedidos360\.(notificaciones|cocina|documentos)\.queue$
# No se aplica a .dlq (no perder fallos antes de diagnosticarlos) ni a .retry.
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File infra/03_Politica_Retencion_Windows.ps1
#
# Verificacion manual:
#   docker exec pedidos360-rabbit1 rabbitmqctl list_policies

$ErrorActionPreference = "Stop"

$contenedor = "pedidos360-rabbit1"
$patron = '^pedidos360\.(notificaciones|cocina|documentos)\.queue$'
# PowerShell 5.1 elimina las comillas dobles al invocar un .exe: se escapan con \".
$definicion = '{\"message-ttl\":120000,\"max-length\":200}'

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker no esta disponible. Instale Docker Desktop antes de continuar."
}

docker exec $contenedor rabbitmqctl await_startup
if ($LASTEXITCODE -ne 0) { Write-Error "El contenedor $contenedor no esta en ejecucion." }

Write-Host "Aplicando la politica 'pedidos360-principales' solo a las colas principales..."
docker exec $contenedor rabbitmqctl set_policy --apply-to queues pedidos360-principales $patron $definicion
if ($LASTEXITCODE -ne 0) { Write-Error "No fue posible aplicar la politica." }

Write-Host "Reglas vigentes (rabbitmqctl list_policies):"
docker exec $contenedor rabbitmqctl list_policies
