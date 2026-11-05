package cl.duoc.dsy1107.kafkaadminapi.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.kafkaadminapi.config.JwtAuthorityConverter;
import cl.duoc.dsy1107.kafkaadminapi.dto.LagParticion;
import cl.duoc.dsy1107.kafkaadminapi.dto.LagResponse;
import cl.duoc.dsy1107.kafkaadminapi.dto.ParticionResumen;
import cl.duoc.dsy1107.kafkaadminapi.dto.TopicoResumen;
import cl.duoc.dsy1107.kafkaadminapi.service.KafkaAdminPort;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** Endpoints del administrador: seguridad, validación y delegación al puerto. */
@SpringBootTest(properties = "spring.kafka.admin.auto-startup=false")
@AutoConfigureMockMvc
class KafkaAdminControllerTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    KafkaAdminPort port;

    private static JwtRequestPostProcessor admin() {
        return jwt()
                .jwt(j -> j.claim("scp", "Pedidos.Read").claim("roles", List.of("Admin")))
                .authorities(new JwtAuthorityConverter());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/admin/kafka/topics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conScopeSinRolAdminDevuelve403() throws Exception {
        mockMvc.perform(get("/api/admin/kafka/topics")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_Pedidos.Read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void listarTopicosDevuelveParticionesYReplicas() throws Exception {
        when(port.listarTopicos()).thenReturn(List.of(new TopicoResumen(
                "sensores-temperatura",
                3,
                3,
                List.of(new ParticionResumen(0, 1, List.of(1, 2, 3), List.of(1, 2, 3))),
                Map.of())));

        mockMvc.perform(get("/api/admin/kafka/topics").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("sensores-temperatura"))
                .andExpect(jsonPath("$[0].particiones").value(3))
                .andExpect(jsonPath("$[0].factorReplicacion").value(3));
    }

    @Test
    void crearTopicoUsaDefaultsDelServicio() throws Exception {
        mockMvc.perform(post("/api/admin/kafka/topics")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"lab.auditoria\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Topic creado"))
                .andExpect(jsonPath("$.recurso").value("lab.auditoria"));

        verify(port).crearTopico("lab.auditoria", 3, 1, 86400000L);
    }

    @Test
    void crearTopicoConParticionesInvalidasDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/kafka/topics")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"lab.auditoria\",\"particiones\":0}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(port);
    }

    @Test
    void eliminarTopicProtegidoDevuelve409() throws Exception {
        mockMvc.perform(delete("/api/admin/kafka/topics/sensores-temperatura").with(admin()))
                .andExpect(status().isConflict());

        verifyNoInteractions(port);
    }

    @Test
    void lagDevuelveOffsetYTotal() throws Exception {
        when(port.lag("grupo-monitor-s13")).thenReturn(new LagResponse(
                "grupo-monitor-s13",
                4L,
                List.of(new LagParticion("sensores-temperatura", 0, 6L, 10L, 4L))));

        mockMvc.perform(get("/api/admin/kafka/groups/grupo-monitor-s13/lag").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupo").value("grupo-monitor-s13"))
                .andExpect(jsonPath("$.lagTotal").value(4))
                .andExpect(jsonPath("$.particiones[0].lag").value(4));
    }

    @Test
    void nombreConEspaciosDevuelve400() throws Exception {
        mockMvc.perform(get("/api/admin/kafka/topics/topic con espacios").with(admin()))
                .andExpect(status().is4xxClientError());

        verifyNoInteractions(port);
    }
}
