package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Simula la impresión del ticket de cocina.
 *
 * Solo recibe pedido.aceptado: cocina.queue está enlazada únicamente a esa routing key.
 */
@Component
public class CocinaConsumer {

    private static final Logger log = LoggerFactory.getLogger(CocinaConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.COCINA)
    public void imprimirTicket(String eventoJson) {
        log.info("[COCINA] {}", eventoJson);
    }
}
