package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Simula el aviso al equipo de despacho.
 *
 * Solo recibe pedido.despachado: despacho.queue está enlazada únicamente a esa routing key.
 */
@Component
public class DespachoConsumer {

    private static final Logger log = LoggerFactory.getLogger(DespachoConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.DESPACHO)
    public void notificarDespacho(String eventoJson) {
        log.info("[DESPACHO] {}", eventoJson);
    }
}
