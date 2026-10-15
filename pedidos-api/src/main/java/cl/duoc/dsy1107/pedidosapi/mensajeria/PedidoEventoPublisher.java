package cl.duoc.dsy1107.pedidosapi.mensajeria;

import java.util.Map;

/**
 * Publicación de eventos de pedido en RabbitMQ.
 *
 * Igual que CatalogoClient con el catálogo, esta interfaz permite que PedidoService
 * dependa del contrato de mensajería y no de RabbitMQ, y que las pruebas simulen
 * la publicación sin necesidad de un broker.
 *
 * La variante con cabeceras la usa el laboratorio de la Semana 10 para marcar el
 * fallo controlado (fallo-notificaciones, fallo-cocina, fallo-documentos).
 */
public interface PedidoEventoPublisher {

    void publicar(String routingKey, PedidoEvento evento, Map<String, Object> headers);

    default void publicar(String routingKey, PedidoEvento evento) {
        publicar(routingKey, evento, Map.of());
    }
}
