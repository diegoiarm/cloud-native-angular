package cl.duoc.dsy1107.pedidosapi.dto;

import java.util.List;

/**
 * Respuesta del Laboratorio 1: indica qué routing keys se publicaron y qué colas
 * quedaron marcadas para fallar, para ubicar después la evidencia en la Management UI.
 */
public record LabAceptarPedidoResponse(
        String mensaje,
        List<String> publicadosEn,
        List<String> colasConFallo) {
}
