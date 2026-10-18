package cl.duoc.dsy1107.rabbitadminapi.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

/**
 * Creación de una cola. Los argumentos opcionales se validan antes de llegar al
 * broker: un TTL o un max-length en cero o negativos son configuraciones inválidas.
 */
public record ColaRequest(
        @NotBlank(message = "nombre es obligatorio")
        @Pattern(regexp = Nombres.PATRON, message = "nombre " + Nombres.MENSAJE)
        String nombre,
        Boolean durable,
        @Positive(message = "ttlMs debe ser mayor que 0")
        @Max(value = 86_400_000, message = "ttlMs no puede superar 24 horas")
        Integer ttlMs,
        @Positive(message = "maxLength debe ser mayor que 0")
        @Max(value = 1_000_000, message = "maxLength no puede superar 1000000")
        Integer maxLength,
        @Pattern(regexp = Nombres.PATRON, message = "deadLetterExchange " + Nombres.MENSAJE)
        String deadLetterExchange) {
}
