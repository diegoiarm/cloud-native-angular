package cl.duoc.dsy1107.kafkaadminapi.dto;

/** Respuesta de una operación administrativa (crear/eliminar). */
public record OperacionResponse(String mensaje, String recurso) {
}
