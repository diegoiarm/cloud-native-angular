package cl.duoc.dsy1107.sensores.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

/**
 * Contrato único de una lectura de sensor (documento 01 S13).
 *
 * Se repite en el productor y en el monitor con el mismo paquete y nombre para
 * que el type header de JsonSerializer coincida con JsonDeserializer.
 */
public record LecturaSensor(
        @NotBlank(message = "sensorId es obligatorio") String sensorId,
        @NotNull(message = "temperatura es obligatoria") Double temperatura,
        @NotBlank(message = "unidad es obligatoria") String unidad,
        @NotNull(message = "secuencia es obligatoria")
        @PositiveOrZero(message = "secuencia no puede ser negativa") Long secuencia) {
}
