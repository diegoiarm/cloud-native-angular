package cl.duoc.dsy1107.productorsensores.exception;

import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Errores de la API del productor.
 *
 *   validación del DTO          -> 400 (por ejemplo secuencia = -5)
 *   Kafka no disponible         -> 503 (el mensaje no se publicó)
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, String> validacion(MethodArgumentNotValidException excepcion) {
        String mensaje = excepcion.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Solicitud inválida");
        return Map.of("mensaje", mensaje);
    }

    @ExceptionHandler(KafkaNoDisponibleException.class)
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public Map<String, String> kafka(KafkaNoDisponibleException excepcion) {
        return Map.of("mensaje", excepcion.getMessage());
    }
}
