package cl.duoc.dsy1107.monitorsensores.consumer;

import cl.duoc.dsy1107.monitorsensores.dto.LecturaRecibida;
import cl.duoc.dsy1107.monitorsensores.service.MemoriaLecturas;
import cl.duoc.dsy1107.sensores.dto.LecturaSensor;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer de sensores-temperatura (documento 01 S13).
 *
 * Registra partición y offset de cada registro. sensorId="sensor-fallo" lanza
 * una excepción controlada para demostrar los reintentos y la DLT.
 */
@Component
public class TemperaturaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TemperaturaConsumer.class);

    private final MemoriaLecturas memoria;
    private final String sensorFallo;

    public TemperaturaConsumer(
            MemoriaLecturas memoria,
            @Value("${sensores.lab.sensor-fallo:sensor-fallo}") String sensorFallo) {
        this.memoria = memoria;
        this.sensorFallo = sensorFallo;
    }

    @KafkaListener(
            topics = "${sensores.topico-lecturas:sensores-temperatura}",
            groupId = "${sensores.grupo:grupo-monitor-s13}")
    public void consumir(ConsumerRecord<String, LecturaSensor> registro) {
        LecturaSensor lectura = registro.value();
        if (lectura == null) {
            throw new IllegalArgumentException(
                    "Lectura no deserializable en " + registro.topic() + " offset " + registro.offset());
        }
        if (sensorFallo.equals(lectura.sensorId())) {
            throw new IllegalStateException("fallo controlado del sensor " + sensorFallo);
        }

        memoria.agregar(new LecturaRecibida(
                lectura.sensorId(),
                lectura.temperatura(),
                lectura.unidad(),
                lectura.secuencia(),
                registro.partition(),
                registro.offset(),
                Instant.now()));

        log.info("[LECTURA] key={} particion={} offset={} {}",
                registro.key(), registro.partition(), registro.offset(), lectura);
    }
}
