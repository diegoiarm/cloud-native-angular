package cl.duoc.dsy1107.pedidosapi.dto;

/**
 * Confirmación de las operaciones del administrador de RabbitMQ (EP4-09):
 * qué se hizo y sobre qué recurso, para mostrarlo en el video.
 */
public record AdminRabbitResponse(String mensaje, String recurso) {
}
