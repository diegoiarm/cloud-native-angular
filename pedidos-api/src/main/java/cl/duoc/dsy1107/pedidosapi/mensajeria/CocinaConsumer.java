package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

/**
 * Simula la impresión del ticket de cocina.
 *
 * Solo recibe pedido.aceptado: pedidos360.cocina.queue está enlazada únicamente
 * a esa routing key en el DirectExchange. Ante un fallo controlado reintenta dos
 * veces y agota en pedidos360.cocina.dlq.
 */
@Component
public class CocinaConsumer {

    private static final Logger log = LoggerFactory.getLogger(CocinaConsumer.class);

    private final Reintentos reintentos;

    public CocinaConsumer(Reintentos reintentos) {
        this.reintentos = reintentos;
    }

    @RabbitListener(queues = RabbitMQConfig.COCINA)
    public void imprimirTicket(String eventoJson, @Headers Map<String, Object> headers) {
        if (reintentos.fallo("cocina", headers)) {
            reintentos.manejarFallo(RabbitMQConfig.COCINA, "cocina", eventoJson, headers);
            return;
        }
        log.info("[COCINA] {}", eventoJson);
    }
}
