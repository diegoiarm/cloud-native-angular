package cl.duoc.dsy1107.pedidosapi.dto;

import java.util.Map;

/**
 * Estado de una cola reportado por el broker (Management API equivalente):
 * messages_ready, messages_unacknowledged y consumers cuando existen.
 */
public record AdminColaResponse(String nombre, Map<String, Object> propiedades) {
}
