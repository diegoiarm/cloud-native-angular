package cl.duoc.dsy1107.monitorsensores.service;

import static org.assertj.core.api.Assertions.assertThat;

import cl.duoc.dsy1107.monitorsensores.dto.LecturaRecibida;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class MemoriaLecturasTests {

    @Test
    void conservaSoloLasUltimas50LecturasYLasMasRecientesPrimero() {
        MemoriaLecturas memoria = new MemoriaLecturas(50);

        for (int i = 0; i < 60; i++) {
            memoria.agregar(new LecturaRecibida(
                    "sensor-A", 20.0 + i, "C", (long) i, 0, i, Instant.now()));
        }

        assertThat(memoria.total()).isEqualTo(50);
        assertThat(memoria.listar()).hasSize(50);
        assertThat(memoria.listar().get(0).secuencia()).isEqualTo(59L);
        assertThat(memoria.listar().get(49).secuencia()).isEqualTo(10L);
    }
}
