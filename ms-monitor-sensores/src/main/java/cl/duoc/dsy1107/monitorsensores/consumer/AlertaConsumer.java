package cl.duoc.dsy1107.monitorsensores.consumer;

import cl.duoc.dsy1107.monitorsensores.dto.AlertaRecibida;
import cl.duoc.dsy1107.monitorsensores.service.EmisorEventos;
import cl.duoc.dsy1107.monitorsensores.service.MemoriaAlertas;
import cl.duoc.dsy1107.sensores.dto.AlertaSensor;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer del segundo topic, sensores-alertas (EP5: lógica de consumo separada).
 *
 * Recibe las alertas que publicó TemperaturaConsumer al reaccionar a una
 * lectura, las guarda y las envía en tiempo real al navegador.
 */
@Component
public class AlertaConsumer {

    private static final Logger log = LoggerFactory.getLogger(AlertaConsumer.class);

    private final MemoriaAlertas memoria;
    private final EmisorEventos emisor;

    public AlertaConsumer(MemoriaAlertas memoria, EmisorEventos emisor) {
        this.memoria = memoria;
        this.emisor = emisor;
    }

    @KafkaListener(
            topics = "${sensores.topico-alertas:sensores-alertas}",
            groupId = "${sensores.grupo:grupo-monitor-s13}")
    public void consumir(ConsumerRecord<String, AlertaSensor> registro) {
        AlertaSensor alerta = registro.value();
        if (alerta == null) {
            throw new IllegalArgumentException(
                    "Alerta no deserializable en " + registro.topic() + " offset " + registro.offset());
        }

        AlertaRecibida recibida = new AlertaRecibida(
                alerta.sensorId(),
                alerta.temperatura(),
                alerta.unidad(),
                alerta.secuencia(),
                alerta.motivo(),
                alerta.severidad(),
                alerta.generadaEn(),
                registro.partition(),
                registro.offset(),
                Instant.now());
        memoria.agregar(recibida);
        emisor.emitir("alerta", recibida);

        log.warn("[ALERTA] key={} particion={} offset={} {}",
                registro.key(), registro.partition(), registro.offset(), alerta);
    }
}
