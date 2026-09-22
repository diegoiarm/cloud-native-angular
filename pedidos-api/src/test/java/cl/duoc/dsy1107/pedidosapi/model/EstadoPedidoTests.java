package cl.duoc.dsy1107.pedidosapi.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EstadoPedidoTests {

    @Test
    void noSePuedeDespacharSinAceptar() {
        assertThat(EstadoPedido.CREADO.puedeCambiarA(EstadoPedido.DESPACHADO)).isFalse();
        assertThat(EstadoPedido.CREADO.puedeCambiarA(EstadoPedido.EN_PREPARACION)).isFalse();
    }

    @Test
    void flujoCompletoEsValido() {
        assertThat(EstadoPedido.CREADO.puedeCambiarA(EstadoPedido.ACEPTADO)).isTrue();
        assertThat(EstadoPedido.ACEPTADO.puedeCambiarA(EstadoPedido.EN_PREPARACION)).isTrue();
        assertThat(EstadoPedido.EN_PREPARACION.puedeCambiarA(EstadoPedido.DESPACHADO)).isTrue();
        assertThat(EstadoPedido.DESPACHADO.puedeCambiarA(EstadoPedido.ENTREGADO)).isTrue();
    }

    @Test
    void despachadoYEstadosFinalesNoSeCancelan() {
        assertThat(EstadoPedido.DESPACHADO.puedeCambiarA(EstadoPedido.CANCELADO)).isFalse();
        assertThat(EstadoPedido.ENTREGADO.siguientes()).isEmpty();
        assertThat(EstadoPedido.CANCELADO.siguientes()).isEmpty();
    }
}
