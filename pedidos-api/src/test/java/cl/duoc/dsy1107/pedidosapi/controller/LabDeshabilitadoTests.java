package cl.duoc.dsy1107.pedidosapi.controller;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.pedidosapi.mensajeria.PedidoEventoPublisher;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Sin pedidos360.lab.habilitado la ruta de laboratorio no existe, y sin
 * pedidos360.lab.sin-token tampoco es pública: una llamada anónima recibe 401 y
 * no publica nada.
 */
@SpringBootTest
@AutoConfigureMockMvc
class LabDeshabilitadoTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PedidoEventoPublisher productor;

    @Test
    void sinLaBanderaLaRutaDeLaboratorioNoExiste() throws Exception {
        mockMvc.perform(post("/api/lab/pedidos/aceptar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"pedidoId\":210,\"clienteEmail\":\"prueba@example.com\"}"))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productor);
    }
}
