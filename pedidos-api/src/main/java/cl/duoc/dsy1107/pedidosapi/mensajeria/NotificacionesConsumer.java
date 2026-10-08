package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Simula el envío del aviso al cliente (email o webpush).
 *
 * Recibe pedido.creado, pedido.aceptado y pedido.despachado, porque la cola
 * notificaciones.queue está enlazada a las tres routing keys.
 */
@Component
public class NotificacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesConsumer.class);

    @RabbitListener(queues = RabbitMQConfig.NOTIFICACIONES)
    public void notificar(String eventoJson) {
        log.info("[NOTIFICACIÓN] {}", eventoJson);
    }
}
