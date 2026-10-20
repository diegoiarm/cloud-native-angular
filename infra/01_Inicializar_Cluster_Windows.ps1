# Documento 05 - Paso 1, Paso 2 y Paso 3: clúster de dos nodos.
# El clúster se forma solo al arrancar; el Paso 2 hace "reset" + join del segundo
# nodo únicamente si eso no ocurrió. En reinicios normales basta:
#   docker compose -f infra/docker-compose.cluster.yml up -d
#   powershell -ExecutionPolicy Bypass -File infra/02_Verificar_Cluster_Windows.ps1
#
# Uso desde la raiz del repositorio:
#   powershell -ExecutionPolicy Bypass -File infra/01_Inicializar_Cluster_Windows.ps1

$ErrorActionPreference = "Stop"

$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz
$compose = "infra/docker-compose.cluster.yml"

if (-not (Get-Command docker -ErrorAction SilentlyContinue)) {
    Write-Error "Docker no esta disponible. Instale Docker Desktop antes de continuar."
}

Write-Host "Paso 1 - Infraestructura inicial"
docker compose -f $compose up -d
if ($LASTEXITCODE -ne 0) { Write-Error "No se pudo levantar el compose del clúster." }
docker compose -f $compose ps

# "up -d" vuelve apenas crea los contenedores; el nodo Erlang tarda unos segundos más
# en arrancar y await_startup falla si todavía no existe. Se espera el healthcheck.
function Esperar-Healthy($contenedor) {
    for ($i = 0; $i -lt 36; $i++) {
        $estado = docker inspect -f '{{.State.Health.Status}}' $contenedor
        if ($estado -eq "healthy") { return }
        Start-Sleep -Seconds 5
    }
    Write-Error "$contenedor no quedo healthy en 3 minutos (estado: $estado)."
}
Esperar-Healthy "pedidos360-rabbit1"
Esperar-Healthy "pedidos360-rabbit2"

docker exec pedidos360-rabbit1 rabbitmqctl await_startup
if ($LASTEXITCODE -ne 0) { Write-Error "rabbit1 no arranco." }
docker exec pedidos360-rabbit2 rabbitmqctl await_startup
if ($LASTEXITCODE -ne 0) { Write-Error "rabbit2 no arranco." }

Write-Host "Paso 2 - Unir el segundo nodo (solo si el clúster no se formó solo)"
# Con peer discovery (rabbitmq-cloud/rabbitmq.conf) los nodos se unen al arrancar;
# el join manual del documento 05 queda solo como respaldo.
$estado = docker exec pedidos360-rabbit1 rabbitmqctl cluster_status --formatter json | ConvertFrom-Json
if ($estado.running_nodes -contains "rabbit@rabbit2") {
    Write-Host "El clúster ya se formó: rabbit@rabbit2 está en running_nodes. Se omite el join manual."
} else {
    docker exec pedidos360-rabbit2 rabbitmqctl stop_app
    docker exec pedidos360-rabbit2 rabbitmqctl reset
    docker exec pedidos360-rabbit2 rabbitmqctl join_cluster rabbit@rabbit1
    if ($LASTEXITCODE -ne 0) { Write-Error "join_cluster fallo: revise que ambos nodos tengan la misma Erlang Cookie." }
    docker exec pedidos360-rabbit2 rabbitmqctl start_app
}

Write-Host "Paso 3 - Verificar ambos nodos"
docker exec pedidos360-rabbit1 rabbitmqctl cluster_status
docker exec pedidos360-rabbit2 rabbitmqctl cluster_status
Write-Host "Esperado: running_nodes contiene rabbit@rabbit1 y rabbit@rabbit2."

& (Join-Path $PSScriptRoot "02_Verificar_Cluster_Windows.ps1")
