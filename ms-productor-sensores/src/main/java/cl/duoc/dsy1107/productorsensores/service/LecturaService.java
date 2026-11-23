package cl.duoc.dsy1107.productorsensores.service;

import cl.duoc.dsy1107.productorsensores.dto.EstadoProductorResponse;
import cl.duoc.dsy1107.productorsensores.dto.PublicacionResponse;
import cl.duoc.dsy1107.productorsensores.exception.KafkaNoDisponibleException;
import cl.duoc.dsy1107.sensores.dto.LecturaSensor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Ingesta de lecturas: solo publica en sensores-temperatura.
 *
 * El productor no decide si una lectura es una alerta. Esa reacción ocurre en
 * ms-monitor-sensores, que consume el evento, evalúa las reglas y publica en
 * sensores-alertas (procesamiento reactivo, EP6).
 *
 * La clave de cada mensaje es sensorId: así los eventos de un mismo sensor
 * conservan su orden relativo dentro de una partición.
 */
@Service
public class LecturaService {

    private static final long ESPERA_ACK_SEGUNDOS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String bootstrapServers;
    private final String topicoLecturas;
    private final int particiones;
    private final int replicas;
    private final long retencionMs;

    public LecturaService(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${sensores.topico-lecturas:sensores-temperatura}") String topicoLecturas,
            @Value("${sensores.particiones:3}") int particiones,
            @Value("${sensores.replicas:1}") int replicas,
            @Value("${sensores.retencion-ms:86400000}") long retencionMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.bootstrapServers = bootstrapServers;
        this.topicoLecturas = topicoLecturas;
        this.particiones = particiones;
        this.replicas = replicas;
        this.retencionMs = retencionMs;
    }

    public PublicacionResponse publicar(LecturaSensor lectura) {
        SendResult<String, Object> resultado = enviar(topicoLecturas, lectura.sensorId(), lectura);
        return new PublicacionResponse(
                lectura.sensorId(),
                resultado.getRecordMetadata().partition(),
                resultado.getRecordMetadata().offset());
    }

    public EstadoProductorResponse estado() {
        return new EstadoProductorResponse(
                "ms-productor-sensores",
                bootstrapServers,
                topicoLecturas,
                particiones,
                replicas,
                retencionMs);
    }

    private SendResult<String, Object> enviar(String topico, String clave, Object evento) {
        try {
            return kafkaTemplate.send(topico, clave, evento).get(ESPERA_ACK_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new KafkaNoDisponibleException("Publicación interrumpida hacia " + topico, e);
        } catch (ExecutionException | TimeoutException e) {
            throw new KafkaNoDisponibleException(
                    "Kafka no confirmó la publicación en " + topico + ": " + e.getMessage(), e);
        }
    }
}
