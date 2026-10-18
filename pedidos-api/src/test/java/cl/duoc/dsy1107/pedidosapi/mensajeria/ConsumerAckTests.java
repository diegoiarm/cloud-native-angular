package cl.duoc.dsy1107.pedidosapi.mensajeria;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import com.rabbitmq.client.Channel;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

/**
 * Confirmación manual de los consumers: ACK al procesar, ACK tras programar el
 * reintento y NACK sin requeue (hacia la DLQ) al agotar los intentos. Sin broker:
 * el canal y el RabbitTemplate son mocks.
 */
class ConsumerAckTests {

    private static final long TAG = 42L;
    private static final String EVENTO = "{\"pedidoId\":210,\"estado\":\"ACEPTADO\"}";

    private final RabbitTemplate rabbitTemplate = mock(RabbitTemplate.class);
    private final Channel channel = mock(Channel.class);
    private final CocinaConsumer cocina = new CocinaConsumer(new Reintentos(rabbitTemplate));

    private static Message mensaje(boolean falla, Integer intento) {
        MessageBuilder builder = MessageBuilder.withBody(EVENTO.getBytes(StandardCharsets.UTF_8));
        builder.setDeliveryTag(TAG);
        if (falla) {
            builder.setHeader(RabbitMQConfig.HEADER_FALLO_COCINA, true);
        }
        if (intento != null) {
            builder.setHeader(RabbitMQConfig.HEADER_INTENTOS, intento);
        }
        return builder.build();
    }

    @Test
    void procesadoConExitoHaceAck() throws Exception {
        cocina.imprimirTicket(mensaje(false, null), channel);

        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void primerFalloPublicaEnRetryYConfirmaElOriginal() throws Exception {
        cocina.imprimirTicket(mensaje(true, null), channel);

        ArgumentCaptor<Message> reintento = ArgumentCaptor.forClass(Message.class);
        verify(rabbitTemplate).send(eq(""), eq(RabbitMQConfig.COCINA_RETRY), reintento.capture());
        assertThat(reintento.getValue().getMessageProperties()
                .<Integer>getHeader(RabbitMQConfig.HEADER_INTENTOS)).isEqualTo(2);
        assertThat(new String(reintento.getValue().getBody(), StandardCharsets.UTF_8))
                .isEqualTo(EVENTO);
        verify(channel).basicAck(TAG, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void intentosAgotadosHaceNackSinRequeueHaciaLaDlq() throws Exception {
        cocina.imprimirTicket(mensaje(true, RabbitMQConfig.MAX_INTENTOS), channel);

        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
        verify(rabbitTemplate, never()).send(anyString(), anyString(), any(Message.class));
    }

    @Test
    void sinPoderPublicarElReintentoHaceNackEnVezDeQuedarUnacked() throws Exception {
        doThrow(new AmqpConnectException(new java.net.ConnectException("refused")))
                .when(rabbitTemplate).send(anyString(), anyString(), any(Message.class));

        cocina.imprimirTicket(mensaje(true, null), channel);

        verify(channel).basicNack(TAG, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void notificacionesYDocumentosConfirmanIgual() throws Exception {
        Reintentos reintentos = new Reintentos(rabbitTemplate);
        new NotificacionesConsumer(reintentos).notificar(mensaje(false, null), channel);
        new DocumentosConsumer(reintentos).emitirDocumentos(mensaje(false, null), channel);

        verify(channel, org.mockito.Mockito.times(2)).basicAck(TAG, false);
    }
}
