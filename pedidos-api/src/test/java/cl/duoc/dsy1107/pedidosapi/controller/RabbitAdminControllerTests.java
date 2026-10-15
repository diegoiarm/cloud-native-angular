package cl.duoc.dsy1107.pedidosapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.pedidosapi.config.JwtAuthorityConverter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Queue;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Administrador de RabbitMQ (EP4-09): solo Admin con scope, topología protegida
 * y creación/eliminación de recursos. El broker se simula con @MockitoBean.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RabbitAdminControllerTests {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AmqpAdmin amqpAdmin;

    private static JwtRequestPostProcessor admin() {
        return jwt()
                .jwt(j -> j.claim("scp", "Pedidos.Read").claim("roles", List.of("Admin")))
                .authorities(new JwtAuthorityConverter());
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/admin/rabbit/topologia"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conScopeSinRolDevuelve403() throws Exception {
        mockMvc.perform(get("/api/admin/rabbit/topologia")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_Pedidos.Read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void topologiaConAdminDevuelveLaDocumentada() throws Exception {
        mockMvc.perform(get("/api/admin/rabbit/topologia").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.colas[0]").value("pedidos360.notificaciones.queue"))
                .andExpect(jsonPath("$.dlq[1]").value("pedidos360.cocina.dlq"))
                .andExpect(jsonPath("$.retry[2]").value("pedidos360.documentos.retry"))
                .andExpect(jsonPath("$.bindings[2].routingKey").value("pedido.#"));
    }

    @Test
    void crearColaDeLaboratorioDevuelve200() throws Exception {
        mockMvc.perform(post("/api/admin/rabbit/queues")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"lab.demo.queue\",\"durable\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Cola creada"))
                .andExpect(jsonPath("$.recurso").value("lab.demo.queue"));

        verify(amqpAdmin).declareQueue(any(Queue.class));
    }

    @Test
    void eliminarColaDeLaboratorioDevuelve200() throws Exception {
        mockMvc.perform(delete("/api/admin/rabbit/queues/lab.demo.queue").with(admin()))
                .andExpect(status().isOk());

        verify(amqpAdmin).deleteQueue("lab.demo.queue");
    }

    @Test
    void laTopologiaDocumentadaNoSeElimina() throws Exception {
        mockMvc.perform(delete("/api/admin/rabbit/queues/pedidos360.cocina.queue").with(admin()))
                .andExpect(status().isConflict());

        verify(amqpAdmin, never()).deleteQueue(anyString());
    }

    @Test
    void crearBindingDeLaboratorioDevuelve200() throws Exception {
        mockMvc.perform(post("/api/admin/rabbit/bindings")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cola":"lab.demo.queue","exchange":"lab.demo.exchange",
                                 "exchangeTipo":"direct","routingKey":"lab.demo.creado"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Binding creado"));

        verify(amqpAdmin).declareBinding(any());
    }
}
