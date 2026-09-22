package cl.duoc.dsy1107.pedidosapi.exception;

import cl.duoc.dsy1107.pedidosapi.model.EstadoPedido;

public class TransicionInvalidaException extends RuntimeException {

    public TransicionInvalidaException(EstadoPedido actual, EstadoPedido nuevo) {
        super("No se puede pasar de " + actual + " a " + nuevo
                + ". Estados permitidos: " + actual.siguientes());
    }
}
