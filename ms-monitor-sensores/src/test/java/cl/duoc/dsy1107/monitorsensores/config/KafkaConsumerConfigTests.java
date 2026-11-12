package cl.duoc.dsy1107.monitorsensores.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

/**
 * El destino de la DLT debe coincidir con los topics declarados (<topic>.DLT).
 * Spring Kafka 3.3 usa <topic>-dlt por defecto, que no existe en el broker.
 */
class KafkaConsumerConfigTests {

    @Test
    void destinoDltUsaSufijoPuntoDltYLaMismaParticion() {
        ConsumerRecord<String, String> registro =
                new ConsumerRecord<>("sensores-temperatura", 2, 15L, "sensor-fallo", "{}");

        TopicPartition destino = KafkaConsumerConfig.destinoDlt(registro);

        assertThat(destino.topic()).isEqualTo("sensores-temperatura.DLT");
        assertThat(destino.partition()).isEqualTo(2);
    }

    @Test
    void destinoDltDelTopicoDeAlertas() {
        ConsumerRecord<String, String> registro =
                new ConsumerRecord<>("sensores-alertas", 0, 3L, "sensor-B", "{}");

        assertThat(KafkaConsumerConfig.destinoDlt(registro).topic()).isEqualTo("sensores-alertas.DLT");
    }
}
