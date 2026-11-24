# Tópicos, colas y DLQ

## Kafka (clúster de 3 ZooKeeper + 3 brokers)

| Tópico | Particiones | RF | Retención | Clave | Quién publica | Quién consume |
|---|---|---|---|---|---|---|
| `sensores-temperatura` | 3 | 3 | 24 h | `sensorId` | `ms-productor-sensores` | `TemperaturaConsumer` |
| `sensores-alertas` | 3 | 3 | 24 h | `sensorId` | `ms-monitor-sensores` (`PublicadorAlertas`) | `AlertaConsumer` |
| `sensores-temperatura.DLT` | 3 | 3 | 7 días | `sensorId` | `DeadLetterPublishingRecoverer` | revisión manual (Kafbat UI o script 09) |
| `sensores-alertas.DLT` | 3 | 3 | 7 días | `sensorId` | `DeadLetterPublishingRecoverer` | revisión manual |

Configuración común: `min.insync.replicas=2`, `auto.create.topics.enable=false`,
`__consumer_offsets` y `__transaction_state` con RF3.

**Consumer group:** `grupo-monitor-s13`, con dos listeners (uno por tópico),
`auto-offset-reset: earliest`, `enable-auto-commit: false` y `ack-mode: record`.
Otro grupo (por ejemplo `grupo-auditoria-s13`) lee las mismas lecturas por su
cuenta, con sus propios offsets.

**Dónde se crean:**
- Por código al arrancar: `KafkaTopicsConfig` (productor) y
  `KafkaConsumerConfig` (DLT del monitor), con RF3 en el perfil `cluster`.
- Por script: `kafka-lab/scripts/07_crear_topicos_cluster.ps1`.
- Por REST: `POST /api/admin/kafka/topics` en `kafka-admin-api`. Los tópicos
  del sistema están protegidos contra borrado.

### Contratos (DTO)

```json
// LecturaSensor → sensores-temperatura
{ "sensorId": "sensor-B", "temperatura": 31.2, "unidad": "C", "secuencia": 4 }

// AlertaSensor → sensores-alertas
{ "sensorId": "sensor-B", "temperatura": 31.2, "unidad": "C", "secuencia": 4,
  "motivo": "UMBRAL: 31.2 C supera 30.0 C; ALZA_BRUSCA: subió 9.1 C desde 22.1 C",
  "severidad": "ALTA", "generadaEn": "2026-11-23T03:44:58.901Z" }
```

Se serializan con `JsonSerializer` y la cabecera de tipo. El monitor los lee con
`ErrorHandlingDeserializer` + `JsonDeserializer` (`trusted.packages:
cl.duoc.dsy1107.*`). Por eso `LecturaSensor` vive en el mismo paquete
`cl.duoc.dsy1107.sensores.dto` en ambos servicios. `LecturaSensor` se valida con
Bean Validation (`secuencia` negativa → 400).

## RabbitMQ (pedidos, clúster de 2 nodos)

| Exchange | Tipo | Routing key | Cola | Consumer |
|---|---|---|---|---|
| `pedidos360.direct.exchange` | direct | `pedido.aceptado` | `pedidos360.cocina.queue` | `CocinaConsumer` |
| `pedidos360.direct.exchange` | direct | `pedido.despachado` | `pedidos360.documentos.queue` | `DocumentosConsumer` |
| `pedidos360.topic.exchange` | topic | `pedido.#` | `pedidos360.notificaciones.queue` | `NotificacionesConsumer` |
| `pedidos360.dlx.exchange` | direct | `<cola>.dlq` | `pedidos360.<dominio>.dlq` | revisión y purga |

- Cada cola principal tiene su `pedidos360.<dominio>.retry`, que retiene el
  mensaje 5 s y lo devuelve a la principal.
- ACK manual: falla en los intentos 1 y 2 → `.retry`; falla en el 3 →
  `basicNack(requeue=false)` → DLX → `.dlq`.
- Política de retención del clúster (script `infra/03_Politica_Retencion_Windows.ps1`):
  TTL 120 s y máximo 200 mensajes.
- Administración por REST: `rabbit-admin-api` (rol `Admin`). La topología
  `pedidos360.*` solo se puede purgar, no modificar.

## DLT de Kafka frente a DLQ de RabbitMQ

No son lo mismo y no se deben confundir en la defensa:

- **DLQ (RabbitMQ):** una cola. El mensaje llega por el dead-letter exchange
  cuando el consumer hace NACK sin reencolar. Se consume o se purga.
- **DLT (Kafka):** un tópico más, con retención propia (7 días). Lo escribe el
  `DeadLetterPublishingRecoverer` en la misma partición del original, con
  cabeceras que indican la excepción y el offset de origen. El offset original
  se confirma para que la partición no se bloquee.
