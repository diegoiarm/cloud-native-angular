package cl.duoc.dsy1107.pedidosapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Creación o eliminación de bindings (cola <-> exchange con routing key),
 * incluida la sintaxis de comodín del TopicExchange: pedido.# o pedido.*.
 */
public record AdminBindingRequest(
        @NotBlank(message = "cola es obligatoria")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{1,255}$",
                message = "cola solo admite letras, numeros, punto, guion y guion bajo")
        String cola,
        @NotBlank(message = "exchange es obligatorio")
        @Pattern(regexp = "^[a-zA-Z0-9._-]{1,255}$",
                message = "exchange solo admite letras, numeros, punto, guion y guion bajo")
        String exchange,
        @NotBlank(message = "exchangeTipo es obligatorio")
        @Pattern(regexp = "^(direct|topic)$",
                message = "exchangeTipo debe ser direct o topic")
        String exchangeTipo,
        @NotBlank(message = "routingKey es obligatoria")
        @Pattern(regexp = "^[a-zA-Z0-9._*#:~-]{1,255}$",
                message = "routingKey admite letras, numeros, punto, guion, #, * y :")
        String routingKey) {
}
