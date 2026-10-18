package cl.duoc.dsy1107.rabbitadminapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.dsy1107.rabbitadminapi.dto.ColaRequest;
import cl.duoc.dsy1107.rabbitadminapi.dto.ExchangeRequest;
import cl.duoc.dsy1107.rabbitadminapi.exception.AdminRabbitException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.ExchangeTypes;
import org.springframework.amqp.core.Queue;
import org.springframework.http.HttpStatus;

/** Reglas de RabbitAdminService sin Spring ni broker. */
class RabbitAdminServiceTests {

    private static final String PROTEGIDA =
            "^pedidos360\\.(notificaciones|cocina|documentos)\\.(queue|retry|dlq)$"
                    + "|^pedidos360\\.(direct|topic|dlx)\\.exchange$";

    private final AmqpAdmin amqpAdmin = mock(AmqpAdmin.class);
    private final RabbitAdminService service = new RabbitAdminService(amqpAdmin, PROTEGIDA);

    @Test
    void crearColaTraduceTtlMaxLengthYDlxEnArgumentos() {
        service.crearCola(new ColaRequest("lab.demo.queue", true, 60000, 100, "lab.dlx"));

        ArgumentCaptor<Queue> cola = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(cola.capture());
        assertThat(cola.getValue().isDurable()).isTrue();
        assertThat(cola.getValue().getArguments())
                .containsEntry("x-message-ttl", 60000)
                .containsEntry("x-max-length", 100L)
                .containsEntry("x-dead-letter-exchange", "lab.dlx");
    }

    @Test
    void crearColaSinDurableLaDeclaraDuradera() {
        service.crearCola(new ColaRequest("lab.demo.queue", null, null, null, null));

        ArgumentCaptor<Queue> cola = ArgumentCaptor.forClass(Queue.class);
        verify(amqpAdmin).declareQueue(cola.capture());
        assertThat(cola.getValue().isDurable()).isTrue();
        assertThat(cola.getValue().getArguments()).isEmpty();
    }

    @Test
    void noRedeclaraUnaColaProtegida() {
        assertThatThrownBy(() -> service.crearCola(
                        new ColaRequest("pedidos360.cocina.queue", true, null, null, null)))
                .isInstanceOf(AdminRabbitException.class)
                .extracting(e -> ((AdminRabbitException) e).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void crearExchangeTopic() {
        service.crearExchange(new ExchangeRequest("lab.demo.exchange", "topic", true));

        ArgumentCaptor<Exchange> exchange = ArgumentCaptor.forClass(Exchange.class);
        verify(amqpAdmin).declareExchange(exchange.capture());
        assertThat(exchange.getValue().getType()).isEqualTo(ExchangeTypes.TOPIC);
    }

    @Test
    void brokerCaidoDevuelve503() {
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused")))
                .when(amqpAdmin).declareExchange(any());

        assertThatThrownBy(() -> service.crearExchange(
                        new ExchangeRequest("lab.demo.exchange", "direct", true)))
                .isInstanceOf(AdminRabbitException.class)
                .extracting(e -> ((AdminRabbitException) e).getStatus())
                .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
    }

    @Test
    void purgarColaInexistenteDevuelve404() {
        when(amqpAdmin.getQueueProperties("no.existe")).thenReturn(null);

        assertThatThrownBy(() -> service.purgarCola("no.existe"))
                .isInstanceOf(AdminRabbitException.class)
                .extracting(e -> ((AdminRabbitException) e).getStatus())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }
}
