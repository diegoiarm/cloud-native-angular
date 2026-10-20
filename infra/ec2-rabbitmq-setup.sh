#!/bin/bash
# Prepara una EC2 ya creada con ec2-user-data.sh para la mensajería (EP4).
# Se ejecuta UNA vez, en la instancia, después de subirlo con scp:
#
#   sudo RABBITMQ_PASSWORD='<clave-nueva>' bash ec2-rabbitmq-setup.sh
#
# Opcionales: RABBITMQ_USER (pedidos360), JAVA_XMX (256m; en t3.medium puede ser 512m).
#
# Deja instalado Docker con Compose, una swap de 1 GB, el .env del clúster en
# /opt/pedidos360/rabbitmq, las variables de RabbitMQ en pedidos-api y el servicio
# rabbit-admin-api (:8082). El compose y los JAR los sube después infra/deploy-ec2.sh.
# Volver a ejecutarlo es seguro: conserva la Erlang cookie existente.
set -euo pipefail

RABBITMQ_USER="${RABBITMQ_USER:-pedidos360}"
RABBITMQ_PASSWORD="${RABBITMQ_PASSWORD:?Defina RABBITMQ_PASSWORD (no use labpass)}"
JAVA_XMX="${JAVA_XMX:-256m}"
COMPOSE_VERSION="v2.29.7"
BASE=/opt/pedidos360
DIR_RABBIT="$BASE/rabbitmq"

echo "==> Docker y Compose"
dnf -y install docker
systemctl enable --now docker
usermod -aG docker ec2-user
if ! docker compose version >/dev/null 2>&1; then
  mkdir -p /usr/local/lib/docker/cli-plugins
  curl -fsSL "https://github.com/docker/compose/releases/download/${COMPOSE_VERSION}/docker-compose-linux-$(uname -m)" \
    -o /usr/local/lib/docker/cli-plugins/docker-compose
  chmod +x /usr/local/lib/docker/cli-plugins/docker-compose
fi
docker compose version

echo "==> Swap de 1 GB (respaldo de memoria en t3.small)"
if ! swapon --show | grep -q /swapfile; then
  fallocate -l 1G /swapfile
  chmod 600 /swapfile
  mkswap /swapfile
  swapon /swapfile
  grep -q '^/swapfile' /etc/fstab || echo '/swapfile none swap sw 0 0' >>/etc/fstab
fi

echo "==> Credenciales del clúster en $DIR_RABBIT/.env"
mkdir -p "$DIR_RABBIT"
cookie=""
if [[ -f "$DIR_RABBIT/.env" ]]; then
  cookie=$(grep '^RABBITMQ_ERLANG_COOKIE=' "$DIR_RABBIT/.env" | cut -d= -f2- || true)
fi
# La cookie cambia solo la primera vez: con otra, los nodos existentes no se reconocen.
cookie="${cookie:-$(openssl rand -hex 24)}"
umask 077
cat >"$DIR_RABBIT/.env" <<EOF
RABBITMQ_USER=${RABBITMQ_USER}
RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}
RABBITMQ_ERLANG_COOKIE=${cookie}
EOF
umask 022
chown -R ec2-user:ec2-user "$DIR_RABBIT"

echo "==> Variables de RabbitMQ para los servicios"
# Drop-ins de systemd: completan los servicios de ec2-user-data.sh sin reescribirlos.
dropin() {
  local servicio="$1" contenido="$2"
  mkdir -p "/etc/systemd/system/${servicio}.service.d"
  umask 077
  printf '[Service]\n%s\n' "$contenido" >"/etc/systemd/system/${servicio}.service.d/pedidos360.conf"
  umask 022
}
rabbit_env="Environment=RABBITMQ_ADDRESSES=localhost:5672,localhost:5673
Environment=RABBITMQ_USER=${RABBITMQ_USER}
Environment=RABBITMQ_PASSWORD=${RABBITMQ_PASSWORD}
Environment=JAVA_TOOL_OPTIONS=-Xmx${JAVA_XMX}"
# PEDIDOS360_LAB_HABILITADO: fallo controlado de EP4-08, protegido con rol Admin.
dropin pedidos-api "${rabbit_env}
Environment=PEDIDOS360_LAB_HABILITADO=true"
dropin catalogo-api "Environment=JAVA_TOOL_OPTIONS=-Xmx${JAVA_XMX}"

echo "==> Servicio rabbit-admin-api (:8082)"
# Mismo tenant, audiencia y CORS que pedidos-api, tomados de su unidad.
entra_env=$(grep -E '^Environment=(ENTRA_TENANT_ID|ENTRA_API_CLIENT_ID|CORS_ORIGINS)=' \
  /etc/systemd/system/pedidos-api.service || true)
cat >/etc/systemd/system/rabbit-admin-api.service <<EOF
[Unit]
Description=rabbit-admin-api
After=network.target docker.service

[Service]
User=ec2-user
WorkingDirectory=${BASE}
${entra_env}
ExecStart=/usr/bin/java -jar ${BASE}/rabbit-admin-api.jar --server.port=8082
SuccessExitStatus=143
Restart=always
RestartSec=15

[Install]
WantedBy=multi-user.target
EOF
dropin rabbit-admin-api "$rabbit_env"

systemctl daemon-reload
systemctl enable rabbit-admin-api
echo "==> Listo. Ahora, desde tu PC: ./infra/deploy-ec2.sh <ip-ec2> <clave.pem>"
