package cl.duoc.dsy1107.pedidosapi.exception;

public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(String detalle) {
        super("Stock insuficiente para aceptar el pedido"
                + (detalle == null || detalle.isBlank() ? "" : ": " + detalle));
    }
}
