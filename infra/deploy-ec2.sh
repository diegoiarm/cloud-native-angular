#!/usr/bin/env bash
# Compila los tres microservicios, los sube a la EC2 junto con el clúster RabbitMQ,
# lo levanta y reinicia los servicios.
#
#   ./infra/deploy-ec2.sh <ip-ec2> <ruta-clave.pem>
#
# O bien exportando EC2_HOST y EC2_KEY para no repetirlos:
#   export EC2_HOST=1.2.3.4 EC2_KEY=~/claves/dsy1107.pem
#   ./infra/deploy-ec2.sh
#
# Requiere haber ejecutado una vez infra/ec2-rabbitmq-setup.sh en la instancia.
set -euo pipefail

EC2_HOST="${1:-${EC2_HOST:-}}"
EC2_KEY="${2:-${EC2_KEY:-}}"
EC2_USER="${EC2_USER:-ec2-user}"
DESTINO="/opt/pedidos360"

if [[ -z "$EC2_HOST" || -z "$EC2_KEY" ]]; then
  echo "Uso: $0 <ip-ec2> <ruta-clave.pem>" >&2
  exit 1
fi

raiz="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$raiz"
SSH_OPTS=(-i "$EC2_KEY" -o StrictHostKeyChecking=accept-new)

echo "==> Compilando"
mvn -q -f pedidos-api/pom.xml -DskipTests package
mvn -q -f catalogo-api/pom.xml -DskipTests package
mvn -q -f rabbit-admin-api/pom.xml -DskipTests package

# Spring Boot deja el ejecutable como *.jar y el original como *.jar.original.
jar_de() { local jars=("$1"/target/*.jar); echo "${jars[0]}"; }

echo "==> Subiendo a $EC2_HOST"
scp "${SSH_OPTS[@]}" "$(jar_de pedidos-api)" "$EC2_USER@$EC2_HOST:$DESTINO/pedidos-api.jar"
scp "${SSH_OPTS[@]}" "$(jar_de catalogo-api)" "$EC2_USER@$EC2_HOST:$DESTINO/catalogo-api.jar"
scp "${SSH_OPTS[@]}" "$(jar_de rabbit-admin-api)" "$EC2_USER@$EC2_HOST:$DESTINO/rabbit-admin-api.jar"
scp "${SSH_OPTS[@]}" infra/rabbitmq-cloud/docker-compose.yml infra/rabbitmq-cloud/rabbitmq.conf \
  "$EC2_USER@$EC2_HOST:$DESTINO/rabbitmq/"

echo "==> Clúster RabbitMQ y servicios"
ssh "${SSH_OPTS[@]}" "$EC2_USER@$EC2_HOST" 'bash -s' <<'REMOTO'
set -euo pipefail
cd /opt/pedidos360/rabbitmq
sudo docker compose up -d
# Los dos nodos arrancan a la vez; healthy = RabbitMQ terminó de arrancar.
for c in pedidos360-rabbit1 pedidos360-rabbit2; do
  for i in $(seq 1 40); do
    [[ "$(sudo docker inspect -f '{{.State.Health.Status}}' "$c")" == healthy ]] && break
    sleep 5
  done
  echo "$c: $(sudo docker inspect -f '{{.State.Health.Status}}' "$c")"
done
sudo docker exec pedidos360-rabbit1 rabbitmqctl -q cluster_status --formatter json |
  python3 -c 'import json,sys; print("running_nodes:", json.load(sys.stdin)["running_nodes"])'
# Retención de las tres colas principales (documentos 03 y 04), nunca en .retry ni .dlq.
sudo docker exec pedidos360-rabbit1 rabbitmqctl -q set_policy --apply-to queues pedidos360-principales \
  '^pedidos360\.(notificaciones|cocina|documentos)\.queue$' '{"message-ttl":120000,"max-length":200}'

sudo systemctl restart catalogo-api pedidos-api rabbit-admin-api
# En t3.small tres JVM arrancando a la vez tardan; se espera a que respondan.
for puerto in 8080 8081 8082; do
  for i in $(seq 1 36); do
    curl -s -o /dev/null "http://localhost:$puerto/" && break
    sleep 5
  done
done
systemctl is-active catalogo-api pedidos-api rabbit-admin-api
curl -s -o /dev/null -w "publico=%{http_code}\n" http://localhost:8080/api/publico
curl -s -o /dev/null -w "orders sin token=%{http_code}\n" http://localhost:8080/api/orders
curl -s -o /dev/null -w "catalog sin token=%{http_code}\n" http://localhost:8081/api/catalog/products
curl -s -o /dev/null -w "admin rabbit sin token=%{http_code}\n" http://localhost:8082/api/admin/rabbit/queues/x
# pedidos-api declara la topología al conectarse: deben verse las 9 colas.
sudo docker exec pedidos360-rabbit1 rabbitmqctl -q list_queues name messages_ready consumers
REMOTO

echo "==> Listo"
