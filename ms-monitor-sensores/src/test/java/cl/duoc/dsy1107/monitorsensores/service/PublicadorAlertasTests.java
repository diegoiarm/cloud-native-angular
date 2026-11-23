package cl.duoc.dsy1107.monitorsensores.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.dsy1107.sensores.dto.AlertaSensor;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

class PublicadorAlertasTests {

    @SuppressWarnings("unchecked")
    private final KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final PublicadorAlertas publicador = new PublicadorAlertas(kafkaTemplate, "sensores-alertas");

    @Test
    void publicaEnElSegundoTopicoConSensorIdComoClave() {
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(mock(SendResult.class)));
        AlertaSensor alerta = alerta();

        publicador.publicar(alerta);

        verify(kafkaTemplate).send(eq("sensores-alertas"), eq("sensor-B"), eq(alerta));
        assertThat(publicador.publicadas()).isEqualTo(1);
    }

    @Test
    void siKafkaNoConfirmaLanzaExcepcionParaQueSeReintente() {
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker caido")));

        assertThatThrownBy(() -> publicador.publicar(alerta()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sensores-alertas");
        assertThat(publicador.publicadas()).isZero();
    }

    private static AlertaSensor alerta() {
        return new AlertaSensor("sensor-B", 31.2, "C", 4L, "UMBRAL", "ALTA", Instant.now());
    }
}
