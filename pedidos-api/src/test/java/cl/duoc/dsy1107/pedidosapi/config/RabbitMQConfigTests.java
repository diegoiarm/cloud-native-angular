package cl.duoc.dsy1107.pedidosapi.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

/**
 * Topología declarada por RabbitMQConfig. No necesita broker: verifica la configuración
 * de Exchange, colas y bindings que Spring publica al conectarse. El recorrido real de
 * los mensajes se comprueba en la Management UI durante la sesión.
 */
class RabbitMQConfigTests {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void exchangeEsDirectYDurable() {
        DirectExchange exchange = config.pedidosExchange();

        assertThat(exchange.getName()).isEqualTo("pedidos360.direct.exchange");
        assertThat(exchange.isDurable()).isTrue();
        assertThat(exchange.isAutoDelete()).isFalse();
    }

    @Test
    void lasTresColasSonDurables() {
        for (Queue queue : new Queue[] {
                config.notificacionesQueue(), config.cocinaQueue(), config.despachoQueue()}) {
            assertThat(queue.isDurable()).isTrue();
            assertThat(queue.isAutoDelete()).isFalse();
        }
        assertThat(config.notificacionesQueue().getName()).isEqualTo("notificaciones.queue");
        assertThat(config.cocinaQueue().getName()).isEqualTo("cocina.queue");
        assertThat(config.despachoQueue().getName()).isEqualTo("despacho.queue");
    }

    /**
     * Tabla del documento: cada routing key debe llegar exactamente a las colas esperadas.
     */
    @Test
    void cadaRoutingKeyLlegaASusColas() {
        assertThat(destinoDe("pedido.creado")).containsExactly("notificaciones.queue");
        assertThat(destinoDe("pedido.aceptado"))
                .containsExactlyInAnyOrder("notificaciones.queue", "cocina.queue");
        assertThat(destinoDe("pedido.despachado"))
                .containsExactlyInAnyOrder("notificaciones.queue", "despacho.queue");
    }

    @Test
    void routingKeyDesconocidaNoLlegaANingunaCola() {
        // DirectExchange exige coincidencia exacta: pedido.aceptado.v2 no es pedido.aceptado.
        assertThat(destinoDe("pedido.aceptado.v2")).isEmpty();
    }

    private java.util.List<String> destinoDe(String routingKey) {
        DirectExchange exchange = config.pedidosExchange();
        Queue[] colas = {
                config.notificacionesQueue(), config.cocinaQueue(), config.despachoQueue()};
        Binding[] bindings = {
                config.notificaCreado(exchange, config.notificacionesQueue()),
                config.notificaAceptado(exchange, config.notificacionesQueue()),
                config.cocinaAceptado(exchange, config.cocinaQueue()),
                config.notificaDespachado(exchange, config.notificacionesQueue()),
                config.despachoDespachado(exchange, config.despachoQueue())};

        return java.util.Arrays.stream(bindings)
                .filter(b -> routingKey.equals(b.getRoutingKey()))
                .map(b -> b.getDestination())
                .toList();
    }
}
