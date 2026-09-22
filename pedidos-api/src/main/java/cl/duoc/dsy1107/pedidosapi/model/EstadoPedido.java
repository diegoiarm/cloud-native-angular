package cl.duoc.dsy1107.pedidosapi.model;

import java.util.EnumSet;
import java.util.Set;

/**
 * Estados del pedido y transiciones permitidas:
 * CREADO -> ACEPTADO -> EN_PREPARACION -> DESPACHADO -> ENTREGADO
 * CREADO / ACEPTADO / EN_PREPARACION -> CANCELADO
 *
 * Al no existir CREADO -> DESPACHADO, un pedido no puede despacharse sin haber sido aceptado.
 */
public enum EstadoPedido {
    CREADO,
    ACEPTADO,
    EN_PREPARACION,
    DESPACHADO,
    ENTREGADO,
    CANCELADO;

    public Set<EstadoPedido> siguientes() {
        return switch (this) {
            case CREADO -> EnumSet.of(ACEPTADO, CANCELADO);
            case ACEPTADO -> EnumSet.of(EN_PREPARACION, CANCELADO);
            case EN_PREPARACION -> EnumSet.of(DESPACHADO, CANCELADO);
            case DESPACHADO -> EnumSet.of(ENTREGADO);
            case ENTREGADO, CANCELADO -> EnumSet.noneOf(EstadoPedido.class);
        };
    }

    public boolean puedeCambiarA(EstadoPedido nuevo) {
        return siguientes().contains(nuevo);
    }

    // Estados en que el stock ya fue descontado (al cancelar hay que reponerlo).
    public boolean stockDescontado() {
        return this == ACEPTADO || this == EN_PREPARACION;
    }
}
