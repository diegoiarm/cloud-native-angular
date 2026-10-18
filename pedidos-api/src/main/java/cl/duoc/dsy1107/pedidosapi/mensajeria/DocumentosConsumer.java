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
 * Simula la emisión de los documentos del pedido (boleta y guía de despacho).
 *
 * Solo recibe pedido.despachado: pedidos360.documentos.queue está enlazada
 * únicamente a esa routing key en el DirectExchange. Confirmación manual: ACK al
 * emitir, ACK tras programar un reintento y NACK sin requeue al agotar los
 * intentos, que el DLX conduce a pedidos360.documentos.dlq.
 */
@Component
public class DocumentosConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentosConsumer.class);

    private final Reintentos reintentos;

    public DocumentosConsumer(Reintentos reintentos) {
        this.reintentos = reintentos;
    }

    @RabbitListener(queues = RabbitMQConfig.DOCUMENTOS, ackMode = "MANUAL")
    public void emitirDocumentos(Message mensaje, Channel channel) throws IOException {
        long tag = mensaje.getMessageProperties().getDeliveryTag();
        String eventoJson = new String(mensaje.getBody(), StandardCharsets.UTF_8);
        Map<String, Object> headers = mensaje.getMessageProperties().getHeaders();
        try {
            if (reintentos.fallo("documentos", headers)) {
                throw new IllegalStateException("fallo controlado de documentos");
            }
            log.info("[DOCUMENTOS] {}", eventoJson);
        } catch (RuntimeException e) {
            if (reintentos.reintentar(
                    RabbitMQConfig.DOCUMENTOS, "documentos", eventoJson, headers, e)) {
                // La copia ya está en documentos.retry: se confirma el original.
                channel.basicAck(tag, false);
            } else {
                // Intentos agotados: NACK sin requeue y el DLX lo deja en documentos.dlq.
                channel.basicNack(tag, false, false);
            }
            return;
        }
        channel.basicAck(tag, false);
    }
}
