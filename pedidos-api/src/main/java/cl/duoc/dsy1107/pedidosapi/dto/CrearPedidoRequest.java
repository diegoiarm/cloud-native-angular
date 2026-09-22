package cl.duoc.dsy1107.pedidosapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// El precio no viene del cliente: se toma del catálogo al crear el pedido.
public record CrearPedidoRequest(@NotEmpty List<@Valid Item> items) {

    public record Item(@NotNull Long productoId, @NotNull @Min(1) Integer cantidad) {
    }
}
