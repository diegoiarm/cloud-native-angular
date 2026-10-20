package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import cl.duoc.dsy1107.pedidosapi.exception.MensajeriaNoDisponibleException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.MessagePostProcessor;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publica cada evento en los dos exchanges de Pedidos360.
 *
 * El DirectExchange hace enrutamiento exacto: pedido.aceptado llega a cocina y
 * pedido.despachado a documentos. El TopicExchange difunde con el comodín
 * pedido.# hacia notificaciones. Cada cola queda enlazada a un solo exchange,
 * así que un mismo evento no se entrega dos veces.
 *
 * El producer solo conoce los Exchanges y la routing key: no sabe qué colas ni
 * qué consumers están suscritos. Ese acoplamiento lo resuelve los Bindings.
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
    public void publicar(String routingKey, PedidoEvento evento, Map<String, Object> headers) {
        try {
            String json = objectMapper.writeValueAsString(evento);
            MessagePostProcessor conCabeceras = mensaje -> {
                headers.forEach(mensaje.getMessageProperties()::setHeader);
                return mensaje;
            };
            rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, routingKey, json, conCabeceras);
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.TOPIC_EXCHANGE, routingKey, json, conCabeceras);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("No fue posible serializar el evento", e);
        } catch (AmqpException e) {
            // PedidoService no conoce RabbitMQ: recibe una excepción propia del dominio.
            throw new MensajeriaNoDisponibleException(e);
        }
    }
}
