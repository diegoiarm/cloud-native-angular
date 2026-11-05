package cl.duoc.dsy1107.productorsensores.exception;

/** El broker no respondió al publicar: la API lo traduce a HTTP 503. */
public class KafkaNoDisponibleException extends RuntimeException {

    public KafkaNoDisponibleException(String mensaje, Throwable causa) {
        super(mensaje, causa);
    }
}
