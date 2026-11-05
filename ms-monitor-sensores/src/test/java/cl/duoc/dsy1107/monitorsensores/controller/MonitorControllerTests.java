package cl.duoc.dsy1107.monitorsensores.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.monitorsensores.dto.AlertaRecibida;
import cl.duoc.dsy1107.monitorsensores.dto.LecturaRecibida;
import cl.duoc.dsy1107.monitorsensores.service.MemoriaAlertas;
import cl.duoc.dsy1107.monitorsensores.service.MemoriaLecturas;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class MonitorControllerTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    MemoriaLecturas lecturas;

    @Autowired
    MemoriaAlertas alertas;

    @Test
    void lecturasDevuelveLoRecibido() throws Exception {
        lecturas.agregar(new LecturaRecibida(
                "sensor-A", 22.4, "C", 7L, 1, 10L, Instant.now()));

        mockMvc.perform(get("/api/monitor/lecturas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sensorId").value("sensor-A"))
                .andExpect(jsonPath("$[0].particion").value(1))
                .andExpect(jsonPath("$[0].offset").value(10));
    }

    @Test
    void alertasDevuelveLoRecibido() throws Exception {
        alertas.agregar(new AlertaRecibida(
                "sensor-B", 31.2, "C", 4L, "umbral", "ALTA",
                Instant.now(), 2, 15L, Instant.now()));

        mockMvc.perform(get("/api/monitor/alertas"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].sensorId").value("sensor-B"))
                .andExpect(jsonPath("$[0].severidad").value("ALTA"));
    }

    @Test
    void estadoDevuelveGrupoYContadores() throws Exception {
        mockMvc.perform(get("/api/monitor/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicio").value("ms-monitor-sensores"))
                .andExpect(jsonPath("$.grupo").value("grupo-monitor-s13"))
                .andExpect(jsonPath("$.topicoLecturas").value("sensores-temperatura"))
                .andExpect(jsonPath("$.topicoAlertas").value("sensores-alertas"));
    }
}
