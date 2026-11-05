package cl.duoc.dsy1107.kafkaadminapi.service;

import cl.duoc.dsy1107.kafkaadminapi.dto.ClusterResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.GrupoResumen;
import cl.duoc.dsy1107.kafkaadminapi.dto.LagResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.TopicoResumen;
import java.util.List;

/**
 * Puerto de administración: permite probar las reglas de negocio sin un broker
 * y deja el AdminClient aislado en el adaptador.
 */
public interface KafkaAdminPort {

    ClusterResponse describirCluster();

    List<TopicoResumen> listarTopicos();

    TopicoResumen describirTopico(String nombre);

    void crearTopico(String nombre, int particiones, int replicas, long retencionMs);

    void eliminarTopico(String nombre);

    List<GrupoResumen> listarGrupos();

    LagResponse lag(String grupo);
}
