package cl.duoc.dsy1107.kafkaadminapi.controller;

import cl.duoc.dsy1107.kafkaadminapi.dto.ClusterResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.GrupoResumen;
import cl.duoc.dsy1107.kafkaadminapi.dto.LagResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.Nombres;
import cl.duoc.dsy1107.kafkaadminapi.dto.OperacionResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.TopicoRequest;
import cl.duoc.dsy1107.kafkaadminapi.dto.TopicoResumen;
import cl.duoc.dsy1107.kafkaadminapi.service.KafkaAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administración de Kafka por REST. Solo el rol Admin.
 *
 *   GET    /api/admin/kafka/cluster                 cluster, controller y brokers
 *   GET    /api/admin/kafka/topics                  topics y factoring de replicación
 *   GET    /api/admin/kafka/topics/{nombre}         detalle y configuraciones
 *   POST   /api/admin/kafka/topics                  crear topic
 *   DELETE /api/admin/kafka/topics/{nombre}         eliminar topic no protegido
 *   GET    /api/admin/kafka/groups                  consumer groups
 *   GET    /api/admin/kafka/groups/{grupo}/lag      offsets y lag por partición
 *
 * La lógica vive en KafkaAdminService; aquí solo se validan las entradas.
 */
@RestController
@RequestMapping("/api/admin/kafka")
@PreAuthorize("hasRole('Admin')")
public class KafkaAdminController {

    private final KafkaAdminService service;

    public KafkaAdminController(KafkaAdminService service) {
        this.service = service;
    }

    @GetMapping("/cluster")
    public ClusterResponse cluster() {
        return service.describirCluster();
    }

    @GetMapping("/topics")
    public List<TopicoResumen> topicos() {
        return service.listarTopicos();
    }

    @GetMapping("/topics/{nombre}")
    public TopicoResumen topico(@PathVariable
            @Pattern(regexp = Nombres.PATRON, message = "nombre " + Nombres.MENSAJE) String nombre) {
        return service.describirTopico(nombre);
    }

    @PostMapping("/topics")
    @ResponseStatus(HttpStatus.CREATED)
    public OperacionResponse crearTopico(@Valid @RequestBody TopicoRequest solicitud) {
        service.crearTopico(solicitud);
        return new OperacionResponse("Topic creado", solicitud.nombre());
    }

    @DeleteMapping("/topics/{nombre}")
    public OperacionResponse eliminarTopico(@PathVariable
            @Pattern(regexp = Nombres.PATRON, message = "nombre " + Nombres.MENSAJE) String nombre) {
        service.eliminarTopico(nombre);
        return new OperacionResponse("Topic eliminado", nombre);
    }

    @GetMapping("/groups")
    public List<GrupoResumen> grupos() {
        return service.listarGrupos();
    }

    @GetMapping("/groups/{grupo}/lag")
    public LagResponse lag(@PathVariable
            @Pattern(regexp = Nombres.PATRON, message = "grupo " + Nombres.MENSAJE) String grupo) {
        return service.lag(grupo);
    }
}
