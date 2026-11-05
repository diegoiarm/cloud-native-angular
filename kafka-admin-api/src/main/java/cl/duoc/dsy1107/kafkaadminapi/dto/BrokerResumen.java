package cl.duoc.dsy1107.kafkaadminapi.dto;

/** Broker registrado en el cluster. */
public record BrokerResumen(int id, String host, int port) {
}
