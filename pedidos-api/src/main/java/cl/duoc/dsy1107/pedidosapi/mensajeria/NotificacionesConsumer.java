package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

/**
 * Simula el envío del aviso al cliente (email o webpush).
 *
 * Recibe pedido.creado, pedido.aceptado y pedido.despachado porque está enlazada
 * al TopicExchange con el comodín pedido.#. Ante un fallo controlado reintenta dos
 * veces y agota en pedidos360.notificaciones.dlq.
 */
@Component
public class NotificacionesConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificacionesConsumer.class);

    private final Reintentos reintentos;

    public NotificacionesConsumer(Reintentos reintentos) {
        this.reintentos = reintentos;
    }

    @RabbitListener(queues = RabbitMQConfig.NOTIFICACIONES)
    public void notificar(String eventoJson, @Headers Map<String, Object> headers) {
        if (reintentos.fallo("notificaciones", headers)) {
            reintentos.manejarFallo(
                    RabbitMQConfig.NOTIFICACIONES, "notificaciones", eventoJson, headers);
            return;
        }
        log.info("[NOTIFICACIÓN] {}", eventoJson);
    }
}
