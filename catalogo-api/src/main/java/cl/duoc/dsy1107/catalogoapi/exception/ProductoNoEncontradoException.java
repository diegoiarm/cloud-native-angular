package cl.duoc.dsy1107.catalogoapi.exception;

public class ProductoNoEncontradoException extends RuntimeException {

    public ProductoNoEncontradoException(Long id) {
        super("Producto " + id + " no encontrado");
    }
}
