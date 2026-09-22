package cl.duoc.dsy1107.pedidosapi.exception;

public class CatalogoNoDisponibleException extends RuntimeException {

    public CatalogoNoDisponibleException(Throwable causa) {
        super("El servicio de catálogo no está disponible", causa);
    }
}
