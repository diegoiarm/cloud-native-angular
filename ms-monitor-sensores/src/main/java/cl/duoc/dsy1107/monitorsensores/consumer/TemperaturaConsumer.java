package cl.duoc.dsy1107.monitorsensores.consumer;

import cl.duoc.dsy1107.monitorsensores.dto.LecturaRecibida;
import cl.duoc.dsy1107.monitorsensores.service.EmisorEventos;
import cl.duoc.dsy1107.monitorsensores.service.MemoriaLecturas;
import cl.duoc.dsy1107.monitorsensores.service.PublicadorAlertas;
import cl.duoc.dsy1107.monitorsensores.service.ReglasReaccion;
import cl.duoc.dsy1107.sensores.dto.LecturaSensor;
import java.time.Instant;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumer de sensores-temperatura y punto de reacción del sistema (EP6).
 *
 * Por cada lectura:
 *   1. evalúa las reglas (umbral y alza brusca por sensor);
 *   2. si corresponde, publica la alerta en sensores-alertas;
 *   3. guarda la lectura y la envía en tiempo real al navegador (SSE).
 *
 * Si falla la publicación de la alerta se lanza la excepción antes de guardar,
 * así el reintento no duplica la lectura. sensorId="sensor-fallo" lanza una
 * excepción controlada para demostrar los reintentos y la DLT.
 */
@Component
public class TemperaturaConsumer {

    private static final Logger log = LoggerFactory.getLogger(TemperaturaConsumer.class);

    private final MemoriaLecturas memoria;
    private final ReglasReaccion reglas;
    private final PublicadorAlertas publicador;
    private final EmisorEventos emisor;
    private final String sensorFallo;

    public TemperaturaConsumer(
            MemoriaLecturas memoria,
            ReglasReaccion reglas,
            PublicadorAlertas publicador,
            EmisorEventos emisor,
            @Value("${sensores.lab.sensor-fallo:sensor-fallo}") String sensorFallo) {
        this.memoria = memoria;
        this.reglas = reglas;
        this.publicador = publicador;
        this.emisor = emisor;
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

        reglas.evaluar(lectura).ifPresent(publicador::publicar);
        reglas.registrar(lectura);

        LecturaRecibida recibida = new LecturaRecibida(
                lectura.sensorId(),
                lectura.temperatura(),
                lectura.unidad(),
                lectura.secuencia(),
                registro.partition(),
                registro.offset(),
                Instant.now());
        memoria.agregar(recibida);
        emisor.emitir("lectura", recibida);

        log.info("[LECTURA] key={} particion={} offset={} {}",
                registro.key(), registro.partition(), registro.offset(), lectura);
    }
}
