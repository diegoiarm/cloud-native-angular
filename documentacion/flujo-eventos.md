# Flujos de eventos

GitHub dibuja estos diagramas automáticamente. La vista general está en
[`arquitectura.png`](arquitectura.png).

## 1. Lectura normal (Kafka)

```mermaid
sequenceDiagram
    participant S as Sensor
    participant P as ms-productor-sensores
    participant T as sensores-temperatura
    participant M as ms-monitor-sensores
    participant N as Navegador (SSE)

    S->>P: POST /api/lecturas {sensorId, temperatura, unidad, secuencia}
    P->>T: send(key = sensorId, LecturaSensor) acks=all
    T-->>P: partición y offset
    P-->>S: 202 {sensorId, particion, offset}
    T->>M: @KafkaListener TemperaturaConsumer
    M->>M: ReglasReaccion.evaluar(): sin alerta
    M->>N: event: lectura
    M->>T: commit del offset
```

## 2. Lectura que dispara una alerta (procesamiento reactivo)

```mermaid
sequenceDiagram
    participant P as ms-productor-sensores
    participant T as sensores-temperatura
    participant M as TemperaturaConsumer
    participant A as sensores-alertas
    participant C as AlertaConsumer
    participant N as Navegador (SSE)

    P->>T: sensor-B 31.2 °C (antes 22.1 °C)
    T->>M: consume
    M->>M: UMBRAL (≥ 30) y ALZA_BRUSCA (+9.1) → ALTA
    M->>A: send(key = sensor-B, AlertaSensor) y espera confirmación
    M->>N: event: lectura
    A->>C: consume
    C->>N: event: alerta (ALTA)
```

Con `sensor-C` de 20.0 a 26.0 °C solo se cumple `ALZA_BRUSCA` y la alerta sale
con severidad MEDIA.

## 3. Error de procesamiento: reintentos y DLT

```mermaid
sequenceDiagram
    participant T as sensores-temperatura
    participant M as TemperaturaConsumer
    participant H as DefaultErrorHandler
    participant D as sensores-temperatura.DLT

    T->>M: lectura de sensor-fallo
    M--xH: excepción (intento 1)
    H->>M: reintento tras 1 s (intento 2)
    M--xH: excepción
    H->>M: reintento tras 1 s (intento 3)
    M--xH: excepción
    H->>D: DeadLetterPublishingRecoverer (misma partición)
    H->>T: commit: la partición sigue avanzando
```

Un mensaje que no se puede deserializar sigue el mismo camino gracias a
`ErrorHandlingDeserializer`. Si lo que falla es la publicación de la alerta, la
lectura no se guarda antes de reintentar, así no queda duplicada.

## 4. Pedido aceptado (RabbitMQ, flujo que se mantiene)

```mermaid
sequenceDiagram
    participant U as Angular (Operador)
    participant G as API Gateway (JWT)
    participant PA as pedidos-api
    participant CA as catalogo-api
    participant X as pedidos360.direct / topic
    participant Q as colas cocina / notificaciones

    U->>G: PUT /api/orders/{id}/status ACEPTADO
    G->>PA: token validado
    PA->>CA: descontar stock (reenvía el token)
    CA-->>PA: OK
    PA->>X: evento pedido.aceptado
    X->>Q: binding pedido.aceptado → cocina; pedido.# → notificaciones
    Q->>Q: consumer con ACK manual; si falla: .retry (5 s) ×3 → .dlq
```

## Comparación de manejo de errores

| | RabbitMQ (pedidos) | Kafka (sensores) |
|---|---|---|
| Reintento | Copia en `<dominio>.retry` con TTL 5 s y cabecera `x-intentos` | `DefaultErrorHandler` con `FixedBackOff(1000, 2)` |
| Intentos | 3 | 3 (1 + 2 reintentos) |
| Destino final | `pedidos360.<dominio>.dlq` vía DLX | `<topic>.DLT`, misma partición |
| Qué pasa con el original | `basicNack(requeue=false)` | Se confirma el offset y la partición sigue |
