package cl.duoc.dsy1107.pedidosapi.controller;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.pedidosapi.config.JwtAuthorityConverter;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class PedidoControllerSecurityTests {

    @Autowired
    MockMvc mockMvc;

    @Test
    void publicoDevuelve200SinToken() throws Exception {
        mockMvc.perform(get("/api/publico"))
                .andExpect(status().isOk());
    }

    @Test
    void pedidosSinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/orders"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void pedidosSinScopeDevuelve403() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void pedidosConScopeSinRolDevuelve403() throws Exception {
        mockMvc.perform(get("/api/orders")
                        .with(jwt().authorities(new SimpleGrantedAuthority("SCOPE_Pedidos.Read"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void meConvierteScpYRolesDesdeLosClaims() throws Exception {
        // Usa el converter real para verificar el mapeo de claims de Entra ID.
        mockMvc.perform(get("/api/me")
                        .with(jwt()
                                .jwt(j -> j.claim("preferred_username", "admin.p360@test.cl")
                                        .claim("scp", "Pedidos.Read")
                                        .claim("roles", List.of("Admin")))
                                .authorities(new JwtAuthorityConverter())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("Admin"))
                .andExpect(jsonPath("$.authorities[?(@ == 'SCOPE_Pedidos.Read')]").exists())
                .andExpect(jsonPath("$.authorities[?(@ == 'ROLE_Admin')]").exists());
    }
}
