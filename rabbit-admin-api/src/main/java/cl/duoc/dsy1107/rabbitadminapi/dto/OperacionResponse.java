package cl.duoc.dsy1107.rabbitadminapi.dto;

/** Confirmación de una operación del administrador: qué se hizo y sobre qué recurso. */
public record OperacionResponse(String mensaje, String recurso) {
}
