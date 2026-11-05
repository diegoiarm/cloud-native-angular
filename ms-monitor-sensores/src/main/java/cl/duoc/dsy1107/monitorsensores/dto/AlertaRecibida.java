package cl.duoc.dsy1107.monitorsensores.dto;

import java.time.Instant;

/** Alerta tal como llegó desde Kafka, con partición y offset. */
public record AlertaRecibida(
        String sensorId,
        Double temperatura,
        String unidad,
        Long secuencia,
        String motivo,
        String severidad,
        Instant generadaEn,
        int particion,
        long offset,
        Instant recibidaEn) {
}
