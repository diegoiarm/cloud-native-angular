# Pedidos360 — DSY1107

Sistema de pedidos con catálogo, autenticación con Microsoft Entra ID y autorización por roles.
Monorepo con los tres componentes de la entrega.

| Carpeta | Qué es | Puerto |
|---|---|---|
| `cloud-native-angular/` | Frontend Angular 21 + MSAL | 4200 |
| `pedidos-api/` | Microservicio de pedidos (Spring Boot 3.4, Java 21) | 8080 |
| `catalogo-api/` | Microservicio de catálogo y stock | 8081 |
| `infra/` | Definición OpenAPI para AWS API Gateway | — |
| `docker-compose.yml` | Broker RabbitMQ para la mensajería asíncrona | 5672 / 15672 |

```
Angular + MSAL  ──Access Token──►  API Gateway (JWT Authorizer)
                                          │
                                          ▼
                                   pedidos-api ──token──► catalogo-api
                                          │                     │
                                          ▼                     ▼
                                      pedidosdb             catalogodb
```

Los dos microservicios validan el token por su cuenta: firma, issuer, audience, el scope
`Pedidos.Read` y los App Roles. `pedidos-api` reenvía el token del usuario cuando llama al
catálogo, así que el catálogo aplica sus propias reglas.

## Requisitos

- Java 21 y Maven
- Node.js 22+
- PostgreSQL, o bien el perfil `local` que usa H2 y no necesita instalar nada

## Entra ID

Tenant `6bf42f50-ccc9-46e4-ae46-2b6534675957`.

- **SPA** `67e79135-0890-48e1-af3b-0a217b270cfa`, con redirect URI `http://localhost:4200` y
  permiso `Pedidos.Read` con consentimiento de administrador concedido.
- **API** `371e0368-c37d-4768-9266-0212824e67ba`, que expone el scope `Pedidos.Read` y define
  los App Roles `Admin`, `Operador` y `Cliente`.
- Cada usuario de prueba necesita un rol asignado en Enterprise applications → Users and groups.
  Sin rol, la API responde 403.

Estos valores ya están en `environment.ts` y en el `application.yml` de cada servicio.

## Ejecutar en local

Con H2, sin base de datos que instalar:

```bash
cd catalogo-api && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd pedidos-api  && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd cloud-native-angular && npm install && npm start
```

Con PostgreSQL, que es lo que se usa en la entrega:

```sql
CREATE DATABASE pedidosdb;
CREATE DATABASE catalogodb;
```

```bash
# en cada servicio, antes de mvn spring-boot:run
export DB_HOST=localhost DB_USER=postgres DB_PASSWORD=<clave>
export CATALOGO_URL=http://localhost:8081   # solo pedidos-api
```

La app queda en http://localhost:4200. El catálogo arranca con tres productos de ejemplo.

Comprobación rápida:

```bash
curl http://localhost:8080/api/publico            # 200
curl -i http://localhost:8080/api/orders          # 401 sin token
curl -i http://localhost:8081/api/catalog/products # 401 sin token
```

## Endpoints

| Método | Ruta | Quién |
|---|---|---|
| GET | `/api/publico` | público |
| GET | `/api/me` | cualquier usuario autenticado |
| GET | `/api/orders` | Cliente ve los suyos; Operador y Admin ven todos |
| GET | `/api/pedidos` | Alias del listado, para la checklist de la guía del curso |
| GET | `/api/orders/{id}` | igual que el listado |
| POST | `/api/orders` | Cliente, Operador |
| PUT | `/api/orders/{id}/status` | Operador, Admin |
| GET | `/api/catalog/products[/{id}]` | los tres roles |
| POST, PUT | `/api/catalog/products` | Admin |
| POST | `/api/catalog/stock/descontar` y `/reponer` | Operador, Admin. Los llama `pedidos-api`, no se publican en el Gateway |

Todas las rutas salvo `/api/publico` exigen el scope `Pedidos.Read`. Sin token responden 401;
con token pero sin el rol o el scope, 403.

## Reglas de pedidos

Estados: `CREADO → ACEPTADO → EN_PREPARACION → DESPACHADO → ENTREGADO`, y se puede cancelar
desde los tres primeros.

- No existe el paso de CREADO a DESPACHADO, así que un pedido no se despacha sin aceptarlo antes.
- Al aceptar, el catálogo descuenta el stock. Si falta stock de algún producto no se descuenta
  nada y el pedido se queda en CREADO.
- Al cancelar un pedido que ya descontó stock, se repone.
- El precio y el nombre del producto se copian desde el catálogo al crear el pedido, para que
  el pedido no cambie si después se edita el producto.
- Los errores de negocio responden 409 con un mensaje explicativo en el campo `mensaje`.

## Mensajería asíncrona con RabbitMQ

`pedidos-api` publica eventos de pedido en un DirectExchange y tres colas los consumen.
Solo se desacoplan tareas posteriores: la validación de stock, la persistencia y el
cambio de estado siguen siendo síncronos, porque el cliente espera su resultado.

| Routing key | notificaciones.queue | cocina.queue | despacho.queue |
|---|---|---|---|
| `pedido.creado` | sí | no | no |
| `pedido.aceptado` | sí | sí | no |
| `pedido.despachado` | sí | no | sí |

`EN_PREPARACION` y `CANCELADO` no tienen binding, así que no publican nada. El Exchange
exige coincidencia exacta de routing key: `pedido.aceptado` y `pedido.aceptado.v2`
son claves distintas.

El producer (`PedidoEventoProducer`) solo conoce el Exchange y la routing key; no sabe
qué colas ni qué consumers están suscritos. Eso lo resuelven los Bindings.

### Levantar el broker

```bash
docker compose up -d
docker compose ps        # debe mostrar el broker healthy antes de seguir
```

La Management UI queda en http://localhost:15672 con `guest` / `guest`. El puerto 5672
es AMQP (conexiones de las aplicaciones) y el 15672 es la interfaz web de administración.

### Ejecutar con mensajería

```bash
docker compose up -d
cd catalogo-api && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd pedidos-api  && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd cloud-native-angular && npm install && npm start
```

`pedidos-api` declara el Exchange, las colas y los bindings al conectarse, así que
aparecen solos en la Management UI. Los consumers escriben en la consola del servicio:

```
[NOTIFICACIÓN] {"pedidoId":101,"estado":"ACEPTADO",...}
[COCINA] {"pedidoId":101,"estado":"ACEPTADO",...}
[DESPACHO] {"pedidoId":101,"estado":"DESPACHADO",...}
```

Para comprobar que un mensaje sobrevive a un consumer apagado, detén `pedidos-api` con
un pedido en `ACEPTADO`: la cola queda con el mensaje en Ready y se procesa al reiniciar.

La conexión se parametriza con `RABBITMQ_HOST`, `RABBITMQ_PORT`, `RABBITMQ_USER` y
`RABBITMQ_PASSWORD`. Los valores por defecto (`guest`/`guest`) son para el laboratorio
local; en cloud hay que usar credenciales propias, nunca las del compose.

Lo que esta etapa deja fuera, y corresponde a la continuidad de la unidad: ACK/NACK
manual, reintentos, DLX/DLQ, prefetch, TTL, clúster de dos nodos y Kafka. La
consistencia entre la transacción de base de datos y la publicación del mensaje también
se profundiza más adelante.

## Pruebas

```bash
cd pedidos-api  && mvn test      # 34 tests
cd catalogo-api && mvn test      # 14 tests
cd cloud-native-angular && npm test
```

Cubren los rechazos 401 y 403, los permisos de cada rol, las transiciones de estado, el
descuento de stock y el contrato HTTP entre los dos microservicios.

## Despliegue

1. RDS PostgreSQL con las bases `pedidosdb` y `catalogodb`.
2. Los dos servicios en EC2, con las variables `DB_HOST`, `DB_USER`, `DB_PASSWORD` y
   `CATALOGO_URL`.
3. Importar `infra/api-gateway-openapi.yaml` en API Gateway como HTTP API y reemplazar
   `TU-EC2-DNS` y `TU-EC2-CATALOGO-DNS` por la IP de la instancia.
4. Publicar el frontend y completar `environment.prod.ts` con la URL del Gateway.
5. Agregar el dominio de producción como redirect URI en Entra y en el CORS de los dos
   servicios, que hoy solo permiten `http://localhost:4200`.
