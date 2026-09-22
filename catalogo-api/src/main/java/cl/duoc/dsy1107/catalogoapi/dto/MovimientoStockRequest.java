package cl.duoc.dsy1107.catalogoapi.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

// Cuerpo de /api/catalog/stock/descontar y /reponer (contrato usado por pedidos-api).
public record MovimientoStockRequest(@NotEmpty List<@Valid Item> items) {

    public record Item(@NotNull Long productoId, @NotNull @Min(1) Integer cantidad) {
    }
}
