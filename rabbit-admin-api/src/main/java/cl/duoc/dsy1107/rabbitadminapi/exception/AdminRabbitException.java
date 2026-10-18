package cl.duoc.dsy1107.rabbitadminapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Error operativo del administrador: el recurso no existe, está protegido o el
 * broker rechazó la operación. Lleva el código HTTP que debe responder la API.
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
