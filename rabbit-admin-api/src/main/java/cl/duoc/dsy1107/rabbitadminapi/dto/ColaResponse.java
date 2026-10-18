package cl.duoc.dsy1107.rabbitadminapi.dto;

/**
 * Estado de una cola según el broker. mensajesReady son los mensajes que esperan
 * consumer; los Unacked se revisan en la Management UI o con monitor_queues.py.
 */
public record ColaResponse(String nombre, int mensajesReady, int consumers) {
}
