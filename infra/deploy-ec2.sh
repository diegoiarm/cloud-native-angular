#!/usr/bin/env bash
# Compila los dos microservicios, los sube a la EC2 y los reinicia.
#
#   ./infra/deploy-ec2.sh <ip-ec2> <ruta-clave.pem>
#
# O bien exportando EC2_HOST y EC2_KEY para no repetirlos:
#   export EC2_HOST=1.2.3.4 EC2_KEY=~/claves/dsy1107.pem
#   ./infra/deploy-ec2.sh
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

echo "==> Compilando"
mvn -q -f pedidos-api/pom.xml -DskipTests package
mvn -q -f catalogo-api/pom.xml -DskipTests package

pedidos_jar=$(ls pedidos-api/target/*.jar | grep -v original | head -1)
catalogo_jar=$(ls catalogo-api/target/*.jar | grep -v original | head -1)

echo "==> Subiendo a $EC2_HOST"
scp -i "$EC2_KEY" -o StrictHostKeyChecking=accept-new \
  "$pedidos_jar" "$EC2_USER@$EC2_HOST:$DESTINO/pedidos-api.jar"
scp -i "$EC2_KEY" -o StrictHostKeyChecking=accept-new \
  "$catalogo_jar" "$EC2_USER@$EC2_HOST:$DESTINO/catalogo-api.jar"

echo "==> Reiniciando servicios"
ssh -i "$EC2_KEY" -o StrictHostKeyChecking=accept-new "$EC2_USER@$EC2_HOST" \
  'sudo systemctl restart catalogo-api pedidos-api && sleep 12 &&
   systemctl is-active catalogo-api pedidos-api &&
   curl -s -o /dev/null -w "publico=%{http_code}\n" http://localhost:8080/api/publico &&
   curl -s -o /dev/null -w "orders sin token=%{http_code}\n" http://localhost:8080/api/orders &&
   curl -s -o /dev/null -w "catalog sin token=%{http_code}\n" http://localhost:8081/api/catalog/products'

echo "==> Listo"
