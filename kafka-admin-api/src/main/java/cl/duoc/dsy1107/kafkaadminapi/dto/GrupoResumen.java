package cl.duoc.dsy1107.kafkaadminapi.dto;

/** Consumer group y su estado. */
public record GrupoResumen(
        String grupo,
        String estado,
        int miembros,
        boolean simple) {
}
