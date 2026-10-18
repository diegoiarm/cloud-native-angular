package cl.duoc.dsy1107.rabbitadminapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Creación o eliminación de un binding (exchange -> cola con routing key).
 * Los comodines * y # solo tienen sentido en un exchange topic; el servicio
 * rechaza su uso con exchangeTipo direct.
 */
public record BindingRequest(
        @NotBlank(message = "cola es obligatoria")
        @Pattern(regexp = Nombres.PATRON, message = "cola " + Nombres.MENSAJE)
        String cola,
        @NotBlank(message = "exchange es obligatorio")
        @Pattern(regexp = Nombres.PATRON, message = "exchange " + Nombres.MENSAJE)
        String exchange,
        @NotBlank(message = "exchangeTipo es obligatorio")
        @Pattern(regexp = "^(direct|topic)$", message = "exchangeTipo debe ser direct o topic")
        String exchangeTipo,
        @NotBlank(message = "routingKey es obligatoria")
        @Pattern(regexp = "^[a-zA-Z0-9._*#-]{1,255}$",
                message = "routingKey admite letras, numeros, punto, guion, * y #")
        String routingKey) {
}
