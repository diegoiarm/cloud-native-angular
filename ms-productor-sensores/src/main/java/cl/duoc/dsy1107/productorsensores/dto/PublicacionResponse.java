package cl.duoc.dsy1107.productorsensores.dto;

/** Respuesta 202: el broker confirmó la publicación (no el procesamiento). */
public record PublicacionResponse(
        String sensorId,
        int particion,
        long offset,
        boolean alertaPublicada) {
}
