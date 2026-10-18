package cl.duoc.dsy1107.rabbitadminapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Creación de un exchange. Se admiten los dos tipos que usa Pedidos360. */
public record ExchangeRequest(
        @NotBlank(message = "nombre es obligatorio")
        @Pattern(regexp = Nombres.PATRON, message = "nombre " + Nombres.MENSAJE)
        String nombre,
        @NotBlank(message = "tipo es obligatorio")
        @Pattern(regexp = "^(direct|topic)$", message = "tipo debe ser direct o topic")
        String tipo,
        Boolean durable) {
}
