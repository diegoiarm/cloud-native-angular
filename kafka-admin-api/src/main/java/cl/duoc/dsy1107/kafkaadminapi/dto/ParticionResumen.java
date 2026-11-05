package cl.duoc.dsy1107.kafkaadminapi.dto;

import java.util.List;

/** Detalle de una partición: líder, réplicas e ISR (evidencia de RF3). */
public record ParticionResumen(
        int particion,
        int lider,
        List<Integer> replicas,
        List<Integer> isr) {
}
