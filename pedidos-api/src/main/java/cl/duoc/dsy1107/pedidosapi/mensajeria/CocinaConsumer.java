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
 * Simula la impresión del ticket de cocina.
 *
 * Solo recibe pedido.aceptado: pedidos360.cocina.queue está enlazada únicamente
 * a esa routing key en el DirectExchange. Confirmación manual: ACK al imprimir,
 * ACK tras programar un reintento y NACK sin requeue al agotar los intentos,
 * que el DLX conduce a pedidos360.cocina.dlq.
 */
@Component
public class CocinaConsumer {

    private static final Logger log = LoggerFactory.getLogger(CocinaConsumer.class);

    private final Reintentos reintentos;

    public CocinaConsumer(Reintentos reintentos) {
        this.reintentos = reintentos;
    }

    @RabbitListener(queues = RabbitMQConfig.COCINA, ackMode = "MANUAL")
    public void imprimirTicket(Message mensaje, Channel channel) throws IOException {
        long tag = mensaje.getMessageProperties().getDeliveryTag();
        String eventoJson = new String(mensaje.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> headers = mensaje.getMessageProperties().getHeaders();
        try {
            if (reintentos.fallo("cocina", headers)) {
                throw new IllegalStateException("fallo controlado de cocina");
            }
            log.info("[COCINA] {}", eventoJson);
        } catch (RuntimeException e) {
            if (reintentos.reintentar(RabbitMQConfig.COCINA, "cocina", eventoJson, headers, e)) {
                // La copia ya está en cocina.retry: el original se confirma para no duplicarlo.
                channel.basicAck(tag, false);
            } else {
                // Intentos agotados: NACK sin requeue y el DLX lo deja en cocina.dlq.
                channel.basicNack(tag, false, false);
            }
            return;
        }
        channel.basicAck(tag, false);
    }
}
