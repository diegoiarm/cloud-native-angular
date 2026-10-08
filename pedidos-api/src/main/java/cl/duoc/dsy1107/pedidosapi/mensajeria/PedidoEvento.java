package cl.duoc.dsy1107.pedidosapi.mensajeria;

import java.time.LocalDateTime;

/**
 * Evento de pedido publicado en RabbitMQ.
 *
 * Lleva solo lo que el consumer necesita para su tarea: el mensaje no reemplaza al
 * estado persistido en la base de datos, comunica que ocurrió una acción.
 */
public record PedidoEvento(
        Long pedidoId,
        String estado,
        String clienteEmail,
        LocalDateTime timestamp) {
}
