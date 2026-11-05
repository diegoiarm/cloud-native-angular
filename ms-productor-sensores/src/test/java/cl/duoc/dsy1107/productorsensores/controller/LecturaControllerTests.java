package cl.duoc.dsy1107.productorsensores.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.sensores.dto.AlertaSensor;
import cl.duoc.dsy1107.sensores.dto.LecturaSensor;
import java.util.concurrent.CompletableFuture;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LecturaControllerTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    KafkaTemplate<String, Object> kafkaTemplate;

    @BeforeEach
    void brokerConfirma() {
        SendResult<String, Object> resultado = mock(SendResult.class);
        RecordMetadata metadata = mock(RecordMetadata.class);
        when(metadata.partition()).thenReturn(1);
        when(metadata.offset()).thenReturn(7L);
        when(resultado.getRecordMetadata()).thenReturn(metadata);
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.completedFuture(resultado));
    }

    @Test
    void publicarDevuelve202ConParticionYOffset() throws Exception {
        mockMvc.perform(post("/api/lecturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sensorId\":\"sensor-A\",\"temperatura\":22.4,"
                                + "\"unidad\":\"C\",\"secuencia\":7}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.sensorId").value("sensor-A"))
                .andExpect(jsonPath("$.particion").value(1))
                .andExpect(jsonPath("$.offset").value(7))
                .andExpect(jsonPath("$.alertaPublicada").value(false));

        verify(kafkaTemplate).send(eq("sensores-temperatura"), eq("sensor-A"),
                any(LecturaSensor.class));
    }

    @Test
    void secuenciaNegativaDevuelve400() throws Exception {
        mockMvc.perform(post("/api/lecturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sensorId\":\"sensor-A\",\"temperatura\":22.4,"
                                + "\"unidad\":\"C\",\"secuencia\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(
                        org.hamcrest.Matchers.containsString("secuencia")));
    }

    @Test
    void sensorIdVacioDevuelve400() throws Exception {
        mockMvc.perform(post("/api/lecturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sensorId\":\"\",\"temperatura\":22.4,"
                                + "\"unidad\":\"C\",\"secuencia\":1}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void temperaturaSobreUmbralPublicaAlertaEnElSegundoTopico() throws Exception {
        mockMvc.perform(post("/api/lecturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sensorId\":\"sensor-B\",\"temperatura\":31.2,"
                                + "\"unidad\":\"C\",\"secuencia\":4}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.alertaPublicada").value(true));

        verify(kafkaTemplate).send(eq("sensores-alertas"), eq("sensor-B"),
                any(AlertaSensor.class));
    }

    @Test
    void kafkaCaidoDevuelve503() throws Exception {
        when(kafkaTemplate.send(anyString(), anyString(), any()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker")));

        mockMvc.perform(post("/api/lecturas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sensorId\":\"sensor-A\",\"temperatura\":22.4,"
                                + "\"unidad\":\"C\",\"secuencia\":1}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void estadoDevuelveConfiguracion() throws Exception {
        mockMvc.perform(get("/api/lecturas/estado"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.servicio").value("ms-productor-sensores"))
                .andExpect(jsonPath("$.topicoLecturas").value("sensores-temperatura"))
                .andExpect(jsonPath("$.topicoAlertas").value("sensores-alertas"))
                .andExpect(jsonPath("$.umbralAlertaC").value(30.0));
    }
}
