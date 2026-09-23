#!/bin/bash
# User data para la instancia EC2 (Amazon Linux 2023) que corre los dos microservicios.
# Pegar en "Advanced details -> User data" al lanzar la instancia, reemplazando la
# sección CONFIGURACIÓN.
#
# Deja instalado Java 21, crea las dos bases en RDS y registra pedidos-api (:8080) y
# catalogo-api (:8081) como servicios systemd. Los JAR se suben después con scp a
# /opt/pedidos360; hasta entonces los servicios quedan en espera.
set -euo pipefail
set -x

# ----------------------- CONFIGURACIÓN -----------------------
DB_HOST="dsy1107-pedidos-db.xxxxxxxx.us-east-1.rds.amazonaws.com"
DB_USER="postgres"
DB_PASSWORD="TU-CLAVE-RDS"

ENTRA_TENANT_ID="6bf42f50-ccc9-46e4-ae46-2b6534675957"
ENTRA_API_CLIENT_ID="371e0368-c37d-4768-9266-0212824e67ba"

# Orígenes del frontend, separados por coma.
CORS_ORIGINS="http://localhost:4200,https://TU-DOMINIO.vercel.app"
# -------------------------------------------------------------

dnf -y update
dnf -y install java-21-amazon-corretto-headless
# El cliente psql sirve para crear las bases; el nombre del paquete varía según la AMI.
dnf -y install postgresql16 || dnf -y install postgresql15 || dnf -y install postgresql

mkdir -p /opt/pedidos360
chown ec2-user:ec2-user /opt/pedidos360

# Las bases: RDS crea solo pedidosdb, catalogodb hay que agregarla.
# set +x evita que la contraseña quede escrita en /var/log/cloud-init-output.log.
crear_bases() {
  set +x
  export PGPASSWORD="$DB_PASSWORD"
  local base
  for base in pedidosdb catalogodb; do
    psql -h "$DB_HOST" -U "$DB_USER" -d postgres -tc \
      "SELECT 1 FROM pg_database WHERE datname='${base}'" | grep -q 1 ||
      psql -h "$DB_HOST" -U "$DB_USER" -d postgres -c "CREATE DATABASE ${base}"
  done
  unset PGPASSWORD
  set -x
}

# Si el RDS todavía no acepta conexiones (falta la regla 5432 desde el SG de esta
# instancia), el script sigue igual y deja los servicios creados. Después basta con
# corregir la regla y volver a ejecutar:  sudo bash /var/lib/cloud/instance/user-data.txt
crear_bases || echo "AVISO: no se pudieron crear las bases. Revisa la regla 5432 del SG del RDS."

crear_servicio() {
  local nombre="$1" puerto="$2" base="$3" extra="$4"
  cat >"/etc/systemd/system/${nombre}.service" <<EOF
[Unit]
Description=${nombre}
After=network.target

[Service]
User=ec2-user
WorkingDirectory=/opt/pedidos360
Environment=DB_HOST=${DB_HOST}
Environment=DB_PORT=5432
Environment=DB_NAME=${base}
Environment=DB_USER=${DB_USER}
Environment=DB_PASSWORD=${DB_PASSWORD}
Environment=ENTRA_TENANT_ID=${ENTRA_TENANT_ID}
Environment=ENTRA_API_CLIENT_ID=${ENTRA_API_CLIENT_ID}
Environment=CORS_ORIGINS=${CORS_ORIGINS}
${extra}
ExecStart=/usr/bin/java -jar /opt/pedidos360/${nombre}.jar --server.port=${puerto}
SuccessExitStatus=143
Restart=always
RestartSec=15

[Install]
WantedBy=multi-user.target
EOF
}

crear_servicio "catalogo-api" 8081 "catalogodb" ""
# pedidos-api llama al catálogo en la misma instancia.
crear_servicio "pedidos-api" 8080 "pedidosdb" "Environment=CATALOGO_URL=http://localhost:8081"

systemctl daemon-reload
systemctl enable catalogo-api pedidos-api
# Arrancan cuando existan los JAR; Restart=always reintenta solo.
systemctl start catalogo-api pedidos-api || true
