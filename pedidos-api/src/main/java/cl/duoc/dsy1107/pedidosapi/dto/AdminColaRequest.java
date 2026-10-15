package cl.duoc.dsy1107.pedidosapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Creación de colas desde el administrador de RabbitMQ (EP4-09).
 * Solo letras, números, punto, guion bajo y guion, como los nombres de la topología.
 */
public record AdminColaRequest(
        @NotBlank(message = "nombre es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{1,255}$",
                message = "nombre solo admite letras, numeros, punto, guion y guion bajo")
        String nombre,
        Boolean durable) {
}
