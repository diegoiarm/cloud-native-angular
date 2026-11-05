package cl.duoc.dsy1107.kafkaadminapi.dto;

import java.util.List;
import java.util.Map;

/** Topico con particiones, factor de replicación y configuraciones. */
public record TopicoResumen(
        String nombre,
        int particiones,
        int factorReplicacion,
        List<ParticionResumen> detalleParticiones,
        Map<String, String> configuraciones) {
}
