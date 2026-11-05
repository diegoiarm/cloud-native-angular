package cl.duoc.dsy1107.kafkaadminapi.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * Creación de un topic por REST (EP5: administración por código).
 * Los valores omitidos usan los defaults de la aplicación: 3 particiones,
 * 1 réplica y retención de 24 h.
 */
public record TopicoRequest(
        @NotBlank(message = "nombre es obligatorio")
        @Pattern(regexp = Nombres.PATRON, message = "nombre " + Nombres.MENSAJE)
        String nombre,
        @Min(value = 1, message = "particiones debe ser mayor o igual a 1")
        Integer particiones,
        @Min(value = 1, message = "replicas debe ser mayor o igual a 1")
        Integer replicas,
        @Min(value = 60000, message = "retencionMs debe ser al menos 60000")
        Long retencionMs) {
}
