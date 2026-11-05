package cl.duoc.dsy1107.kafkaadminapi.dto;

/** Diferencia entre el último offset confirmado y el final de la partición. */
public record LagParticion(
        String topico,
        int particion,
        long offsetActual,
        long offsetFinal,
        long lag) {
}
