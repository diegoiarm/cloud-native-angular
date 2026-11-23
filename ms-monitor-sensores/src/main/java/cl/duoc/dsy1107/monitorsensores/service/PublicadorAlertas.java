package cl.duoc.dsy1107.monitorsensores.service;

import cl.duoc.dsy1107.sensores.dto.AlertaSensor;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Publica en sensores-alertas las alertas que genera el monitor.
 *
 * El envío espera la confirmación del broker. Si falla, lanza una excepción y
 * el DefaultErrorHandler reintenta la lectura completa y, al agotar los
 * intentos, la manda a sensores-temperatura.DLT.
 */
@Component
public class PublicadorAlertas {

    private static final Logger log = LoggerFactory.getLogger(PublicadorAlertas.class);
    private static final long ESPERA_ACK_SEGUNDOS = 5;

    private final KafkaTemplate<Object, Object> kafkaTemplate;
    private final String topicoAlertas;
    private final AtomicLong publicadas = new AtomicLong();

    public PublicadorAlertas(
            KafkaTemplate<Object, Object> kafkaTemplate,
            @Value("${sensores.topico-alertas:sensores-alertas}") String topicoAlertas) {
        this.kafkaTemplate = kafkaTemplate;
        this.topicoAlertas = topicoAlertas;
    }

    public void publicar(AlertaSensor alerta) {
        try {
            kafkaTemplate.send(topicoAlertas, alerta.sensorId(), alerta)
                    .get(ESPERA_ACK_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicación de alerta interrumpida", e);
        } catch (ExecutionException | TimeoutException e) {
            throw new IllegalStateException(
                    "Kafka no confirmó la alerta en " + topicoAlertas + ": " + e.getMessage(), e);
        }
        publicadas.incrementAndGet();
        log.warn("[REACCION] alerta {} publicada en {} para {}: {}",
                alerta.severidad(), topicoAlertas, alerta.sensorId(), alerta.motivo());
    }

    public long publicadas() {
        return publicadas.get();
    }
}
