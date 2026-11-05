package cl.duoc.dsy1107.kafkaadminapi.service;

import cl.duoc.dsy1107.kafkaadminapi.dto.ClusterResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.GrupoResumen;
import cl.duoc.dsy1107.kafkaadminapi.dto.LagResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.Nombres;
import cl.duoc.dsy1107.kafkaadminapi.dto.TopicoRequest;
import cl.duoc.dsy1107.kafkaadminapi.dto.TopicoResumen;
import cl.duoc.dsy1107.kafkaadminapi.exception.AdminKafkaException;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Reglas del administrador de Kafka (EP5: administración por código).
 *
 * Valida entradas, aplica defaults y protege la topología del laboratorio:
 * sensores-temperatura, sensores-alertas, sus DLT y __consumer_offsets se
 * pueden consultar, pero no eliminar.
 */
@Service
public class KafkaAdminService {

    private final KafkaAdminPort puerto;
    private final Pattern topologiaProtegida;
    private final int particionesDefecto;
    private final int replicasDefecto;
    private final long retencionDefecto;

    public KafkaAdminService(
            KafkaAdminPort puerto,
            @Value("${kafka-admin.topologia-protegida:^sensores-(temperatura|alertas)(\\.DLT)?$|^__consumer_offsets$}")
            String topologiaProtegida,
            @Value("${kafka-admin.particiones:3}") int particionesDefecto,
            @Value("${kafka-admin.replicas:1}") int replicasDefecto,
            @Value("${kafka-admin.retencion-ms:86400000}") long retencionDefecto) {
        this.puerto = puerto;
        this.topologiaProtegida = Pattern.compile(topologiaProtegida);
        this.particionesDefecto = particionesDefecto;
        this.replicasDefecto = replicasDefecto;
        this.retencionDefecto = retencionDefecto;
    }

    public ClusterResponse describirCluster() {
        return puerto.describirCluster();
    }

    public List<TopicoResumen> listarTopicos() {
        return puerto.listarTopicos();
    }

    public TopicoResumen describirTopico(String nombre) {
        validarNombre(nombre);
        return puerto.describirTopico(nombre);
    }

    public void crearTopico(TopicoRequest solicitud) {
        validarNombre(solicitud.nombre());
        int particiones = solicitud.particiones() == null
                ? particionesDefecto : solicitud.particiones();
        int replicas = solicitud.replicas() == null
                ? replicasDefecto : solicitud.replicas();
        long retencion = solicitud.retencionMs() == null
                ? retencionDefecto : solicitud.retencionMs();

        if (particiones < 1) {
            throw new AdminKafkaException(
                    "particiones debe ser mayor o igual a 1", HttpStatus.BAD_REQUEST);
        }
        if (replicas < 1) {
            throw new AdminKafkaException(
                    "replicas debe ser mayor o igual a 1", HttpStatus.BAD_REQUEST);
        }
        if (retencion < 60000) {
            throw new AdminKafkaException(
                    "retencionMs debe ser al menos 60000", HttpStatus.BAD_REQUEST);
        }
        puerto.crearTopico(solicitud.nombre(), particiones, replicas, retencion);
    }

    public void eliminarTopico(String nombre) {
        validarNombre(nombre);
        if (topologiaProtegida.matcher(nombre).matches()) {
            throw new AdminKafkaException(
                    "El topic " + nombre + " pertenece a la topología del laboratorio; "
                            + "solo se puede consultar",
                    HttpStatus.CONFLICT);
        }
        puerto.eliminarTopico(nombre);
    }

    public List<GrupoResumen> listarGrupos() {
        return puerto.listarGrupos();
    }

    public LagResponse lag(String grupo) {
        validarNombre(grupo);
        return puerto.lag(grupo);
    }

    private static void validarNombre(String nombre) {
        if (nombre == null || !nombre.matches(Nombres.PATRON)) {
            throw new AdminKafkaException("nombre " + Nombres.MENSAJE, HttpStatus.BAD_REQUEST);
        }
    }
}
