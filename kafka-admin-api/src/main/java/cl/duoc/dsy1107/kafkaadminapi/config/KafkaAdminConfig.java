package cl.duoc.dsy1107.kafkaadminapi.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * AdminClient del administrador. No se conecta al crear el bean: la conexión
 * ocurre en la primera operación, por eso la API puede arrancar sin broker.
 */
@Configuration
public class KafkaAdminConfig {

    @Bean(destroyMethod = "close")
    AdminClient adminClient(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers) {
        Map<String, Object> propiedades = new HashMap<>();
        propiedades.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        propiedades.put(AdminClientConfig.REQUEST_TIMEOUT_MS_CONFIG, 5000);
        propiedades.put(AdminClientConfig.DEFAULT_API_TIMEOUT_MS_CONFIG, 8000);
        return AdminClient.create(propiedades);
    }
}
