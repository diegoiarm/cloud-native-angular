package cl.duoc.dsy1107.productorsensores.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cl.duoc.dsy1107.productorsensores.dto.PublicacionResponse;
import cl.duoc.dsy1107.productorsensores.exception.KafkaNoDisponibleException;
import cl.duoc.dsy1107.sensores.dto.LecturaSensor;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

/** Reglas del productor sin Kafka real. */
class LecturaServiceTests {

    private final KafkaTemplate<String, Object> kafkaTemplate = mock(KafkaTemplate.class);
    private final LecturaService service = new LecturaService(
            kafkaTemplate, "localhost:9092", "sensores-temperatura", 3, 1, 86400000L);

    @BeforeEach
    void brokerConfirma() {
        SendResult<String, Object> resultado = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.partition()).thenReturn(2);
        when(metadata.offset()).thenReturn(15L);
        when(resultado.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(resultado));
    }

    @Test
    void usaSensorIdComoClaveYDevuelveParticionYOffset() {
        PublicacionResponse respuesta = service.publicar(
                new LecturaSensor("sensor-A", 22.4, "C", 7L));

        assertThat(respuesta.sensorId()).isEqualTo("sensor-A");
        assertThat(respuesta.particion()).isEqualTo(2);
        assertThat(respuesta.offset()).isEqualTo(15L);
        verify(kafkaTemplate).send(eq("sensores-temperatura"), eq("sensor-A"),
                any(LecturaSensor.class));
    }

    @Test
    void temperaturaAltaNoGeneraAlertaEnElProductor() {
        // La alerta la decide el monitor al consumir el evento (procesamiento reactivo).
        service.publicar(new LecturaSensor("sensor-B", 35.0, "C", 5L));

        verify(kafkaTemplate).send(eq("sensores-temperatura"), eq("sensor-B"),
                any(LecturaSensor.class));
        verify(kafkaTemplate, never()).send(eq("sensores-alertas"), anyString(), any());
    }

    @Test
    void kafkaCaidoLanzaExcepcionDeDominio() {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("caida")));

        assertThatThrownBy(() -> service.publicar(
                new LecturaSensor("sensor-A", 22.4, "C", 1L)))
                .isInstanceOf(KafkaNoDisponibleException.class);
    }
}
