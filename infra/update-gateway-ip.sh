#!/usr/bin/env bash
# Reemplaza la IP de la EC2 en todas las integraciones de la HTTP API.
# Útil cada vez que el Learner Lab cambia la IP pública de la instancia.
#
#   ./infra/update-gateway-ip.sh <api-id> <ip-nueva>
#
# Requiere AWS CLI configurado con las credenciales del laboratorio
# (AWS Details -> AWS CLI -> copiar el bloque a ~/.aws/credentials).
set -euo pipefail

API_ID="${1:?Uso: $0 <api-id> <ip-nueva>}"
IP_NUEVA="${2:?Uso: $0 <api-id> <ip-nueva>}"
REGION="${AWS_REGION:-us-east-1}"

echo "==> Integraciones actuales"
aws apigatewayv2 get-integrations --api-id "$API_ID" --region "$REGION" \
  --query 'Items[].[IntegrationId,IntegrationUri]' --output text |
while read -r id uri; do
  # Cambia solo el host, conservando puerto y ruta.
  nueva=$(echo "$uri" | sed -E "s#http://[^:/]+#http://${IP_NUEVA}#")
  if [[ "$nueva" == "$uri" ]]; then
    echo "sin cambios: $uri"
    continue
  fi
  echo "$uri  ->  $nueva"
  aws apigatewayv2 update-integration --api-id "$API_ID" --region "$REGION" \
    --integration-id "$id" --integration-uri "$nueva" >/dev/null
done

echo "==> Listo. Verifica con:"
echo "curl -i https://${API_ID}.execute-api.${REGION}.amazonaws.com/api/publico"
