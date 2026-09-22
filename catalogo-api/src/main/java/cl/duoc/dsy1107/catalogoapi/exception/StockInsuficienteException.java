package cl.duoc.dsy1107.catalogoapi.exception;

public class StockInsuficienteException extends RuntimeException {

    public StockInsuficienteException(String producto, int disponible, int solicitado) {
        super("Stock insuficiente para " + producto + ": disponible " + disponible
                + ", solicitado " + solicitado);
    }
}
