# Documento 05 - Paso 3: comprobar el clúster SIN resetear nada.
# Ejecutable en cualquier momento, tambien despues de reiniciar la maquina.
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File infra/02_Verificar_Cluster_Windows.ps1

$ErrorActionPreference = "Stop"

docker compose -f (Join-Path (Split-Path -Parent $PSScriptRoot) "infra\docker-compose.cluster.yml") ps

docker exec pedidos360-rabbit1 rabbitmqctl await_startup
docker exec pedidos360-rabbit2 rabbitmqctl await_startup

$estado1 = docker exec pedidos360-rabbit1 rabbitmqctl cluster_status
$estado2 = docker exec pedidos360-rabbit2 rabbitmqctl cluster_status

Write-Host "--- cluster_status de rabbit@rabbit1 ---"
$estado1 | ForEach-Object { Write-Host $_ }
Write-Host "--- cluster_status de rabbit@rabbit2 ---"
$estado2 | ForEach-Object { Write-Host $_ }

$esperados = @("rabbit@rabbit1", "rabbit@rabbit2")
$fallos = @()
foreach ($nodo in $esperados) {
    $en1 = ($estado1 | Select-String -SimpleMatch $nodo).Count -gt 0
    $en2 = ($estado2 | Select-String -SimpleMatch $nodo).Count -gt 0
    if (-not ($en1 -and $en2)) { $fallos += $nodo }
}

if ($fallos.Count -eq 0) {
    Write-Host "OK: running_nodes contiene rabbit@rabbit1 y rabbit@rabbit2 en ambos nodos."
    exit 0
}

Write-Warning "running_nodes incompleto en: $($fallos -join ', ')"
Write-Warning "Si rabbit2 nunca se unio, ejecute infra/01_Inicializar_Cluster_Windows.ps1 (solo primera vez)."
exit 1
