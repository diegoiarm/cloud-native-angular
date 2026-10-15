package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.messaging.handler.annotation.Headers;
import org.springframework.stereotype.Component;

/**
 * Simula la emisión de los documentos del pedido (boleta y guía de despacho).
 *
 * Solo recibe pedido.despachado: pedidos360.documentos.queue está enlazada
 * únicamente a esa routing key en el DirectExchange. Ante un fallo controlado
 * reintenta dos veces y agota en pedidos360.documentos.dlq.
 */
@Component
public class DocumentosConsumer {

    private static final Logger log = LoggerFactory.getLogger(DocumentosConsumer.class);

    private final Reintentos reintentos;

    public DocumentosConsumer(Reintentos reintentos) {
        this.reintentos = reintentos;
    }

    @RabbitListener(queues = RabbitMQConfig.DOCUMENTOS)
    public void emitirDocumentos(String eventoJson, @Headers Map<String, Object> headers) {
        if (reintentos.fallo("documentos", headers)) {
            reintentos.manejarFallo(RabbitMQConfig.DOCUMENTOS, "documentos", eventoJson, headers);
            return;
        }
        log.info("[DOCUMENTOS] {}", eventoJson);
    }
}
