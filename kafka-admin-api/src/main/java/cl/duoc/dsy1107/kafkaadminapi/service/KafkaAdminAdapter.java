package cl.duoc.dsy1107.kafkaadminapi.service;

import cl.duoc.dsy1107.kafkaadminapi.dto.BrokerResumen;
import cl.duoc.dsy1107.kafkaadminapi.dto.ClusterResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.GrupoResumen;
import cl.duoc.dsy1107.kafkaadminapi.dto.LagParticion;
import cl.duoc.dsy1107.kafkaadminapi.dto.LagResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.ParticionResumen;
import cl.duoc.dsy1107.kafkaadminapi.dto.TopicoResumen;
import cl.duoc.dsy1107.kafkaadminapi.exception.AdminKafkaException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.Config;
import org.apache.kafka.clients.admin.ConfigEntry;
import org.apache.kafka.clients.admin.ConsumerGroupDescription;
import org.apache.kafka.clients.admin.ConsumerGroupListing;
import org.apache.kafka.clients.admin.DescribeClusterResult;
import org.apache.kafka.clients.admin.ListOffsetsResult.ListOffsetsResultInfo;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.admin.TopicDescription;
import org.apache.kafka.clients.consumer.OffsetAndMetadata;
import org.apache.kafka.common.KafkaFuture;
import org.apache.kafka.common.Node;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.config.ConfigResource;
import org.apache.kafka.common.config.TopicConfig;
import org.apache.kafka.common.errors.GroupIdNotFoundException;
import org.apache.kafka.common.errors.InvalidPartitionsException;
import org.apache.kafka.common.errors.InvalidReplicationFactorException;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.errors.UnknownTopicOrPartitionException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/** Adaptador real sobre AdminClient. Traduce errores de Kafka a HTTP. */
@Component
public class KafkaAdminAdapter implements KafkaAdminPort {

    private static final long TIMEOUT_SEGUNDOS = 8;

    private final AdminClient admin;

    public KafkaAdminAdapter(AdminClient admin) {
        this.admin = admin;
    }

    @Override
    public ClusterResponse describirCluster() {
        DescribeClusterResult cluster = admin.describeCluster();
        String clusterId = esperar(cluster.clusterId());
        Node controller = esperar(cluster.controller());
        Collection<Node> nodos = esperar(cluster.nodes());
        List<BrokerResumen> brokers = nodos.stream()
                .map(nodo -> new BrokerResumen(nodo.id(), nodo.host(), nodo.port()))
                .sorted(Comparator.comparingInt(BrokerResumen::id))
                .toList();
        return new ClusterResponse(clusterId, controller == null ? -1 : controller.id(), brokers);
    }

    @Override
    public List<TopicoResumen> listarTopicos() {
        Set<String> nombres = esperar(admin.listTopics().names());
        if (nombres.isEmpty()) {
            return List.of();
        }
        Map<String, TopicDescription> descripciones =
                esperar(admin.describeTopics(nombres).allTopicNames());
        return descripciones.values().stream()
                .sorted(Comparator.comparing(TopicDescription::name))
                .map(descripcion -> aResumen(descripcion, Map.of()))
                .toList();
    }

    @Override
    public TopicoResumen describirTopico(String nombre) {
        Map<String, TopicDescription> descripciones =
                esperar(admin.describeTopics(List.of(nombre)).allTopicNames());
        TopicDescription descripcion = descripciones.get(nombre);
        if (descripcion == null) {
            throw new AdminKafkaException("El topic no existe: " + nombre, HttpStatus.NOT_FOUND);
        }
        return aResumen(descripcion, configuraciones(nombre));
    }

    @Override
    public void crearTopico(String nombre, int particiones, int replicas, long retencionMs) {
        NewTopic nuevo = new NewTopic(nombre, particiones, (short) replicas)
                .configs(Map.of(TopicConfig.RETENTION_MS_CONFIG, String.valueOf(retencionMs)));
        esperar(admin.createTopics(List.of(nuevo)).all());
    }

    @Override
    public void eliminarTopico(String nombre) {
        esperar(admin.deleteTopics(List.of(nombre)).all());
    }

    @Override
    public List<GrupoResumen> listarGrupos() {
        Collection<ConsumerGroupListing> listado = esperar(admin.listConsumerGroups().valid());
        if (listado.isEmpty()) {
            return List.of();
        }
        List<String> ids = listado.stream().map(ConsumerGroupListing::groupId).toList();
        Map<String, ConsumerGroupDescription> descripciones =
                esperar(admin.describeConsumerGroups(ids).all());
        return descripciones.values().stream()
                .sorted(Comparator.comparing(ConsumerGroupDescription::groupId))
                .map(descripcion -> new GrupoResumen(
                        descripcion.groupId(),
                        descripcion.state().toString(),
                        descripcion.members().size(),
                        descripcion.isSimpleConsumerGroup()))
                .toList();
    }

    @Override
    public LagResponse lag(String grupo) {
        Map<TopicPartition, OffsetAndMetadata> comprometidos =
                esperar(admin.listConsumerGroupOffsets(grupo).partitionsToOffsetAndMetadata());
        if (comprometidos.isEmpty()) {
            throw new AdminKafkaException(
                    "El consumer group no existe o no tiene offsets confirmados: " + grupo,
                    HttpStatus.NOT_FOUND);
        }

        Map<TopicPartition, OffsetSpec> peticion = new HashMap<>();
        comprometidos.keySet().forEach(particion -> peticion.put(particion, OffsetSpec.latest()));
        Map<TopicPartition, ListOffsetsResultInfo> finales =
                esperar(admin.listOffsets(peticion).all());

        long lagTotal = 0;
        List<LagParticion> particiones = new ArrayList<>();
        for (Map.Entry<TopicPartition, OffsetAndMetadata> entrada : comprometidos.entrySet()) {
            long offsetActual = entrada.getValue().offset();
            ListOffsetsResultInfo info = finales.get(entrada.getKey());
            long offsetFinal = info == null ? offsetActual : info.offset();
            long lag = Math.max(0, offsetFinal - offsetActual);
            lagTotal += lag;
            particiones.add(new LagParticion(
                    entrada.getKey().topic(),
                    entrada.getKey().partition(),
                    offsetActual,
                    offsetFinal,
                    lag));
        }
        particiones.sort(Comparator.comparing(LagParticion::topico)
                .thenComparingInt(LagParticion::particion));
        return new LagResponse(grupo, lagTotal, particiones);
    }

    private Map<String, String> configuraciones(String nombre) {
        ConfigResource recurso = new ConfigResource(ConfigResource.Type.TOPIC, nombre);
        Map<ConfigResource, Config> mapa =
                esperar(admin.describeConfigs(List.of(recurso)).all());
        Config config = mapa.get(recurso);
        if (config == null) {
            return Map.of();
        }
        return config.entries().stream().collect(Collectors.toMap(
                ConfigEntry::name,
                ConfigEntry::value,
                (anterior, nuevo) -> anterior,
                TreeMap::new));
    }

    private static TopicoResumen aResumen(
            TopicDescription descripcion, Map<String, String> configuraciones) {
        List<ParticionResumen> particiones = descripcion.partitions().stream()
                .map(particion -> new ParticionResumen(
                        particion.partition(),
                        particion.leader() == null ? -1 : particion.leader().id(),
                        particion.replicas().stream().map(Node::id).toList(),
                        particion.isr().stream().map(Node::id).toList()))
                .toList();
        int factorReplicacion = particiones.stream()
                .mapToInt(particion -> particion.replicas().size())
                .max()
                .orElse(0);
        return new TopicoResumen(
                descripcion.name(),
                particiones.size(),
                factorReplicacion,
                particiones,
                configuraciones);
    }

    private static <T> T esperar(KafkaFuture<T> futuro) {
        try {
            return futuro.get(TIMEOUT_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new AdminKafkaException(
                    "Operación administrativa interrumpida", HttpStatus.SERVICE_UNAVAILABLE);
        } catch (ExecutionException e) {
            throw traducir(e);
        } catch (TimeoutException e) {
            throw new AdminKafkaException(
                    "Kafka no respondió en " + TIMEOUT_SEGUNDOS + " s",
                    HttpStatus.SERVICE_UNAVAILABLE);
        }
    }

    private static AdminKafkaException traducir(ExecutionException e) {
        Throwable causa = e.getCause() == null ? e : e.getCause();
        if (causa instanceof UnknownTopicOrPartitionException
                || causa instanceof GroupIdNotFoundException) {
            return new AdminKafkaException(causa.getMessage(), HttpStatus.NOT_FOUND);
        }
        if (causa instanceof TopicExistsException) {
            return new AdminKafkaException(causa.getMessage(), HttpStatus.CONFLICT);
        }
        if (causa instanceof InvalidPartitionsException
                || causa instanceof InvalidReplicationFactorException) {
            return new AdminKafkaException(causa.getMessage(), HttpStatus.BAD_REQUEST);
        }
        return new AdminKafkaException(
                "Kafka no disponible: " + causa.getMessage(),
                HttpStatus.SERVICE_UNAVAILABLE);
    }
}
