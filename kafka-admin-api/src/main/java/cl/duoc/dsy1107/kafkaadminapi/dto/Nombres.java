package cl.duoc.dsy1107.kafkaadminapi.dto;

/** Reglas de nombre de topic de Kafka, compartidas por controller y service. */
public final class Nombres {

    public static final String PATRON = "^[a-zA-Z0-9._-]{1,249}$";
    public static final String MENSAJE =
            "debe tener entre 1 y 249 caracteres y solo [a-zA-Z0-9._-]";

    private Nombres() {
    }
}
