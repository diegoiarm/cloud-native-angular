package cl.duoc.dsy1107.kafkaadminapi.exception;

import org.springframework.http.HttpStatus;

/**
 * Error operativo del administrador: el recurso no existe, está protegido o
 * Kafka rechazó la operación. Lleva el código HTTP que debe responder la API.
 */
public class AdminKafkaException extends RuntimeException {

    private final HttpStatus status;

    public AdminKafkaException(String mensaje) {
        this(mensaje, HttpStatus.BAD_REQUEST);
    }

    public AdminKafkaException(String mensaje, HttpStatus status) {
        super(mensaje);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
