package cl.duoc.dsy1107.sensores.dto;

import java.time.Instant;

/**
 * Evento del segundo topic, sensores-alertas (EP5: dos topics y flujos de negocio).
 *
 * Se genera cuando una lectura supera el umbral configurado. Mantiene el mismo
 * sensorId como clave para conservar el orden por sensor.
 */
public record AlertaSensor(
        String sensorId,
        Double temperatura,
        String unidad,
        Long secuencia,
        String motivo,
        String severidad,
        Instant generadaEn) {
}
