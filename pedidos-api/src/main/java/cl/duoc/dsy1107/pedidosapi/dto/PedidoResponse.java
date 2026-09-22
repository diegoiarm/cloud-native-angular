package cl.duoc.dsy1107.pedidosapi.dto;

import cl.duoc.dsy1107.pedidosapi.model.EstadoPedido;
import cl.duoc.dsy1107.pedidosapi.model.ItemPedido;
import cl.duoc.dsy1107.pedidosapi.model.Pedido;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

public record PedidoResponse(
        Long id,
        String cliente,
        EstadoPedido estado,
        // Estados a los que puede pasar; Angular lo usa para mostrar los botones.
        Set<EstadoPedido> siguientesEstados,
        BigDecimal total,
        Instant creadoEn,
        Instant actualizadoEn,
        List<Item> items) {

    public record Item(Long productoId, String nombreProducto, Integer cantidad,
            BigDecimal precioUnitario, BigDecimal subtotal) {

        static Item de(ItemPedido item) {
            return new Item(item.getProductoId(), item.getNombreProducto(), item.getCantidad(),
                    item.getPrecioUnitario(), item.getSubtotal());
        }
    }

    public static PedidoResponse de(Pedido pedido) {
        return new PedidoResponse(
                pedido.getId(),
                pedido.getClienteNombre(),
                pedido.getEstado(),
                pedido.getEstado().siguientes(),
                pedido.getTotal(),
                pedido.getCreadoEn(),
                pedido.getActualizadoEn(),
                pedido.getItems().stream().map(Item::de).toList());
    }
}
