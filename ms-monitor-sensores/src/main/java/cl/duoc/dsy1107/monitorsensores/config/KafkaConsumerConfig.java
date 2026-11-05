package cl.duoc.dsy1107.monitorsensores.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Manejo de errores de los consumers (EP5: retry y DLT).
 *
 * Intento inicial + 2 reintentos con 1 s de espera. Si el mensaje sigue
 * fallando, DeadLetterPublishingRecoverer lo publica en <topic>.DLT y el
 * consumer confirma el original para no bloquear la partición.
 */
@Configuration
@Profile("!test")
public class KafkaConsumerConfig {

    private static final Logger log = LoggerFactory.getLogger(KafkaConsumerConfig.class);

    @Bean
    DefaultErrorHandler errorHandler(KafkaTemplate<Object, Object> kafkaTemplate) {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(kafkaTemplate);
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.setRetryListeners((registro, excepcion, intento) -> log.warn(
                "[KAFKA] intento {}/3 fallido en {}-{} offset {} ({}); se reintenta o va a {}.DLT",
                intento,
                registro.topic(),
                registro.partition(),
                registro.offset(),
                excepcion.getMessage(),
                registro.topic()));
        return handler;
    }

    @Bean
    NewTopic dltLecturas(
            @Value("${sensores.topico-lecturas:sensores-temperatura}") String nombre,
            @Value("${sensores.particiones:3}") int particiones,
            @Value("${sensores.replicas:1}") int replicas) {
        return topic(nombre + ".DLT", particiones, replicas);
    }

    @Bean
    NewTopic dltAlertas(
            @Value("${sensores.topico-alertas:sensores-alertas}") String nombre,
            @Value("${sensores.particiones:3}") int particiones,
            @Value("${sensores.replicas:1}") int replicas) {
        return topic(nombre + ".DLT", particiones, replicas);
    }

    private static NewTopic topic(String nombre, int particiones, int replicas) {
        return TopicBuilder.name(nombre)
                .partitions(particiones)
                .replicas((short) replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, "604800000")
                .build();
    }
}
