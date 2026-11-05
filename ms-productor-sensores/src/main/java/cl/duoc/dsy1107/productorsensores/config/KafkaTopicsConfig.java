package cl.duoc.dsy1107.productorsensores.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.config.TopicConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Administración declarativa de los topics (EP5: administración por código).
 *
 * KafkaAdmin crea los topics al arrancar si no existen. En el laboratorio de un
 * broker se usa RF1; en el cluster de EP6 se arranca con KAFKA_REPLICAS=3.
 */
@Configuration
@Profile("!test")
public class KafkaTopicsConfig {

    @Bean
    NewTopic topicoLecturas(
            @Value("${sensores.topico-lecturas:sensores-temperatura}") String nombre,
            @Value("${sensores.particiones:3}") int particiones,
            @Value("${sensores.replicas:1}") int replicas,
            @Value("${sensores.retencion-ms:86400000}") long retencionMs) {
        return topic(nombre, particiones, replicas, retencionMs);
    }

    @Bean
    NewTopic topicoAlertas(
            @Value("${sensores.topico-alertas:sensores-alertas}") String nombre,
            @Value("${sensores.particiones:3}") int particiones,
            @Value("${sensores.replicas:1}") int replicas,
            @Value("${sensores.retencion-ms:86400000}") long retencionMs) {
        return topic(nombre, particiones, replicas, retencionMs);
    }

    @Bean
    NewTopic dltLecturas(
            @Value("${sensores.topico-lecturas:sensores-temperatura}") String nombre,
            @Value("${sensores.particiones:3}") int particiones,
            @Value("${sensores.replicas:1}") int replicas) {
        return topic(nombre + ".DLT", particiones, replicas, 604800000L);
    }

    @Bean
    NewTopic dltAlertas(
            @Value("${sensores.topico-alertas:sensores-alertas}") String nombre,
            @Value("${sensores.particiones:3}") int particiones,
            @Value("${sensores.replicas:1}") int replicas) {
        return topic(nombre + ".DLT", particiones, replicas, 604800000L);
    }

    private static NewTopic topic(String nombre, int particiones, int replicas, long retencionMs) {
        return TopicBuilder.name(nombre)
                .partitions(particiones)
                .replicas((short) replicas)
                .config(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retencionMs))
                .build();
    }
}
