package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publica eventos de pedido en el DirectExchange de Pedidos360.
 *
 * El producer solo conoce el Exchange y la routing key: no sabe qué colas ni qué
 * consumers están suscritos. Ese acoplamiento lo resuelve el Exchange con los Bindings.
 */
@Service
public class PedidoEventoProducer implements PedidoEventoPublisher {

    private final RabbitTemplate rabbitTemplate;
    private final ObjectMapper objectMapper;

    public PedidoEventoProducer(RabbitTemplate rabbitTemplate, ObjectMapper objectMapper) {
        this.rabbitTemplate = rabbitTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void publicar(String routingKey, PedidoEvento evento) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EXCHANGE,
                    routingKey,
                    objectMapper.writeValueAsString(evento));
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible serializar el evento", e);
        }
    }
}
