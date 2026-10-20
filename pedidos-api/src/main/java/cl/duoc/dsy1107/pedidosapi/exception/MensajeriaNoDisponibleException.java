package cl.duoc.dsy1107.pedidosapi.exception;

/**
 * RabbitMQ no aceptó el evento del pedido. La operación se revierte y la API
 * responde 503, igual que cuando el catálogo no está disponible.
 */
public class MensajeriaNoDisponibleException extends RuntimeException {

    public MensajeriaNoDisponibleException(Throwable causa) {
        super("La mensajería no está disponible; el pedido no cambió. Intente nuevamente.", causa);
    }
}
