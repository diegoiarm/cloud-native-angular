# Guion del video EP6 (adaptado a nuestra implementación)

Sigue los 10 tramos del documento `DSY1107-002-EP5-EP6-GUION-VIDEO` con lo que
de verdad tiene el proyecto. Duración objetivo: **unos 9 minutos** (5 a 10
permitidos). Se graba la pantalla con narración audible; no es obligatorio
mostrar la cara.

## Antes de grabar (15 minutos antes)

1. Abrir Docker Desktop y levantar el clúster Kafka:
   ```powershell
   docker compose -f kafka-lab/compose.cluster.yaml up -d
   ```
   Si Docker se había cerrado de golpe, los brokers se reinician solos y tardan
   unos 40 s. Esperar y confirmar con:
   ```powershell
   powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/08_verificar_cluster.ps1
   ```
2. Levantar RabbitMQ para el tramo 7 (no choca con Kafka):
   ```powershell
   docker compose -f infra/docker-compose.cluster.yml up -d
   ```
3. Arrancar los servicios, cada uno en su terminal:
   ```powershell
   cd ms-productor-sensores; ./mvnw spring-boot:run "-Dspring-boot.run.profiles=cluster"
   cd ms-monitor-sensores;   ./mvnw spring-boot:run "-Dspring-boot.run.profiles=cluster"
   cd kafka-admin-api;       ./mvnw spring-boot:run "-Dspring-boot.run.profiles=cluster"
   cd pedidos-api;           ./mvnw spring-boot:run "-Dspring-boot.run.profiles=local"
   ```
   Si el monitor se reinicia, tarda unos 40 s en volver a recibir lecturas
   (espera a que venza la sesión anterior en el grupo).
4. Abrir en pestañas: Kafbat UI http://localhost:8091, monitor
   http://localhost:8086/, RabbitMQ http://localhost:15672 y el repo en VS Code.
5. Subir el tamaño de letra de VS Code y de la terminal (zoom) para que se lea.
6. **No mostrar** contraseñas, archivos `.env`, tokens JWT completos ni la clave
   del RDS. Los IDs de tenant y de app de Entra no son secretos.

## Tramos

| # | Tiempo | Qué se muestra |
|---|---|---|
| 1 | 0:00–0:25 | Presentación |
| 2 | 0:25–1:10 | Arquitectura |
| 3 | 1:10–2:10 | Infraestructura y Kafbat UI |
| 4 | 2:10–3:05 | Tópicos, particiones y réplicas |
| 5 | 3:05–4:05 | Código: configuración, productor y consumidor |
| 6 | 4:05–5:45 | Evento de punta a punta y reacción |
| 7 | 5:45–6:40 | Errores (DLT) y continuidad de RabbitMQ |
| 8 | 6:40–7:45 | Consumer groups, offsets, lag y retención |
| 9 | 7:45–8:25 | Administración de Kafka |
| 10 | 8:25–9:10 | Casos de uso, decisiones y cierre |

### 1 · Presentación (0:00–0:25)

**Pantalla:** portada simple con sección 002, grupo, integrantes y "Pedidos360".

**Narración:** "Somos el grupo [N] de DSY1107, sección 002: [nombres].
Presentamos Pedidos360, un sistema de pedidos de comida, y cómo incorporamos
Kafka para monitorear en tiempo real la temperatura de la cocina, manteniendo
RabbitMQ para las tareas de cada pedido."

### 2 · Arquitectura (0:25–1:10)

**Pantalla:** [`documentacion/arquitectura.png`](../documentacion/arquitectura.png).

**Narración:** "El frontend Angular inicia sesión con Entra ID y llama al API
Gateway, que valida el JWT. pedidos-api y catalogo-api corren en EC2 con
PostgreSQL en RDS. Cuando un pedido cambia de estado, pedidos-api publica en
RabbitMQ y las colas de cocina, documentos y notificaciones hacen esas tareas.
Kafka se suma para el flujo continuo de lecturas de los sensores de cocina: el
productor las publica y el monitor reacciona generando alertas."

**Comprobar:** decir quién publica (productor y monitor), quién consume (monitor)
y qué sigue haciendo RabbitMQ (tareas por pedido).

### 3 · Infraestructura (1:10–2:10)

**Pantalla:**
1. `kafka-lab/compose.cluster.yaml`: los 3 servicios `zookeeper` y los 3 `kafka`.
2. Terminal: `docker compose -f kafka-lab/compose.cluster.yaml ps` (7 contenedores healthy).
3. Terminal: script 08, que muestra 1 leader y 2 followers, `[1, 2, 3]` y el controller.
4. Kafbat UI → Brokers.

**Narración:** "Levantamos la topología de la pauta: tres nodos ZooKeeper y tres
brokers. ZooKeeper guarda qué brokers están vivos y elige el controller; con
tres nodos sigue funcionando si cae uno. Usamos Confluent 7.6 porque Kafka 4 ya
no soporta ZooKeeper. En Kafbat UI se ven los tres brokers."

### 4 · Tópicos (2:10–3:05)

**Pantalla:** Kafbat UI → Topics → `sensores-temperatura` (Partitions 3,
Replication Factor 3, ISR 9 of 9, URP 0) y luego `sensores-alertas`. Pestaña
Settings: `retention.ms` y `min.insync.replicas`.

**Narración:** "Tenemos dos tópicos: sensores-temperatura con todas las
lecturas y sensores-alertas solo con lo que requiere acción. Cada uno tiene 3
particiones, para que hasta 3 consumidores trabajen en paralelo, y 3 réplicas:
cada partición tiene un líder en un broker distinto y dos copias. Con
min.insync.replicas en 2, una lectura confirmada está al menos en dos brokers.
La retención es de 24 horas, y la DLT guarda los errores 7 días."

### 5 · Código (3:05–4:05)

**Pantalla (VS Code), en este orden:**
1. `ms-productor-sensores/.../application-cluster.yml` y `application.yml`:
   bootstrap de los 3 brokers, `JsonSerializer`, `acks: all`.
2. `LecturaService.java`: `kafkaTemplate.send(topico, lectura.sensorId(), lectura)`.
3. `LecturaSensor.java`: el DTO.
4. `ms-monitor-sensores/.../application.yml`: `group-id`, `ErrorHandlingDeserializer`.
5. `TemperaturaConsumer.java`: `@KafkaListener` y la llamada a `ReglasReaccion`.

**Narración:** "El productor se conecta a los tres brokers y serializa el DTO
LecturaSensor a JSON. La clave es el sensorId, así las lecturas de un sensor van
a la misma partición y llegan en orden. El monitor pertenece al grupo
grupo-monitor-s13; su listener recibe cada lectura, evalúa las reglas y, si
corresponde, publica la alerta. La mensajería está separada de las reglas del
dominio."

### 6 · Evento de punta a punta (4:05–5:45)

**Pantalla:** dividida entre la página del monitor (http://localhost:8086/) y la
terminal.

```powershell
powershell -ExecutionPolicy Bypass -File kafka-lab/scripts/05_enviar_6_lecturas.ps1
```

Luego el caso de alza brusca:

```powershell
curl.exe -X POST http://localhost:8085/api/lecturas -H "Content-Type: application/json" -d '{\"sensorId\":\"sensor-C\",\"temperatura\":20.0,\"unidad\":\"C\",\"secuencia\":1}'
curl.exe -X POST http://localhost:8085/api/lecturas -H "Content-Type: application/json" -d '{\"sensorId\":\"sensor-C\",\"temperatura\":26.0,\"unidad\":\"C\",\"secuencia\":2}'
```

Mostrar la respuesta `202` con partición y offset, las lecturas y alertas
apareciendo solas en la página, la misma lectura en Kafbat UI → Messages (con la
misma partición y offset) y el log `[REACCION]` del monitor.

**Narración:** "Enviamos seis lecturas. El productor responde con la partición y
el offset donde quedó cada una: las de sensor-A van siempre a la misma
partición. El monitor reacciona: sensor-B sube de 22 a 31 grados, supera el
umbral y además sube más de 5 grados, así que publica una alerta ALTA. Ahora
sensor-C sube de 20 a 26: no llega al umbral, pero sí es un alza brusca, y sale
una alerta MEDIA. Esto funciona porque el monitor recuerda la lectura anterior
de cada sensor, y eso es correcto gracias a la clave. La página recibe todo en
vivo por Server-Sent Events, sin recargar."

**Comprobar:** relacionar un evento por su sensorId, partición y offset en la
respuesta, la UI y la página.

### 7 · Errores y continuidad de RabbitMQ (5:45–6:40)

**Pantalla:**
1. `KafkaConsumerConfig.java`: `DefaultErrorHandler`, `FixedBackOff(1000, 2)`, `destinoDlt`.
2. Terminal: `kafka-lab/scripts/09_probar_dlt.ps1 -Cluster`. Mostrar en el log
   del monitor los intentos 1/3, 2/3 y 3/3 y el mensaje en la DLT.
3. RabbitMQ: publicar con el endpoint de laboratorio y mostrar la DLQ en la
   Management UI o con el monitor de colas:
   ```powershell
   curl.exe -X POST http://localhost:8080/api/lab/pedidos/aceptar -H "Content-Type: application/json" -d '{\"pedidoId\":210,\"clienteEmail\":\"prueba@example.com\",\"falloCocina\":true}'
   python monitor/monitor_queues.py --una-vez
   ```

**Narración:** "Si una lectura no se puede procesar, Kafka la reintenta dos
veces con un segundo de espera y después la deja en sensores-temperatura.DLT,
en la misma partición, sin bloquear las demás. En RabbitMQ el flujo de pedidos
sigue igual: el mensaje pasa por la cola retry y después de tres intentos queda
en la DLQ de cocina. Son mecanismos distintos: la DLT es un tópico con
retención; la DLQ es una cola a la que se llega por el dead-letter exchange."

**Comprobar:** no confundir DLT con DLQ.

### 8 · Grupos, offsets y lag (6:40–7:45)

**Pantalla:** Kafbat UI → Consumers → `grupo-monitor-s13` (STABLE, 2 miembros,
6 particiones, lag 0) y el detalle por partición. Opcional: script 06.

Para mostrar lag distinto de cero: detener el monitor (Ctrl+C), enviar 2
lecturas, mostrar el lag en 2 en la UI, arrancar el monitor y mostrar que
vuelve a 0.

**Narración:** "El grupo grupo-monitor-s13 tiene dos miembros, uno por tópico, y
se reparte las 6 particiones. El offset actual es hasta dónde procesó; el log
end offset es lo último publicado; la diferencia es el lag. Con el monitor
detenido el lag sube a 2; al volver, retoma desde el último offset confirmado y
el lag vuelve a cero, sin perder lecturas."

### 9 · Administración (7:45–8:25)

**Pantalla:**
1. `kafka-lab/scripts/07_crear_topicos_cluster.ps1` (creación versionada con
   particiones, RF3 y retención).
2. `KafkaAdminController.java` / `KafkaAdminService.java`: endpoints de tópicos,
   grupos y lag.
3. Terminal: `curl.exe -i http://localhost:8084/api/admin/kafka/topics` → 401
   sin token.

**Narración:** "La administración está en código: el script 07 crea los
tópicos con sus parámetros, y kafka-admin-api lista y crea tópicos y calcula el
lag con AdminClient. Está protegido con el mismo JWT de Entra ID y rol Admin
que el administrador de RabbitMQ; sin token responde 401. Los tópicos del
sistema no se pueden borrar desde la API."

**Comprobar:** no hacer operaciones destructivas en la grabación.

### 10 · Cierre (8:25–9:10)

**Pantalla:** volver a `arquitectura.png` y luego el repositorio en GitHub.

**Narración:** "Con Kafka agregamos dos casos de uso: el registro continuo de
temperaturas y la reacción en tiempo real con alertas por umbral y por alza
brusca. Elegimos Kafka para este flujo porque conserva el historial, permite
varios grupos y mantiene el orden por sensor, mientras RabbitMQ sigue con las
tareas de cada pedido. Con 3 réplicas probamos que el sistema sigue funcionando
si cae un broker. El repositorio tiene el código, el Docker Compose y la
documentación. Cada integrante defenderá sus decisiones en la presentación
individual."

## Checklist final del video

- [ ] Dura entre 5 y 10 minutos, con audio claro y código legible.
- [ ] Se ve la topología real: 3 ZooKeeper, 3 brokers y Kafbat UI.
- [ ] Se ven los dos tópicos con 3 particiones, RF3 y retención.
- [ ] Se ubican en el código el productor, el consumidor, el DTO y el grupo.
- [ ] Un evento real de punta a punta, con la reacción y la alerta.
- [ ] DLT de Kafka y DLQ de RabbitMQ funcionando.
- [ ] Offsets, lag y consumer group explicados.
- [ ] Administración de Kafka mostrada.
- [ ] Casos de uso y decisiones justificados.
- [ ] No aparecen contraseñas, `.env`, tokens ni claves.
