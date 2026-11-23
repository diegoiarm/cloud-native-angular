package cl.duoc.dsy1107.monitorsensores.consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import cl.duoc.dsy1107.monitorsensores.dto.LecturaRecibida;
import cl.duoc.dsy1107.monitorsensores.service.EmisorEventos;
import cl.duoc.dsy1107.monitorsensores.service.MemoriaLecturas;
import cl.duoc.dsy1107.monitorsensores.service.PublicadorAlertas;
import cl.duoc.dsy1107.monitorsensores.service.ReglasReaccion;
import cl.duoc.dsy1107.sensores.dto.AlertaSensor;
import cl.duoc.dsy1107.sensores.dto.LecturaSensor;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class TemperaturaConsumerTests {

    private final MemoriaLecturas memoria = mock(MemoriaLecturas.class);
    private final PublicadorAlertas publicador = mock(PublicadorAlertas.class);
    private final EmisorEventos emisor = mock(EmisorEventos.class);
    private final TemperaturaConsumer consumer = new TemperaturaConsumer(
            memoria, new ReglasReaccion(30.0, 5.0), publicador, emisor, "sensor-fallo");

    @Test
    void guardaLaLecturaConParticionYOffsetYLaEmite() {
        consumer.consumir(registro("sensor-A", 22.4, 7L));

        ArgumentCaptor<LecturaRecibida> captor = ArgumentCaptor.forClass(LecturaRecibida.class);
        verify(memoria).agregar(captor.capture());
        assertThat(captor.getValue().sensorId()).isEqualTo("sensor-A");
        assertThat(captor.getValue().particion()).isEqualTo(1);
        assertThat(captor.getValue().offset()).isEqualTo(10L);
        verify(emisor).emitir("lectura", captor.getValue());
        verify(publicador, never()).publicar(any());
    }

    @Test
    void reaccionaPublicandoAlertaCuandoSuperaElUmbral() {
        consumer.consumir(registro("sensor-B", 31.2, 4L));

        ArgumentCaptor<AlertaSensor> captor = ArgumentCaptor.forClass(AlertaSensor.class);
        verify(publicador).publicar(captor.capture());
        assertThat(captor.getValue().sensorId()).isEqualTo("sensor-B");
        assertThat(captor.getValue().severidad()).isEqualTo("ALTA");
    }

    @Test
    void siFallaLaAlertaNoGuardaLaLecturaParaQueElReintentoNoDuplique() {
        doThrow(new IllegalStateException("broker caido")).when(publicador).publicar(any());

        assertThatThrownBy(() -> consumer.consumir(registro("sensor-B", 31.2, 4L)))
                .isInstanceOf(IllegalStateException.class);
        verify(memoria, never()).agregar(any());
        verify(emisor, never()).emitir(eq("lectura"), any());
    }

    @Test
    void sensorFalloLanzaExcepcionParaDemostrarRetryYDlt() {
        assertThatThrownBy(() -> consumer.consumir(registro("sensor-fallo", 99.9, 999L)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("fallo controlado");
        verify(publicador, never()).publicar(any());
    }

    @Test
    void valorNuloLanzaExcepcion() {
        ConsumerRecord<String, LecturaSensor> registro =
                new ConsumerRecord<>("sensores-temperatura", 0, 1L, "sensor-A", null);

        assertThatThrownBy(() -> consumer.consumir(registro))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private static ConsumerRecord<String, LecturaSensor> registro(
            String sensorId, double temperatura, long secuencia) {
        return new ConsumerRecord<>(
                "sensores-temperatura",
                1,
                10L,
                sensorId,
                new LecturaSensor(sensorId, temperatura, "C", secuencia));
    }
}
