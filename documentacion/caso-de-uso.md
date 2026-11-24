# Caso de uso y decisiones técnicas

## El problema

Pedidos360 gestiona pedidos de comida: el cliente pide, cocina acepta y prepara,
y el pedido se despacha. La cocina tiene cámaras de frío y equipos con sensores
de temperatura que envían lecturas continuamente. Si una cámara se calienta, la
comida puede echarse a perder y los pedidos en preparación quedan en riesgo, así
que hay que detectarlo en el momento.

Son dos tipos de comunicación distintos, y por eso el sistema usa dos brokers.

| | Pedidos (RabbitMQ) | Sensores de cocina (Kafka) |
|---|---|---|
| Qué viaja | Un evento por cambio de estado de un pedido | Un flujo continuo de lecturas |
| Volumen | Bajo, ligado a cada pedido | Alto y constante, aunque no haya pedidos |
| Qué se espera | Que cada tarea se haga **una vez** (cocina, documentos, notificación) | Que **varios** procesos lean el mismo flujo y reaccionen |
| Después de procesar | El mensaje se elimina con el ACK | El evento se **conserva** (retención 24 h) y se puede volver a leer |
| Orden | No importa entre pedidos | Importa **por sensor** (para detectar subidas) |
| Fallas | Reintento con cola `.retry` y DLQ por cola | Reintentos y DLT por tópico |

**Kafka no reemplaza a RabbitMQ.** RabbitMQ sigue repartiendo las tareas de cada
pedido entre colas con enrutamiento por exchanges. Kafka agrega el streaming de
sensores, donde lo valioso es conservar el historial, leerlo con varios grupos
independientes y mantener el orden por sensor.

## Qué hace el flujo Kafka

1. Cada sensor envía su lectura a `ms-productor-sensores` (`POST /api/lecturas`).
2. El productor la publica en `sensores-temperatura` con clave `sensorId` y
   responde `202` con partición y offset. No toma decisiones: solo ingiere.
3. `ms-monitor-sensores` consume cada lectura y **reacciona**:
   - `UMBRAL`: 30 °C o más → alerta ALTA (la cámara está caliente).
   - `ALZA_BRUSCA`: sube 5 °C o más respecto de la lectura anterior del mismo
     sensor → alerta MEDIA (puerta abierta o falla del compresor, antes de
     llegar al umbral).
4. La alerta se publica en `sensores-alertas` y se muestra en vivo en el
   navegador (SSE), sin recargar.
5. Si una lectura no se puede procesar, se reintenta y termina en
   `sensores-temperatura.DLT` para revisarla sin bloquear el resto.

## Decisiones y su porqué

| Decisión | Por qué |
|---|---|
| **3 ZooKeeper + 3 brokers** | Lo pide la pauta EP6. El ensemble de 3 tolera la caída de 1 nodo (necesita mayoría 2 de 3), y 3 brokers permiten RF3. Se usa Confluent 7.6 (Kafka 3.6) porque Apache Kafka 4.x ya no soporta ZooKeeper. |
| **3 particiones** | Permiten hasta 3 consumidores en paralelo dentro de un grupo, y con 3 brokers cada uno lidera una partición, repartiendo la carga. |
| **RF3 + `min.insync.replicas=2` + `acks=all`** | Una lectura confirmada está en al menos 2 brokers. Si cae 1 broker se sigue publicando y no se pierde nada. Si caen 2, el productor recibe error en vez de perder datos en silencio. Probado: con un broker y el leader de ZooKeeper caídos se siguió publicando y consumiendo. |
| **Clave `sensorId`** | Todas las lecturas de un sensor van a la misma partición y se consumen en orden. Sin eso no se podría calcular el alza respecto de la lectura anterior. |
| **Productor idempotente** | Si un envío se reintenta por un corte de red, el broker no duplica la lectura. |
| **La reacción ocurre en el consumidor** | La ingesta queda simple y rápida. Las reglas se pueden cambiar sin tocar el productor, y otros consumidores (por ejemplo, auditoría) pueden leer las mismas lecturas con su propio grupo. |
| **Dos tópicos** | `sensores-temperatura` guarda todo; `sensores-alertas` solo lo que requiere acción. Quien atiende alertas no tiene que filtrar miles de lecturas normales. |
| **Retención 24 h (datos) y 7 días (DLT)** | Un día basta para reprocesar o auditar las lecturas recientes. La DLT se guarda una semana para dar tiempo a revisar los fallos. |
| **Reintentos 1 + 2 con 1 s y DLT** | Un error pasajero se resuelve solo; uno permanente no bloquea la partición y queda guardado aparte. Si falla la publicación de la alerta, la lectura no se guarda antes de reintentar, así no se duplica. |
| **`auto.create.topics=false`** | Los tópicos se crean de forma explícita (código o script 07) con sus particiones, RF y retención. Un nombre mal escrito falla en vez de crear un tópico con valores por defecto. |
| **Commit manual por registro** | El offset se confirma recién cuando la lectura se procesó, así un reinicio retoma sin perder lecturas. |
| **Administración por código** | `kafka-admin-api` lista tópicos, grupos y lag por REST, protegido con el mismo JWT de Entra ID y rol `Admin` que `rabbit-admin-api`. Los tópicos del sistema están protegidos contra borrado. |

## Límites conocidos

- Las listas del monitor y el estado de la regla de alza viven en memoria: se
  pierden al reiniciar. Los eventos siguen en Kafka según la retención.
- Tras un rebalanceo, la primera lectura de cada sensor no evalúa el alza porque
  el monitor no conoce la anterior. El umbral sí se evalúa siempre.
- El clúster Kafka corre en Docker local con `PLAINTEXT`. Sirve para demostrar
  la topología, RF3 y la tolerancia a fallas, pero no es una instalación de
  producción. Pedidos, RabbitMQ y la base de datos sí están desplegados en AWS.
