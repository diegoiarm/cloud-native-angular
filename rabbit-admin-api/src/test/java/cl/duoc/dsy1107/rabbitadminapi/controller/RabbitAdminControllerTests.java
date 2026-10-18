package cl.duoc.dsy1107.rabbitadminapi.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.rabbitadminapi.config.JwtAuthorityConverter;
import java.util.List;
import java.util.Properties;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Endpoints del administrador: seguridad por rol, validación de entradas y
 * delegación en RabbitAdminService. El broker se simula con @MockitoBean.
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

    private void colaExiste(String nombre, int ready, int consumers) {
        Properties propiedades = new Properties();
        propiedades.put(RabbitAdmin.QUEUE_NAME, nombre);
        propiedades.put(RabbitAdmin.QUEUE_MESSAGE_COUNT, ready);
        propiedades.put(RabbitAdmin.QUEUE_CONSUMER_COUNT, consumers);
        when(amqpAdmin.getQueueProperties(nombre)).thenReturn(propiedades);
    }

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/admin/rabbit/queues/lab.demo.queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void conScopeSinRolAdminDevuelve403() throws Exception {
        mockMvc.perform(get("/api/admin/rabbit/queues/lab.demo.queue")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_Pedidos.Read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void consultarColaDevuelveReadyYConsumers() throws Exception {
        colaExiste("pedidos360.cocina.dlq", 3, 0);

        mockMvc.perform(get("/api/admin/rabbit/queues/pedidos360.cocina.dlq").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("pedidos360.cocina.dlq"))
                .andExpect(jsonPath("$.mensajesReady").value(3))
                .andExpect(jsonPath("$.consumers").value(0));
    }

    @Test
    void consultarColaInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/admin/rabbit/queues/no.existe").with(admin()))
                .andExpect(status().isNotFound());
    }

    @Test
    void crearColaDevuelve201() throws Exception {
        mockMvc.perform(post("/api/admin/rabbit/queues")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"lab.demo.queue\",\"ttlMs\":60000,\"maxLength\":100}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mensaje").value("Cola creada"))
                .andExpect(jsonPath("$.recurso").value("lab.demo.queue"));

        verify(amqpAdmin).declareQueue(any(Queue.class));
    }

    @Test
    void crearColaConNombreVacioDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/rabbit/queues")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("nombre")));

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void crearColaConTtlNegativoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/rabbit/queues")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"lab.demo.queue\",\"ttlMs\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("ttlMs: ttlMs debe ser mayor que 0"));

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void nombreDeRutaInvalidoDevuelve400() throws Exception {
        mockMvc.perform(delete("/api/admin/rabbit/queues/cola con espacios").with(admin()))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void eliminarColaDevuelve200() throws Exception {
        colaExiste("lab.demo.queue", 0, 0);

        mockMvc.perform(delete("/api/admin/rabbit/queues/lab.demo.queue").with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Cola eliminada"));

        verify(amqpAdmin).deleteQueue("lab.demo.queue");
    }

    @Test
    void laTopologiaDePedidos360NoSeElimina() throws Exception {
        mockMvc.perform(delete("/api/admin/rabbit/queues/pedidos360.cocina.queue").with(admin()))
                .andExpect(status().isConflict());

        verify(amqpAdmin, never()).deleteQueue(anyString());
    }

    @Test
    void purgarDlqDevuelveMensajesEliminados() throws Exception {
        colaExiste("pedidos360.cocina.dlq", 2, 0);
        when(amqpAdmin.purgeQueue("pedidos360.cocina.dlq")).thenReturn(2);

        mockMvc.perform(delete("/api/admin/rabbit/queues/pedidos360.cocina.dlq/mensajes")
                        .with(admin()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensajesEliminados").value(2));
    }

    @Test
    void crearExchangeDeTipoDesconocidoDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/rabbit/exchanges")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"lab.demo.exchange\",\"tipo\":\"headers\"}"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(amqpAdmin);
    }

    @Test
    void crearBindingDevuelve201() throws Exception {
        mockMvc.perform(post("/api/admin/rabbit/bindings")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cola":"lab.auditoria.queue","exchange":"pedidos360.topic.exchange",
                                 "exchangeTipo":"topic","routingKey":"pedido.#"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurso")
                        .value("pedidos360.topic.exchange -> lab.auditoria.queue (pedido.#)"));

        verify(amqpAdmin).declareBinding(any(Binding.class));
    }

    @Test
    void comodinEnExchangeDirectDevuelve400() throws Exception {
        mockMvc.perform(post("/api/admin/rabbit/bindings")
                        .with(admin())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"cola":"lab.demo.queue","exchange":"lab.demo.exchange",
                                 "exchangeTipo":"direct","routingKey":"pedido.*"}
                                """))
                .andExpect(status().isBadRequest());

        verify(amqpAdmin, never()).declareBinding(any());
    }
}
