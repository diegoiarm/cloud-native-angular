package cl.duoc.dsy1107.monitorsensores.dto;

/** Estado del monitor para la comprobación del paso 6 del documento S13. */
public record EstadoMonitorResponse(
        String servicio,
        String grupo,
        String topicoLecturas,
        String topicoAlertas,
        int lecturasEnMemoria,
        int alertasEnMemoria) {
}
