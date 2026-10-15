package cl.duoc.dsy1107.pedidosapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Error operativo del administrador de RabbitMQ (EP4-09): el broker rechazó la
 * operación solicitada (recurso inexistente, permisos o precondición).
 */
public class AdminRabbitException extends RuntimeException {

    private final HttpStatus status;

    public AdminRabbitException(String mensaje) {
        this(mensaje, HttpStatus.BAD_REQUEST);
    }

    public AdminRabbitException(String mensaje, HttpStatus status) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
