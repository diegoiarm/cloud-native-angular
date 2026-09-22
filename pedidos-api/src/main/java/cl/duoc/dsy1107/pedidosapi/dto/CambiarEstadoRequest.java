package cl.duoc.dsy1107.pedidosapi.dto;

import cl.duoc.dsy1107.pedidosapi.model.EstadoPedido;
import jakarta.validation.constraints.NotNull;

public record CambiarEstadoRequest(@NotNull EstadoPedido estado) {
}
