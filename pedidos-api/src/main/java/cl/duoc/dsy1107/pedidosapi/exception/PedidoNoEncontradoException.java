package cl.duoc.dsy1107.pedidosapi.exception;

public class PedidoNoEncontradoException extends RuntimeException {

    public PedidoNoEncontradoException(Long id) {
        super("Pedido " + id + " no encontrado");
    }
}
