package cl.duoc.dsy1107.pedidosapi.mensajeria;

/**
 * Publicación de eventos de pedido en RabbitMQ.
 *
 * Igual que CatalogoClient con el catálogo, esta interfaz permite que PedidoService
 * dependa del contrato de mensajería y no de RabbitMQ, y que las pruebas simulen
 * la publicación sin necesidad de un broker.
 */
public interface PedidoEventoPublisher {

    void publicar(String routingKey, PedidoEvento evento);
}
