package cl.duoc.dsy1107.rabbitadminapi.dto;

/**
 * Regla única de nombres para queues y exchanges, compartida por los DTOs y las
 * variables de ruta: evita nombres vacíos, espacios o caracteres que el broker
 * o la Management UI interpretan de forma especial.
 */
public final class Nombres {

    public static final String PATRON = "^[a-zA-Z0-9._-]{1,255}$";
    public static final String MENSAJE =
            "solo admite letras, numeros, punto, guion y guion bajo (1 a 255)";

    private Nombres() {
    }
}
