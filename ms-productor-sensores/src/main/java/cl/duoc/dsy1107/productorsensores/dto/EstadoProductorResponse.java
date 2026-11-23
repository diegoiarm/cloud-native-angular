package cl.duoc.dsy1107.productorsensores.dto;

/** Estado del productor para la comprobación del paso 6 del documento S13. */
public record EstadoProductorResponse(
        String servicio,
        String bootstrapServers,
        String topicoLecturas,
        int particiones,
        int replicas,
        long retencionMs) {
}
