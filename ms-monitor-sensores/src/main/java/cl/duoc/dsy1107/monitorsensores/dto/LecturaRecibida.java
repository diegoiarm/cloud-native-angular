package cl.duoc.dsy1107.monitorsensores.dto;

import java.time.Instant;

/** Lectura tal como llegó desde Kafka, con partición y offset para la evidencia. */
public record LecturaRecibida(
        String sensorId,
        Double temperatura,
        String unidad,
        Long secuencia,
        int particion,
        long offset,
        Instant recibidaEn) {
}
