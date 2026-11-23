# Kafka Lab — Semanas 12 y 13 / EP5 y EP6

Implementación de los documentos:

- `03_DSY1107-S12_TUTORIAL_IMPLEMENTACION.docx` — Kafka local con Docker y Kafbat UI.
- `01_DSY1107_S13_TUTORIAL_IMPLEMENTACION.docx` — productor y consumidor Spring Boot.
- `05_DSY1107-S12_HOJA_RUTA_EP5_EP6.docx` — dos topics, DLT, administración y RF3.
- `05_DSY1107_S13_HOJA_RUTA_EP5_EP6.docx` — preparación de EP5/EP6.

RabbitMQ se mantiene intacto: este laboratorio es paralelo y no reemplaza la
mensajería de Pedidos360.

## Componentes

| Componente | Dirección | Función |
|---|---|---|
| Kafka KRaft (1 broker) | `localhost:9092` / `kafka:9093` interno | Broker + controller |
| Kafbat UI | http://localhost:8090 | Topics, offsets, grupos |
| `ms-productor-sensores` | http://localhost:8085/api/lecturas | Publica lecturas por REST |
| `ms-monitor-sensores` | http://localhost:8086/api/monitor/lecturas | Consume y muestra en memoria |
| `kafka-admin-api` | http://localhost:8084/api/admin/kafka | Administración por código |
| Cluster ZooKeeper | `localhost:9092,9093,9094` / ZK 2181-2183 / UI 8091 | 3 ZooKeeper + 3 brokers, RF3 para EP6 |

### Topics

| Topic | Particiones | RF local | RF cluster | Uso |
|---|---|---|---|---|
| `sensores-temperatura` | 3 | 1 | 3 | Contrato `sensorId, temperatura, unidad, secuencia` |
| `sensores-alertas` | 3 | 1 | 3 | Alertas que publica el monitor al reaccionar (umbral o alza brusca) |
| `sensores-temperatura.DLT` | 3 | 1 | 3 | Mensajes que agotaron reintentos |
| `sensores-alertas.DLT` | 3 | 1 | 3 | Idem para alertas |

La clave de publicación es `sensorId`. Una partición no equivale a un broker:
el laboratorio local tiene 1 broker y 3 particiones; el cluster EP6 tiene 3
brokers y 3 réplicas.

## Requisitos

- JDK 21 y Maven (o los wrappers incluidos).
- Docker Desktop abierto.
- Puertos libres: `9092`, `8090`, `8084`, `8085`, `8086`.

> **Puertos:** el documento S13 propone productor `8081` y monitor `8082`, pero
> en este repositorio `catalogo-api` usa `8081` y `rabbit-admin-api` usa `8082`.
> Por eso el productor queda en `8085` y el monitor en `8086`: Pedidos360 con
> RabbitMQ y el laboratorio Kafka pueden correr a la vez. Los scripts ya apuntan
> a `8085`; `SERVER_PORT` y `-Url` permiten cambiarlo.

## 1. Levantar Kafka local

```powershell
docker compose -f kafka-lab/compose.yaml up -d
docker compose -f kafka-lab/compose.yaml ps      # kafka debe estar healthy
```

Abrir http://localhost:8090 y localizar el clúster `DSY1107-local`.
La UI se conecta con `kafka:9093` (nunca `localhost:9092` desde otro contenedor).
Para pausar use `stop` / `start`; no use `down` como pausa habitual porque se
pueden perder topics y registros.

## 2. Crear topics

```powershell
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/02_crear_topic.ps1
```

Crea los dos topics de negocio con 3 particiones y RF1, más sus DLT. Verifique
en la UI que `Partitions: 3` y `ReplicationFactor: 1`.

## 3. Publicar las seis lecturas por CLI (S12)

```powershell
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/03_publicar_datos.ps1
```

Se publican las seis líneas de `datos/lecturas_con_clave.txt` con
`parse.key=true` y separador `|`. La UI muestra claves, particiones y offsets.

## 4. Consumir y ver el grupo (S12)

```powershell
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/04_consumir_grupo.ps1
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/06_verificar_grupo.ps1
```

El grupo `grupo-monitor-s12` aparece con `CURRENT-OFFSET`, `LOG-END-OFFSET` y
`LAG`. Repita con `-Grupo grupo-auditoria-s12` para comprobar lectura
independiente.

## 5. Productor y monitor Spring Boot (S13)

Terminal 2:

```powershell
cd ms-productor-sensores
./mvnw spring-boot:run
```

Terminal 3:

```powershell
cd ms-monitor-sensores
./mvnw spring-boot:run
```

Estado:

```powershell
curl http://localhost:8085/api/lecturas/estado
curl http://localhost:8086/api/monitor/estado
```

Enviar seis lecturas por REST:

```powershell
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/05_enviar_6_lecturas.ps1
curl http://localhost:8086/api/monitor/lecturas
curl http://localhost:8086/api/monitor/alertas
```

Contrato de una lectura:

```json
{
  "sensorId": "sensor-A",
  "temperatura": 22.4,
  "unidad": "C",
  "secuencia": 7
}
```

La respuesta `202` incluye `particion` y `offset`: el broker confirmó la
publicación, pero el monitor la mostrará cuando su listener la procese.

Validación: `secuencia` negativa responde `400` (pruebe con `-5`). Si Kafka no
está disponible, el productor responde `503`.

## 6. Procesamiento reactivo: el monitor reacciona a cada lectura

El productor solo ingiere: publica cada lectura en `sensores-temperatura` y
responde `202` con partición y offset. **No decide si hay alerta.**

`ms-monitor-sensores` consume cada lectura y reacciona en el momento
(`TemperaturaConsumer` + `ReglasReaccion`):

| Regla | Condición | Severidad |
|---|---|---|
| `UMBRAL` | temperatura >= `UMBRAL_ALERTA_C` (30.0) | ALTA |
| `ALZA_BRUSCA` | subida >= `ALZA_BRUSCA_C` (5.0) respecto de la lectura anterior **del mismo sensor** | MEDIA |

Si se cumple alguna, `PublicadorAlertas` publica un `AlertaSensor` en
`sensores-alertas` con la misma clave `sensorId`. `AlertaConsumer` la recibe en
el segundo topic. Si se cumplen las dos, sale una sola alerta ALTA con ambos
motivos.

`ALZA_BRUSCA` necesita recordar la última temperatura de cada sensor. Funciona
porque la clave es `sensorId`: todas las lecturas de un sensor van a la misma
partición y el consumer las recibe en orden.

Si Kafka no confirma la alerta, el consumer lanza la excepción **antes** de
guardar la lectura: el `DefaultErrorHandler` reintenta (sin duplicarla) y, si
sigue fallando, la manda a `sensores-temperatura.DLT`.

### En tiempo real

- `GET /api/monitor/stream`: Server-Sent Events con un evento `lectura` por
  cada lectura y un evento `alerta` por cada alerta, apenas se procesan.
- http://localhost:8086/ : página que muestra las dos listas en vivo.

Para ver la regla de alza brusca sin superar el umbral:

```powershell
curl -X POST http://localhost:8085/api/lecturas -H "Content-Type: application/json" -d '{"sensorId":"sensor-C","temperatura":20.0,"unidad":"C","secuencia":1}'
curl -X POST http://localhost:8085/api/lecturas -H "Content-Type: application/json" -d '{"sensorId":"sensor-C","temperatura":26.0,"unidad":"C","secuencia":2}'
curl http://localhost:8086/api/monitor/alertas   # MEDIA: ALZA_BRUSCA subió 6.0 C
```

Lecturas y alertas se guardan **solo en memoria** (hasta 50 de cada una).

## 7. Experimentos del documento S13

- **Monitor detenido:** detenga el monitor con `Ctrl+C`, publique dos lecturas,
  vuelva a arrancarlo y verifique que el grupo retoma desde los offsets
  confirmados.
- **Validación:** `secuencia = -5` debe responder `400`.
- **Segundo grupo:** arranque otro monitor con
  `GRUPO_KAFKA=grupo-auditoria-s13` y `SERVER_PORT=8087`; comprueba consumo
  independiente.
- **Error controlado / DLT:** con productor y monitor activos ejecute
  `kafka-lab/scripts/09_probar_dlt.ps1`. El monitor reintenta dos veces y deja
  el registro en `sensores-temperatura.DLT`.

## 8. Administración por código

`kafka-admin-api` expone la administración con token de Entra ID y rol `Admin`
(igual que `rabbit-admin-api`):

| Método | Ruta | Qué hace |
|---|---|---|
| GET | `/api/admin/kafka/cluster` | Cluster ID, controller y brokers |
| GET | `/api/admin/kafka/topics` | Topics con particiones y réplicas |
| GET | `/api/admin/kafka/topics/{nombre}` | Detalle de un topic |
| POST | `/api/admin/kafka/topics` | Crear topic (`nombre`, `particiones`, `replicas`, `retencionMs`) |
| DELETE | `/api/admin/kafka/topics/{nombre}` | Eliminar topic no protegido |
| GET | `/api/admin/kafka/groups` | Consumer groups |
| GET | `/api/admin/kafka/groups/{grupo}/lag` | Offset actual, offset final y lag por partición |

Los topics `sensores-temperatura`, `sensores-alertas`, sus DLT y
`__consumer_offsets` están protegidos: se pueden consultar, pero no eliminar.

```powershell
cd kafka-admin-api
./mvnw spring-boot:run
```

## 9. Cluster de 3 ZooKeeper + 3 brokers y RF3 (EP6)

La pauta EP6 pide tres nodos ZooKeeper y tres brokers. `compose.cluster.yaml`
levanta esa topología con Confluent Platform 7.6 (Kafka 3.6), porque Apache
Kafka 4.x ya no soporta ZooKeeper. El laboratorio de un broker de S12 sigue en
KRaft porque es preparatorio.

| Nodo | Rol |
|---|---|
| `zookeeper1..3` | Ensemble que guarda la metadata: brokers (`/brokers/ids`), controller (`/controller`) y configuración de topics. Tolera la caída de 1 nodo |
| `kafka1..3` | Brokers que guardan las particiones y sus réplicas. Uno de ellos es el controller |

No levantar junto con el compose de un broker: comparten el puerto 9092.

```powershell
docker compose -f kafka-lab/compose.cluster.yaml up -d
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/07_crear_topicos_cluster.ps1
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/08_verificar_cluster.ps1
```

Resultado esperado:

- Ensemble ZooKeeper con 1 `leader` y 2 `follower`.
- `/brokers/ids` = `[1, 2, 3]` y un `brokerid` en `/controller`.
- Los dos topics con `Partitions: 3` y `ReplicationFactor: 3`.
- `__consumer_offsets` y `__transaction_state` con RF3.
- Sin particiones sub-replicadas en operación normal.

Para apuntar productor, monitor y administrador al cluster, arránquelos con el
perfil `cluster` (tres brokers como bootstrap y RF3):

```powershell
cd ms-productor-sensores && ./mvnw spring-boot:run -Dspring-boot.run.profiles=cluster
cd ms-monitor-sensores   && ./mvnw spring-boot:run -Dspring-boot.run.profiles=cluster
cd kafka-admin-api       && ./mvnw spring-boot:run -Dspring-boot.run.profiles=cluster
```

Como el perfil declara los topics y las DLT con RF3, el orden de arranque no
importa: si los servicios arrancan antes que el script 07, los topics igual
quedan con 3 réplicas. Los scripts de verificación y DLT aceptan `-Cluster`:

```powershell
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/06_verificar_grupo.ps1 -Grupo grupo-monitor-s13 -Cluster
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/09_probar_dlt.ps1 -Cluster
```

Las variables disponibles están en `kafka-lab/.env.example`.

UI del cluster: http://localhost:8091

### Después de un apagado brusco

Si Docker Desktop o el PC se apagaron con el clúster arriba, ZooKeeper conserva
unos segundos el registro `/brokers/ids/N` de la sesión anterior y los brokers
abortan al primer arranque (`node already exists`). Tienen `restart:
unless-stopped`, así que vuelven a intentar solos y entran en unos 40 s.
`up -d --wait` puede informar `unhealthy` durante ese reinicio: espere y
confirme con el script 08.

### Tolerancia a fallas (probado)

Con `zookeeper3` (leader) y `kafka3` detenidos a la vez:

- ZooKeeper elige otro leader entre los dos nodos restantes (mayoría 2 de 3).
- Las particiones que lideraba `kafka3` pasan a otro broker y el ISR baja a 2,
  que sigue cumpliendo `min.insync.replicas=2`: se puede publicar y consumir.
- Al volver a iniciarlos, las réplicas se ponen al día y no quedan particiones
  sub-replicadas.

```powershell
docker compose -f kafka-lab/compose.cluster.yaml stop zookeeper3 kafka3
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/08_verificar_cluster.ps1
docker compose -f kafka-lab/compose.cluster.yaml start zookeeper3 kafka3
```

`/brokers/ids` puede seguir mostrando el broker detenido durante unos segundos:
su registro en ZooKeeper es efímero y desaparece al vencer la sesión (~18 s).
Con dos brokers caídos el ISR quedaría en 1 y los productores con `acks=all`
serían rechazados (`NotEnoughReplicas`), que es la protección esperada.

## 10. Pruebas

No necesitan broker ni Docker:

```powershell
cd ms-productor-sensores && ./mvnw test
cd ms-monitor-sensores   && ./mvnw test
cd kafka-admin-api       && ./mvnw test
```

## 11. Fallas frecuentes

| Síntoma | Acción |
|---|---|
| Docker no responde | Abrir Docker Desktop y comprobar `docker info` |
| Puerto 9092 ocupado | Cerrar el otro compose de Kafka o el broker anterior |
| Puerto 8090/8091 ocupado | Cambiar el puerto publicado en el compose correspondiente |
| UI sin datos | Confirmar topics por CLI y bootstrap `kafka:9093` |
| Sin lecturas al repetir consumo | Usar un grupo nuevo; el grupo anterior ya confirmó offsets |
| `ReplicationFactor: 1` | Correcto en el laboratorio de un broker. En el cluster, los servicios deben usar el perfil `cluster`; si un topic quedó con RF1, elimínelo y vuelva a crearlo |
| Monitor no ve lecturas | Revisar que productor y monitor usen el mismo `KAFKA_BOOTSTRAP_SERVERS` |
| Mensaje en `.DLT` | Revisar el log del monitor; el error se agotó tras 3 intentos |

## 12. Advertencias

- La lista del monitor vive en memoria y se pierde al reiniciar. Los eventos
  pueden seguir disponibles en Kafka según la retención (24 h por defecto).
- El laboratorio local usa `PLAINTEXT` y RF1: no es la topología final de EP5/EP6.
- Las credenciales y tokens de Entra no se incluyen en este documento.
- El cluster de 3 ZooKeeper + 3 brokers es local; permite demostrar RF3 y tolerancia a fallas,
  pero no reemplaza una arquitectura de producción.
