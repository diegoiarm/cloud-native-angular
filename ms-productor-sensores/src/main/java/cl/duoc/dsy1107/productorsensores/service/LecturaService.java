package cl.duoc.dsy1107.productorsensores.service;

import cl.duoc.dsy1107.productorsensores.dto.EstadoProductorResponse;
import cl.duoc.dsy1107.productorsensores.dto.PublicacionResponse;
import cl.duoc.dsy1107.productorsensores.exception.KafkaNoDisponibleException;
import cl.duoc.dsy1107.sensores.dto.AlertaSensor;
import cl.duoc.dsy1107.sensores.dto.LecturaSensor;
import java.time.Instant;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

/**
 * Publica lecturas en sensores-temperatura y, si corresponde, alertas en
 * sensores-alertas (EP5: dos topics con flujos separados).
 *
 * La clave de cada mensaje es sensorId: así los eventos de un mismo sensor
 * conservan su orden relativo dentro de una partición.
 */
@Service
public class LecturaService {

    private static final Logger log = LoggerFactory.getLogger(LecturaService.class);
    private static final long ESPERA_ACK_SEGUNDOS = 5;

    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final String bootstrapServers;
    private final String topicoLecturas;
    private final String topicoAlertas;
    private final double umbralAlertaC;
    private final int particiones;
    private final int replicas;
    private final long retencionMs;

    public LecturaService(
            KafkaTemplate<String, Object> kafkaTemplate,
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers,
            @Value("${sensores.topico-lecturas:sensores-temperatura}") String topicoLecturas,
            @Value("${sensores.topico-alertas:sensores-alertas}") String topicoAlertas,
            @Value("${sensores.umbral-alerta-c:30.0}") double umbralAlertaC,
            @Value("${sensores.particiones:3}") int particiones,
            @Value("${sensores.replicas:1}") int replicas,
            @Value("${sensores.retencion-ms:86400000}") long retencionMs) {
        this.kafkaTemplate = kafkaTemplate;
        this.bootstrapServers = bootstrapServers;
        this.topicoLecturas = topicoLecturas;
        this.topicoAlertas = topicoAlertas;
        this.umbralAlertaC = umbralAlertaC;
        this.particiones = particiones;
        this.replicas = replicas;
        this.retencionMs = retencionMs;
    }

    public PublicacionResponse publicar(LecturaSensor lectura) {
        SendResult<String, Object> resultado = enviar(topicoLecturas, lectura.sensorId(), lectura);

        boolean alertaPublicada = false;
        if (lectura.temperatura() >= umbralAlertaC) {
            AlertaSensor alerta = new AlertaSensor(
                    lectura.sensorId(),
                    lectura.temperatura(),
                    lectura.unidad(),
                    lectura.secuencia(),
                    "Temperatura " + lectura.temperatura() + " " + lectura.unidad()
                            + " supera el umbral " + umbralAlertaC + " " + lectura.unidad(),
                    "ALTA",
                    Instant.now());
            try {
                enviar(topicoAlertas, alerta.sensorId(), alerta);
                alertaPublicada = true;
            } catch (KafkaNoDisponibleException e) {
                // La lectura ya fue aceptada: la alerta es un flujo adicional.
                log.warn("Lectura publicada, pero no se pudo publicar la alerta en {}: {}",
                        topicoAlertas, e.getMessage());
            }
        }

        return new PublicacionResponse(
                lectura.sensorId(),
                resultado.getRecordMetadata().partition(),
                resultado.getRecordMetadata().offset(),
                alertaPublicada);
    }

    public EstadoProductorResponse estado() {
        return new EstadoProductorResponse(
                "ms-productor-sensores",
                bootstrapServers,
                topicoLecturas,
                topicoAlertas,
                umbralAlertaC,
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
