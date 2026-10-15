package cl.duoc.dsy1107.pedidosapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;

/**
 * Topología declarada por RabbitMQConfig (documentos 03, 04 y 05). No necesita
 * broker: verifica qué colas, exchanges, bindings y argumentos de dead-letter
 * publica Spring al conectarse. El recorrido real se comprueba en la Management UI.
 */
class RabbitMQConfigTests {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void losTresExchangesSonDurables() {
        DirectExchange direct = config.pedidosExchange();
        TopicExchange topic = config.pedidosTopicExchange();
        DirectExchange dlx = config.dlxExchange();

        assertThat(direct.getName()).isEqualTo("pedidos360.direct.exchange");
        assertThat(topic.getName()).isEqualTo("pedidos360.topic.exchange");
        assertThat(dlx.getName()).isEqualTo("pedidos360.dlx.exchange");
        for (var exchange : List.of(direct, topic, dlx)) {
            assertThat(exchange.isDurable()).isTrue();
            assertThat(exchange.isAutoDelete()).isFalse();
        }
    }

    @Test
    void lasTresColasPrincipalesApuntanASuDlq() {
        for (Queue cola : new Queue[] {
                config.notificacionesQueue(), config.cocinaQueue(), config.documentosQueue()}) {
            assertThat(cola.isDurable()).isTrue();
            assertThat(cola.isAutoDelete()).isFalse();
            assertThat(cola.getArguments())
                    .containsEntry("x-dead-letter-exchange", "pedidos360.dlx.exchange");
            assertThat(cola.getArguments().get("x-dead-letter-routing-key"))
                    .isEqualTo(cola.getName().replace(".queue", ".dlq"));
        }
        assertThat(config.notificacionesQueue().getName())
                .isEqualTo("pedidos360.notificaciones.queue");
        assertThat(config.cocinaQueue().getName()).isEqualTo("pedidos360.cocina.queue");
        assertThat(config.documentosQueue().getName()).isEqualTo("pedidos360.documentos.queue");
    }

    @Test
    void lasColasRetryEsperanCincoSegundosYVuelvenALaPrincipal() {
        Queue retry = config.notificacionesRetryQueue();

        assertThat(retry.getName()).isEqualTo("pedidos360.notificaciones.retry");
        assertThat(retry.getArguments()).containsEntry("x-message-ttl", 5000);
        assertThat(retry.getArguments()).containsEntry("x-dead-letter-exchange", "");
        assertThat(retry.getArguments())
                .containsEntry("x-dead-letter-routing-key", "pedidos360.notificaciones.queue");
        assertThat(config.cocinaRetryQueue().getArguments())
                .containsEntry("x-dead-letter-routing-key", "pedidos360.cocina.queue");
        assertThat(config.documentosRetryQueue().getArguments())
                .containsEntry("x-dead-letter-routing-key", "pedidos360.documentos.queue");
    }

    @Test
    void cadaDlqRecibeLosMensajesDeSuColaPrincipal() {
        DirectExchange dlx = config.dlxExchange();

        assertThat(config.notificacionesDlqBinding(dlx, config.notificacionesDlq()).getRoutingKey())
                .isEqualTo("pedidos360.notificaciones.dlq");
        assertThat(config.cocinaDlqBinding(dlx, config.cocinaDlq()).getRoutingKey())
                .isEqualTo("pedidos360.cocina.dlq");
        assertThat(config.documentosDlqBinding(dlx, config.documentosDlq()).getRoutingKey())
                .isEqualTo("pedidos360.documentos.dlq");
        assertThat(config.notificacionesDlq().isDurable()).isTrue();
    }

    /**
     * El DirectExchange exige coincidencia exacta: cocina y documentos, nada más.
     */
    @Test
    void elDirectExchangeEnrutaExacto() {
        assertThat(destinosDirectos("pedido.aceptado")).containsExactly("pedidos360.cocina.queue");
        assertThat(destinosDirectos("pedido.despachado"))
                .containsExactly("pedidos360.documentos.queue");
        assertThat(destinosDirectos("pedido.creado")).isEmpty();
        assertThat(destinosDirectos("pedido.aceptado.v2")).isEmpty();
    }

    @Test
    void elTopicExchangeDifundeElFlujoConComodin() {
        Binding binding = config.notificacionesTopic(
                config.pedidosTopicExchange(), config.notificacionesQueue());

        assertThat(binding.getExchange()).isEqualTo("pedidos360.topic.exchange");
        assertThat(binding.getRoutingKey()).isEqualTo("pedido.#");
        assertThat(binding.getDestination()).isEqualTo("pedidos360.notificaciones.queue");
    }

    private List<String> destinosDirectos(String routingKey) {
        DirectExchange exchange = config.pedidosExchange();
        Binding[] bindings = {
                config.cocinaAceptado(exchange, config.cocinaQueue()),
                config.documentosDespachado(exchange, config.documentosQueue())};
        return java.util.Arrays.stream(bindings)
                .filter(b -> routingKey.equals(b.getRoutingKey()))
                .map(Binding::getDestination)
                .toList();
    }
}
