# Pedidos360 — DSY1107

Sistema de pedidos con catálogo, autenticación con Microsoft Entra ID y autorización por roles.
Monorepo con los tres componentes de la entrega.

| Carpeta | Qué es | Puerto |
|---|---|---|
| `cloud-native-angular/` | Frontend Angular 21 + MSAL | 4200 |
| `pedidos-api/` | Microservicio de pedidos (Spring Boot 3.4, Java 21) | 8080 |
| `catalogo-api/` | Microservicio de catálogo y stock | 8081 |
| `rabbit-admin-api/` | Microservicio administrador de RabbitMQ (queues, exchanges, bindings) | 8082 |
| `ms-productor-sensores/` | Productor REST de lecturas hacia Kafka (S13 / EP5) | 8081 |
| `ms-monitor-sensores/` | Consumidor Kafka de lecturas y alertas (S13 / EP5) | 8082 |
| `kafka-admin-api/` | Administrador Kafka por REST: topics, grupos y lag (EP5 / EP6) | 8084 |
| `kafka-lab/` | Kafka KRaft + Kafbat UI, scripts y clúster de 3 brokers (S12 / EP6) | 9092 / 8090 · clúster 9092-9094 / 8091 |
| `infra/` | OpenAPI para AWS API Gateway, scripts de despliegue y clúster RabbitMQ | — |
| `monitor/` | Monitor de colas por la Management API (Python, sin dependencias) | — |
| `docker-compose.yml` | Broker RabbitMQ single-node para la mensajería asíncrona | 5672 / 15672 |

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
- Docker Desktop para el laboratorio Kafka de `kafka-lab/` y RabbitMQ

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
- Si el catálogo o RabbitMQ no responden, la API devuelve 503 y el pedido no cambia. Si
  RabbitMQ falla justo después de descontar stock al aceptar, el stock se repone.

## Mensajería asíncrona con RabbitMQ

`pedidos-api` publica eventos de pedido y tres consumers los procesan. Solo se desacoplan
tareas posteriores: la validación de stock, la persistencia y el cambio de estado siguen
siendo síncronos, porque el cliente espera su resultado.

### Topología

Toda la topología se declara en `pedidos-api/.../config/RabbitMQConfig.java`, separada de
la lógica de negocio. El producer (`PedidoEventoProducer`) solo conoce los exchanges y la
routing key; qué colas reciben cada evento lo resuelven los bindings.

| Exchange | Tipo | Binding | Cola | Consumer |
|---|---|---|---|---|
| `pedidos360.direct.exchange` | direct | `pedido.aceptado` | `pedidos360.cocina.queue` | `CocinaConsumer` |
| `pedidos360.direct.exchange` | direct | `pedido.despachado` | `pedidos360.documentos.queue` | `DocumentosConsumer` |
| `pedidos360.topic.exchange` | topic | `pedido.#` | `pedidos360.notificaciones.queue` | `NotificacionesConsumer` |
| `pedidos360.dlx.exchange` | direct | `<cola>.dlq` | `pedidos360.<dominio>.dlq` | — (se revisa y se purga) |

Cada cola principal declara `pedidos360.dlx.exchange` como dead-letter exchange y tiene su
cola `pedidos360.<dominio>.retry`, que retiene el mensaje 5 s y lo devuelve a la principal.

### Confirmación, reintentos y DLQ

Los consumers usan `ackMode = "MANUAL"`:

```
procesa bien                     -> basicAck
falla (intento 1 o 2)            -> copia en <dominio>.retry (x-intentos + 1) y basicAck del original
falla en el intento 3            -> basicNack(requeue=false) -> DLX -> <dominio>.dlq, con log del error
no se pudo publicar el reintento -> basicNack(requeue=false) -> DLQ (nunca queda Unacked)
```

`Reintentos` cuenta los intentos y publica en `.retry`; el ACK o NACK lo decide cada consumer.

### Levantar el broker

Single-node:

```bash
docker compose up -d
docker compose ps        # debe mostrar el broker healthy antes de seguir
```

Clúster de dos nodos (`rabbit1` en 5672/15672, `rabbit2` en 5673/15673). No levantar ambos
compose a la vez, porque comparten puertos:

```powershell
docker compose -f infra/docker-compose.cluster.yml up -d
powershell -ExecutionPolicy Bypass -File infra/01_Inicializar_Cluster_Windows.ps1   # verifica; une a mano solo si hace falta
powershell -ExecutionPolicy Bypass -File infra/02_Verificar_Cluster_Windows.ps1
powershell -ExecutionPolicy Bypass -File infra/03_Politica_Retencion_Windows.ps1    # TTL 120 s y max-length 200
```

Los nodos forman el clúster solos al arrancar (peer discovery en
`infra/rabbitmq-cloud/rabbitmq.conf`) y arrancan a la vez. RabbitMQ 4 necesita mayoría de
nodos para la metadata, y con 2 nodos eso son los 2: si uno arranca esperando al otro, el
clúster no vuelve después de un reinicio. Por eso no hay `depends_on` entre ellos.

La Management UI queda en http://localhost:15672 con `labuser` / `labpass` (solo laboratorio).

### Ejecutar con mensajería

```bash
cd catalogo-api     && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd pedidos-api      && mvn spring-boot:run -Dspring-boot.run.profiles=local
cd rabbit-admin-api && mvn spring-boot:run
cd cloud-native-angular && npm install && npm start
```

`pedidos-api` declara exchanges, colas y bindings al conectarse. Los consumers escriben en
la consola del servicio:

```
[NOTIFICACIÓN] {"pedidoId":101,"estado":"ACEPTADO",...}
[COCINA] {"pedidoId":101,"estado":"ACEPTADO",...}
[DOCUMENTOS] {"pedidoId":101,"estado":"DESPACHADO",...}
```

Si las colas ya existían en el broker con otros argumentos (versiones anteriores sin DLX),
RabbitMQ responde `PRECONDITION_FAILED`: elimínalas en la Management UI antes de arrancar.

### Fallo controlado (laboratorio)

`POST /api/lab/pedidos/aceptar` publica un pedido aceptado y uno despachado, marcando qué
cola debe fallar. Solo existe con `pedidos360.lab.habilitado=true`:

- con el perfil `local` se llama sin token (`pedidos360.lab.sin-token=true`);
- en la nube se habilita con `PEDIDOS360_LAB_HABILITADO=true` para la demostración EP4-08
  y exige un token con rol `Admin`, porque el puerto de la EC2 es alcanzable desde internet.

```bash
curl -X POST http://localhost:8080/api/lab/pedidos/aceptar -H "Content-Type: application/json" \
  -d '{"pedidoId":210,"clienteEmail":"prueba@example.com","falloCocina":true}'
```

En el log se ven los intentos 1/3 y 2/3, luego el NACK, y el mensaje queda en
`pedidos360.cocina.dlq`. Para revisar las colas:

```bash
python monitor/monitor_queues.py --una-vez    # Ready, Unacked, Consumers y alertas de DLQ
```

### Administrador de RabbitMQ (`rabbit-admin-api`)

Microservicio aparte que administra el broker por REST. Exige el scope `Pedidos.Read` y el
rol `Admin`. La lógica está en `RabbitAdminService`; el controller solo valida las entradas.

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/admin/rabbit/queues/{nombre}` | Mensajes Ready y consumers de la cola |
| POST | `/api/admin/rabbit/queues` | Crear cola; `ttlMs`, `maxLength` y `deadLetterExchange` opcionales |
| DELETE | `/api/admin/rabbit/queues/{nombre}` | Eliminar cola |
| DELETE | `/api/admin/rabbit/queues/{nombre}/mensajes` | Purgar cola (por ejemplo, una DLQ ya revisada) |
| POST | `/api/admin/rabbit/exchanges` | Crear exchange `direct` o `topic` |
| DELETE | `/api/admin/rabbit/exchanges/{nombre}` | Eliminar exchange |
| POST | `/api/admin/rabbit/bindings` | Crear binding |
| DELETE | `/api/admin/rabbit/bindings` | Eliminar binding |

Validaciones (400):
- nombres vacíos o con caracteres fuera de `[a-zA-Z0-9._-]`;
- tipo de exchange desconocido;
- TTL o max-length en cero, negativos o fuera de rango;
- comodines `*` o `#` en un binding de un exchange `direct`.

Otras respuestas:
- 404 si la cola no existe;
- 409 si se intenta modificar la topología de Pedidos360 (`pedidos360.*`), que solo se puede purgar;
- 503 si el broker no responde.

```bash
curl -X POST http://localhost:8082/api/admin/rabbit/queues -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" -d '{"nombre":"lab.auditoria.queue","ttlMs":60000}'
```

### Configuración

La conexión se parametriza con `RABBITMQ_ADDRESSES` (por defecto `localhost:5672,localhost:5673`),
`RABBITMQ_USER` y `RABBITMQ_PASSWORD`. `labuser` / `labpass` son solo para el laboratorio
local; en cloud hay que usar credenciales propias.

## Kafka (Semanas 12-13 / EP5-EP6)

Implementación paralela a RabbitMQ, según los documentos `03` y `01` de S12/S13 y
las hojas de ruta EP5/EP6. **No reemplaza** la mensajería de Pedidos360: el
laboratorio RabbitMQ sigue igual.

```
ms-productor-sensores ──POST /api/lecturas──► Kafka sensores-temperatura
                                            └► sensores-alertas (si temperatura >= 30 C)
Kafka ──@KafkaListener──► ms-monitor-sensores ──GET /api/monitor/*──► navegador
kafka-admin-api ──AdminClient──► topics, consumer groups, offsets y lag
Kafbat UI ──────────────────────► http://localhost:8090
```

La guía completa (S12 y S13, experimentos, DLT y clúster de 3 brokers) está en
[`kafka-lab/README.md`](kafka-lab/README.md).

### Topics

| Topic | Particiones | RF local | RF clúster | Uso |
|---|---|---|---|---|
| `sensores-temperatura` | 3 | 1 | 3 | Lecturas `sensorId, temperatura, unidad, secuencia` |
| `sensores-alertas` | 3 | 1 | 3 | Alertas cuando se supera el umbral |
| `sensores-temperatura.DLT` | 3 | 1 | 3 | Mensajes que agotaron reintentos |
| `sensores-alertas.DLT` | 3 | 1 | 3 | Idem para alertas |

La clave es `sensorId`, de modo que los eventos de un mismo sensor mantienen su
orden dentro de una partición.

### Levantar el laboratorio

```bash
docker compose -f kafka-lab/compose.yaml up -d
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/02_crear_topic.ps1

cd ms-productor-sensores && ./mvnw spring-boot:run   # 8081
cd ms-monitor-sensores   && ./mvnw spring-boot:run   # 8082

powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/05_enviar_6_lecturas.ps1
```

> **Puertos:** el documento S13 fija `8081` y `8082`, pero ya los usan
> `catalogo-api` y `rabbit-admin-api`. No ejecute ambos pares a la vez o
> arranque los servicios Kafka con `SERVER_PORT=8085` / `SERVER_PORT=8086`.

### Endpoints

| Método | Ruta | Qué hace |
|---|---|---|
| POST | `/api/lecturas` | Publica una lectura; `202` con partición y offset |
| GET | `/api/lecturas/estado` | Bootstrap, topics, umbral y réplicas |
| GET | `/api/monitor/lecturas` | Últimas 50 lecturas en memoria |
| GET | `/api/monitor/alertas` | Últimas 50 alertas en memoria |
| GET | `/api/monitor/estado` | Grupo y contadores |
| GET | `/api/admin/kafka/cluster` | Cluster ID, controller y brokers |
| GET/POST | `/api/admin/kafka/topics` | Listar o crear topics |
| GET/DELETE | `/api/admin/kafka/topics/{nombre}` | Detalle o eliminación (los topics del laboratorio están protegidos) |
| GET | `/api/admin/kafka/groups` | Consumer groups |
| GET | `/api/admin/kafka/groups/{grupo}/lag` | Offset actual, final y lag |

`kafka-admin-api` exige el scope `Pedidos.Read` y el rol `Admin`, igual que
`rabbit-admin-api`.

### Reintentos y DLT

Los consumers usan `ErrorHandlingDeserializer` y `DefaultErrorHandler`:
1 intento + 2 reintentos con 1 s de espera. Si el mensaje sigue fallando, se
publica en `<topic>.DLT` y el original se confirma. Para demostrarlo, publique
una lectura con `sensorId = "sensor-fallo"`:

```bash
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/09_probar_dlt.ps1
```

### Clúster de 3 brokers (EP6)

```bash
docker compose -f kafka-lab/compose.cluster.yaml up -d
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/07_crear_topicos_cluster.ps1
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/08_verificar_cluster.ps1
```

Crea los dos topics con 3 particiones y RF3, y deja la UI en
http://localhost:8091. No levantar este compose junto con el de un broker:
comparten el puerto 9092.

## Pruebas

```bash
cd pedidos-api        && mvn test      # 49 tests
cd catalogo-api       && mvn test      # 14 tests
cd rabbit-admin-api   && mvn test      # 20 tests
cd ms-productor-sensores && mvn test
cd ms-monitor-sensores   && mvn test
cd kafka-admin-api       && mvn test
cd cloud-native-angular  && npm test
```

Cubren los rechazos 401 y 403, los permisos de cada rol, las transiciones de estado, el
descuento de stock, el contrato HTTP entre los microservicios, la topología de RabbitMQ, el
ACK/NACK de los consumers y las validaciones del administrador. Los tests de Kafka cubren la
validación del DTO, la publicación de alertas en el segundo topic, el tope de 50 en memoria,
el retry/DLT configurado y las reglas del administrador. No necesitan broker.

## Despliegue

La guía completa, paso a paso, está en [`infra/DEPLOY.md`](infra/DEPLOY.md). En resumen:

1. RDS PostgreSQL con las bases `pedidosdb` y `catalogodb`.
2. Una EC2 con los tres servicios (`ec2-user-data.sh`) y, una sola vez,
   `ec2-rabbitmq-setup.sh`: Docker, credenciales propias de RabbitMQ y `rabbit-admin-api`.
3. `infra/deploy-ec2.sh` compila y sube los JAR y el clúster de dos nodos
   (`infra/rabbitmq-cloud/`), lo levanta y aplica la política de retención.
4. Importar `infra/api-gateway-openapi.yaml` en API Gateway como HTTP API y reemplazar
   `TU-EC2-DNS` y `TU-EC2-CATALOGO-DNS` por la IP de la instancia.
5. Publicar el frontend y completar `environment.prod.ts` con la URL del Gateway.
6. Agregar el dominio de producción como redirect URI en Entra y en el CORS de los
   servicios.
