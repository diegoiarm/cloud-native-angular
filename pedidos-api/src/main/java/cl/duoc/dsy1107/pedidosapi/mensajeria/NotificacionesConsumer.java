package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Simula el envío del aviso al cliente (email o webpush).
 *
 * Recibe pedido.creado, pedido.aceptado y pedido.despachado porque está enlazada
 * al TopicExchange con el comodín pedido.#. Confirmación manual: ACK al notificar,
 * ACK tras programar un reintento y NACK sin requeue al agotar los intentos,
 * que el DLX conduce a pedidos360.notificaciones.dlq.
 */
@Component
public class NotificacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesConsumer.class);

    private final Reintentos reintentos;

    public NotificacionesConsumer(Reintentos reintentos) {
        this.reintentos = reintentos;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICACIONES, ackMode = "MANUAL")
    public void notificar(Message mensaje, Channel channel) throws IOException {
        long tag = mensaje.getMessageProperties().getDeliveryTag();
        String eventoJson = new String(mensaje.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> headers = mensaje.getMessageProperties().getHeaders();
        try {
            if (reintentos.fallo("notificaciones", headers)) {
                throw new IllegalStateException("fallo controlado de notificaciones");
            }
            log.info("[NOTIFICACIÓN] {}", eventoJson);
        } catch (RuntimeException e) {
            if (reintentos.reintentar(
                    RabbitMQConfig.NOTIFICACIONES, "notificaciones", eventoJson, headers, e)) {
                // La copia ya está en notificaciones.retry: se confirma el original.
                channel.basicAck(tag, false);
            } else {
                // Intentos agotados: NACK sin requeue y el DLX lo deja en notificaciones.dlq.
                channel.basicNack(tag, false, false);
            }
            return;
        }
        channel.basicAck(tag, false);
    }
}
