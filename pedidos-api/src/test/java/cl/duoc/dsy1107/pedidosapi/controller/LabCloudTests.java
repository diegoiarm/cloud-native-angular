package cl.duoc.dsy1107.pedidosapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.pedidosapi.config.JwtAuthorityConverter;
import cl.duoc.dsy1107.pedidosapi.mensajeria.PedidoEvento;
import cl.duoc.dsy1107.pedidosapi.mensajeria.PedidoEventoPublisher;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Configuración cloud del laboratorio (EP4-08): la ruta existe pero exige un token
 * con rol Admin, porque el puerto de la EC2 es alcanzable desde internet.
 */
@SpringBootTest(properties = "pedidos360.lab.habilitado=true")
@AutoConfigureMockMvc
class LabCloudTests {

    private static final String CUERPO = """
            {"pedidoId":210,"clienteEmail":"prueba@example.com","falloCocina":true}
            """;

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PedidoEventoPublisher productor;

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(post("/api/lab/pedidos/aceptar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isUnauthorized());

        verifyNoInteractions(productor);
    }

    @Test
    void operadorDevuelve403() throws Exception {
        mockMvc.perform(post("/api/lab/pedidos/aceptar")
                        .with(jwt()
                                .jwt(j -> j.claim("scp", "Pedidos.Read")
                                        .claim("roles", List.of("Operador")))
                                .authorities(new JwtAuthorityConverter()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isForbidden());

        verifyNoInteractions(productor);
    }

    @Test
    void adminPublicaLosEventos() throws Exception {
        mockMvc.perform(post("/api/lab/pedidos/aceptar")
                        .with(jwt()
                                .jwt(j -> j.claim("scp", "Pedidos.Read")
                                        .claim("roles", List.of("Admin")))
                                .authorities(new JwtAuthorityConverter()))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(CUERPO))
                .andExpect(status().isAccepted());

        verify(productor, times(2)).publicar(any(), any(PedidoEvento.class), any());
    }
}
