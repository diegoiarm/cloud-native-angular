# Guía para la defensa individual

En la defensa pueden pedir abrir el código y explicar una clase, una
configuración o algo que se vea en Kafbat UI. Esta guía dice dónde está cada
cosa y responde en corto las preguntas de los documentos 02 y 05.

## Mapa del código Kafka

| Qué buscan | Dónde está |
|---|---|
| Conexión del productor a Kafka | [`ms-productor-sensores/src/main/resources/application.yml`](../ms-productor-sensores/src/main/resources/application.yml) (`bootstrap-servers`, serializers, `acks: all`, `enable.idempotence`) |
| Conexión al clúster de 3 brokers | `application-cluster.yml` de cada servicio (bootstrap `9092,9093,9094` y RF3) |
| Creación de tópicos por código | [`KafkaTopicsConfig.java`](../ms-productor-sensores/src/main/java/cl/duoc/dsy1107/productorsensores/config/KafkaTopicsConfig.java) (`TopicBuilder`: particiones, réplicas, retención) |
| Clase productora (`KafkaTemplate`) | [`LecturaService.java`](../ms-productor-sensores/src/main/java/cl/duoc/dsy1107/productorsensores/service/LecturaService.java) — `kafkaTemplate.send(topico, sensorId, lectura)` |
| Endpoint REST del productor | [`LecturaController.java`](../ms-productor-sensores/src/main/java/cl/duoc/dsy1107/productorsensores/controller/LecturaController.java) (`POST /api/lecturas`, `@Valid`) |
| DTO del evento | [`LecturaSensor.java`](../ms-monitor-sensores/src/main/java/cl/duoc/dsy1107/sensores/dto/LecturaSensor.java) y [`AlertaSensor.java`](../ms-monitor-sensores/src/main/java/cl/duoc/dsy1107/sensores/dto/AlertaSensor.java) |
| Configuración del consumidor | [`ms-monitor-sensores/src/main/resources/application.yml`](../ms-monitor-sensores/src/main/resources/application.yml) (`group-id`, `ErrorHandlingDeserializer`, `trusted.packages`, `auto-offset-reset`, `enable-auto-commit: false`) |
| `@KafkaListener` de lecturas | [`TemperaturaConsumer.java`](../ms-monitor-sensores/src/main/java/cl/duoc/dsy1107/monitorsensores/consumer/TemperaturaConsumer.java) |
| `@KafkaListener` de alertas | [`AlertaConsumer.java`](../ms-monitor-sensores/src/main/java/cl/duoc/dsy1107/monitorsensores/consumer/AlertaConsumer.java) |
| Reglas de reacción | [`ReglasReaccion.java`](../ms-monitor-sensores/src/main/java/cl/duoc/dsy1107/monitorsensores/service/ReglasReaccion.java) |
| Publicación de la alerta | [`PublicadorAlertas.java`](../ms-monitor-sensores/src/main/java/cl/duoc/dsy1107/monitorsensores/service/PublicadorAlertas.java) |
| Reintentos y DLT | [`KafkaConsumerConfig.java`](../ms-monitor-sensores/src/main/java/cl/duoc/dsy1107/monitorsensores/config/KafkaConsumerConfig.java) (`DefaultErrorHandler`, `FixedBackOff(1000, 2)`, `destinoDlt`) |
| Tiempo real (SSE) | [`EmisorEventos.java`](../ms-monitor-sensores/src/main/java/cl/duoc/dsy1107/monitorsensores/service/EmisorEventos.java) y `MonitorController.stream()` |
| Administración por código | [`KafkaAdminService.java`](../kafka-admin-api/src/main/java/cl/duoc/dsy1107/kafkaadminapi/service/KafkaAdminService.java) (reglas) y [`KafkaAdminAdapter.java`](../kafka-admin-api/src/main/java/cl/duoc/dsy1107/kafkaadminapi/service/KafkaAdminAdapter.java) (`AdminClient`) |
| Seguridad del administrador | [`SecurityConfig.java`](../kafka-admin-api/src/main/java/cl/duoc/dsy1107/kafkaadminapi/config/SecurityConfig.java) (JWT de Entra, rol `Admin`) |
| Infraestructura | [`kafka-lab/compose.cluster.yaml`](../kafka-lab/compose.cluster.yaml) (3 ZooKeeper, 3 brokers, Kafbat UI) |
| Scripts de operación | [`kafka-lab/scripts/`](../kafka-lab/scripts/) — 07 crear tópicos, 08 verificar clúster, 06 lag, 09 DLT |

## Preguntas probables

### Arquitectura

**¿Por qué 3 ZooKeeper y 3 brokers?**
ZooKeeper decide por mayoría: con 3 nodos sigue funcionando si cae 1. Guarda los
brokers vivos (`/brokers/ids`), quién es el controller (`/controller`) y la
configuración de los tópicos. Los 3 brokers permiten 3 réplicas de cada
partición.

**¿Qué diferencia hay entre partición y réplica?**
La partición divide el tópico para procesar en paralelo (3 particiones = hasta 3
consumidores del mismo grupo). La réplica es una copia de una partición en otro
broker. Con RF3 cada partición tiene un líder y 2 seguidores.

**¿Qué pasa si se cae un broker?**
Las particiones que lideraba pasan a otro broker que estaba en el ISR. Con
`min.insync.replicas=2` y `acks=all` se sigue publicando con 2 réplicas. Lo
probamos: se siguió publicando y consumiendo, y al volver no quedaron
particiones sub-replicadas.

**¿Por qué usan Kafka si ya tenían RabbitMQ?**
Resuelven problemas distintos. RabbitMQ reparte tareas de cada pedido y el
mensaje se borra con el ACK. Kafka guarda un flujo continuo de lecturas que se
puede releer, que varios grupos consumen por separado y que mantiene el orden
por sensor. Ver [`caso-de-uso.md`](caso-de-uso.md).

### Publicación y eventos

**¿Cómo se publica un DTO y cómo se elige la clave?**
`LecturaService` llama a `kafkaTemplate.send(topico, lectura.sensorId(), lectura)`.
El `JsonSerializer` convierte el record a JSON. La clave es `sensorId` porque
así todas las lecturas de un sensor caen en la misma partición y llegan en orden.

**¿Qué significa `acks=all`?**
El broker confirma recién cuando todas las réplicas del ISR escribieron el
mensaje. Junto con `min.insync.replicas=2`, una lectura confirmada no se pierde
aunque se caiga un broker.

**¿Para qué sirve la idempotencia del productor?**
Si un envío se reintenta por un corte de red, el broker detecta el duplicado y
no lo escribe dos veces.

### Consumo y errores

**¿Dónde está el consumidor y a qué grupo pertenece?**
`TemperaturaConsumer` y `AlertaConsumer`, con `@KafkaListener` y
`groupId = grupo-monitor-s13`.

**¿Qué hace el consumidor con cada evento? (procesamiento reactivo)**
Evalúa las reglas en `ReglasReaccion`. Si la temperatura llega a 30 °C o sube
5 °C o más respecto de la lectura anterior del mismo sensor, publica una alerta
en `sensores-alertas` y la envía en vivo al navegador por SSE. El productor no
decide nada: solo ingiere.

**¿Por qué la regla de alza funciona?**
Necesita la lectura anterior del mismo sensor. Como la clave es `sensorId`,
todas sus lecturas están en la misma partición, la consume un solo miembro del
grupo y llegan en orden.

**¿Qué pasa si falla el procesamiento?**
El `DefaultErrorHandler` hace 1 intento + 2 reintentos con 1 s de espera. Si
sigue fallando, el `DeadLetterPublishingRecoverer` lo publica en
`sensores-temperatura.DLT`, en la misma partición, y se confirma el offset para
no bloquear la partición. Se demuestra con `sensor-fallo` (script 09).

**¿Y si llega un JSON inválido?**
El `ErrorHandlingDeserializer` atrapa el error de deserialización, el consumer
recibe `null` y lanza una excepción que sigue el mismo camino hacia la DLT.

**¿Qué pasa si falla la publicación de la alerta?**
`PublicadorAlertas` espera la confirmación. Si no llega, lanza una excepción
**antes** de guardar la lectura, así el reintento no la duplica.

### Operación y diagnóstico

**¿Qué es el offset?**
La posición de un mensaje dentro de una partición. El grupo guarda el último
offset procesado de cada partición en `__consumer_offsets`.

**¿Qué es el lag?**
`LOG-END-OFFSET - CURRENT-OFFSET`: cuántos mensajes faltan por procesar. Lag 0
significa que el consumidor está al día. Si se detiene el monitor y se publican
lecturas, el lag sube; al volver, retoma desde el último offset confirmado.

**¿Qué muestra Kafbat UI?**
- Brokers: los 3 brokers y el controller.
- Tópico: particiones, RF, ISR y URP (sub-replicadas).
- Mensajes: con su clave, partición y offset.
- Consumer groups: miembros, particiones asignadas y lag.

**¿Cómo se administra Kafka por código?**
`kafka-admin-api` usa `AdminClient` para listar y crear tópicos, ver grupos y
calcular el lag. Exige el JWT con rol `Admin` (sin token responde 401). Los
tópicos del sistema no se pueden borrar. Además están los scripts 07 y 08.

**¿Por qué esa retención?**
24 h en los tópicos de datos alcanzan para reprocesar o auditar el día. 7 días
en la DLT dan tiempo para revisar los fallos.

**¿Qué es un consumer group y qué pasa con un segundo grupo?**
Los miembros de un mismo grupo se reparten las particiones. Un grupo distinto
(por ejemplo `grupo-auditoria-s13`) lee todos los mensajes otra vez con sus
propios offsets, sin afectar al primero.
