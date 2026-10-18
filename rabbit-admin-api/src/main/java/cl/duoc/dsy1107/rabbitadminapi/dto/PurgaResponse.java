package cl.duoc.dsy1107.rabbitadminapi.dto;

/** Resultado de vaciar una cola: cuántos mensajes Ready se eliminaron. */
public record PurgaResponse(String cola, int mensajesEliminados) {
}
