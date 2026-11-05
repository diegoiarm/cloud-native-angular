package cl.duoc.dsy1107.kafkaadminapi.dto;

import java.util.List;

/** Cluster ID, controller y brokers del cluster. */
public record ClusterResponse(
        String clusterId,
        int controller,
        List<BrokerResumen> brokers) {
}
