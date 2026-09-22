package cl.duoc.dsy1107.pedidosapi.exception;

public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(Long productoId) {
        super(productoId == null
                ? "Uno de los productos del pedido no existe en el catálogo"
                : "Producto " + productoId + " no existe en el catálogo");
    }
}
