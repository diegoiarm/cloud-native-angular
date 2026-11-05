package cl.duoc.dsy1107.kafkaadminapi.dto;

import java.util.List;

/** Lag total y por partición de un consumer group (EP6: monitoreo). */
public record LagResponse(
        String grupo,
        long lagTotal,
        List<LagParticion> particiones) {
}
