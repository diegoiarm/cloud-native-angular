package cl.duoc.dsy1107.monitorsensores.service;

import static org.assertj.core.api.Assertions.assertThat;

import cl.duoc.dsy1107.sensores.dto.AlertaSensor;
import cl.duoc.dsy1107.sensores.dto.LecturaSensor;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class ReglasReaccionTests {

    private final ReglasReaccion reglas = new ReglasReaccion(30.0, 5.0);

    @Test
    void lecturaNormalNoGeneraAlerta() {
        assertThat(reglas.evaluar(lectura("sensor-A", 22.4, 1))).isEmpty();
    }

    @Test
    void alcanzarElUmbralGeneraAlertaAlta() {
        Optional<AlertaSensor> alerta = reglas.evaluar(lectura("sensor-B", 30.0, 1));

        assertThat(alerta).isPresent();
        assertThat(alerta.get().severidad()).isEqualTo("ALTA");
        assertThat(alerta.get().motivo()).startsWith("UMBRAL");
        assertThat(alerta.get().sensorId()).isEqualTo("sensor-B");
    }

    @Test
    void alzaBruscaDelMismoSensorGeneraAlertaMedia() {
        procesar(lectura("sensor-C", 20.0, 1));

        Optional<AlertaSensor> alerta = reglas.evaluar(lectura("sensor-C", 26.0, 2));

        assertThat(alerta).isPresent();
        assertThat(alerta.get().severidad()).isEqualTo("MEDIA");
        assertThat(alerta.get().motivo()).contains("ALZA_BRUSCA").contains("6.0");
    }

    @Test
    void elEstadoEsPorSensor() {
        procesar(lectura("sensor-A", 20.0, 1));

        // sensor-D no tiene lectura anterior: subir respecto de sensor-A no cuenta.
        assertThat(reglas.evaluar(lectura("sensor-D", 26.0, 1))).isEmpty();
    }

    @Test
    void umbralYAlzaJuntosQuedanEnUnaSolaAlertaAlta() {
        procesar(lectura("sensor-B", 22.1, 1));

        AlertaSensor alerta = reglas.evaluar(lectura("sensor-B", 31.2, 2)).orElseThrow();

        assertThat(alerta.severidad()).isEqualTo("ALTA");
        assertThat(alerta.motivo()).contains("UMBRAL").contains("ALZA_BRUSCA");
    }

    @Test
    void evaluarNoCambiaElEstadoHastaRegistrar() {
        procesar(lectura("sensor-C", 20.0, 1));

        // Un reintento evalúa dos veces la misma lectura y obtiene el mismo resultado.
        assertThat(reglas.evaluar(lectura("sensor-C", 26.0, 2))).isPresent();
        assertThat(reglas.evaluar(lectura("sensor-C", 26.0, 2))).isPresent();
    }

    private void procesar(LecturaSensor lectura) {
        reglas.evaluar(lectura);
        reglas.registrar(lectura);
    }

    private static LecturaSensor lectura(String sensorId, double temperatura, long secuencia) {
        return new LecturaSensor(sensorId, temperatura, "C", secuencia);
    }
}
