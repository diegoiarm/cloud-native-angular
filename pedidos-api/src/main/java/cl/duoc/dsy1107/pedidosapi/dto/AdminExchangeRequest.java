package cl.duoc.dsy1107.pedidosapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Creación de exchanges desde el administrador de RabbitMQ (EP4-09).
 * La Semana 10 trabaja con Direct y Topic, que son los tipos admitidos.
 */
public record AdminExchangeRequest(
        @NotBlank(message = "nombre es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{1,255}$",
                message = "nombre solo admite letras, numeros, punto, guion y guion bajo")
        String nombre,
        @NotBlank(message = "tipo es obligatorio")
        @Pattern(regexp = "^(direct|topic)$",
                message = "tipo debe ser direct o topic")
        String tipo,
        Boolean durable) {
}
