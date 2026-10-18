package cl.duoc.dsy1107.pedidosapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.pedidosapi.mensajeria.PedidoEvento;
import cl.duoc.dsy1107.pedidosapi.mensajeria.PedidoEventoPublisher;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Laboratorio 1 (documento 04), Actividad B: el POST publica los dos eventos con
 * las cabeceras de fallo, sin token (ruta de laboratorio) y con validaciones.
 */
@SpringBootTest(properties = "pedidos360.lab.habilitado=true")
@AutoConfigureMockMvc
class LabControllerTests {

    private static final String CUERPO = """
            {"pedidoId":210,"clienteEmail":"prueba@example.com",
             "falloNotificaciones":true,"falloCocina":false,"falloDocumentos":false}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PedidoEventoPublisher productor;

    @Test
    void publicaAceptadoYDespachadoConLaCabeceraDeFallo() throws Exception {
        mockMvc.perform(post("/api/lab/pedidos/aceptar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.publicadosEn[0]").value("pedido.aceptado"))
                .andExpect(jsonPath("$.colasConFallo[0]").value("pedidos360.notificaciones.queue"));

        ArgumentCaptor<Map<String, Object>> cabeceras = ArgumentCaptor.forClass(Map.class);
        verify(productor, times(2)).publicar(any(), any(PedidoEvento.class), cabeceras.capture());
        assertThat(cabeceras.getAllValues().get(0))
                .containsEntry("fallo-notificaciones", true)
                .containsEntry("fallo-cocina", false)
                .containsEntry("fallo-documentos", false);
        verify(productor).publicar(eq("pedido.aceptado"), any(PedidoEvento.class), any());
        verify(productor).publicar(eq("pedido.despachado"), any(PedidoEvento.class), any());
    }

    @Test
    void sinTokenLaRutaDeLaboratorioEsPublica() throws Exception {
        mockMvc.perform(post("/api/lab/pedidos/aceptar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isAccepted());
    }

    @Test
    void correoInvalidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/lab/pedidos/aceptar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pedidoId\":210,\"clienteEmail\":\"no-es-correo\"}"))
                .andExpect(status().isBadRequest());
    }
}
