package cl.duoc.dsy1107.catalogoapi.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.catalogoapi.config.JwtAuthorityConverter;
import cl.duoc.dsy1107.catalogoapi.model.Producto;
import cl.duoc.dsy1107.catalogoapi.repository.ProductoRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ProductoRepository repository;

    Producto notebook;
    Producto mouse;

    @BeforeEach
    void catalogo() {
        repository.deleteAll();
        notebook = repository.save(new Producto("Notebook", new BigDecimal("500000"), 5));
        mouse = repository.save(new Producto("Mouse", new BigDecimal("20000"), 10));
    }

    // Token con los mismos claims que emite Entra ID, convertido con el converter real.
    private static JwtRequestPostProcessor usuario(String rol) {
        return jwt().jwt(j -> j.claim("preferred_username", rol.toLowerCase() + "@p360.cl")
                        .claim("scp", "Pedidos.Read")
                        .claim("roles", List.of(rol)))
                .authorities(new JwtAuthorityConverter());
    }

    private static String movimiento(long productoId, int cantidad) {
        return "{\"productoId\":" + productoId + ",\"cantidad\":" + cantidad + "}";
    }

    // --- Seguridad ---

    @Test
    void sinTokenDevuelve401() throws Exception {
        mockMvc.perform(get("/api/catalog/products"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sinScopeDevuelve403() throws Exception {
        mockMvc.perform(get("/api/catalog/products")
                        .with(jwt().authorities(new SimpleGrantedAuthority("ROLE_Admin"))))
                .andExpect(status().isForbidden());
    }

    @Test
    void clienteConsultaCatalogo() throws Exception {
        mockMvc.perform(get("/api/catalog/products").with(usuario("Cliente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].nombre").value("Mouse"));
        mockMvc.perform(get("/api/catalog/products/{id}", notebook.getId()).with(usuario("Cliente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.precio").value(500000))
                .andExpect(jsonPath("$.stock").value(5));
    }

    @Test
    void productoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(get("/api/catalog/products/{id}", 9999).with(usuario("Cliente")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    // --- Mantenimiento de productos ---

    @Test
    void soloAdminCreaProductos() throws Exception {
        String body = "{\"nombre\":\"Teclado\",\"precio\":29990,\"stock\":15}";
        mockMvc.perform(post("/api/catalog/products").with(usuario("Operador"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/catalog/products").with(usuario("Admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.stock").value(15));
    }

    @Test
    void adminEditaPrecioYStock() throws Exception {
        mockMvc.perform(put("/api/catalog/products/{id}", mouse.getId()).with(usuario("Admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Mouse inalámbrico\",\"precio\":25000,\"stock\":40}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Mouse inalámbrico"))
                .andExpect(jsonPath("$.stock").value(40));
    }

    @Test
    void clienteNoEditaProductos() throws Exception {
        mockMvc.perform(put("/api/catalog/products/{id}", mouse.getId()).with(usuario("Cliente"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Mouse\",\"precio\":1,\"stock\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void precioYStockInvalidosDevuelven400() throws Exception {
        mockMvc.perform(post("/api/catalog/products").with(usuario("Admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"\",\"precio\":-1,\"stock\":-5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400));
    }

    // --- Stock (contrato usado por pedidos-api) ---

    @Test
    void operadorDescuentaStock() throws Exception {
        mockMvc.perform(post("/api/catalog/stock/descontar").with(usuario("Operador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + movimiento(notebook.getId(), 2) + ","
                                + movimiento(mouse.getId(), 3) + "]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/catalog/products/{id}", notebook.getId()).with(usuario("Operador")))
                .andExpect(jsonPath("$.stock").value(3));
        mockMvc.perform(get("/api/catalog/products/{id}", mouse.getId()).with(usuario("Operador")))
                .andExpect(jsonPath("$.stock").value(7));
    }

    @Test
    void sinStockSuficienteNoDescuentaNinguno() throws Exception {
        mockMvc.perform(post("/api/catalog/stock/descontar").with(usuario("Operador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + movimiento(mouse.getId(), 3) + ","
                                + movimiento(notebook.getId(), 6) + "]}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("Notebook")));
        // Todo o nada: el mouse tampoco se descontó.
        mockMvc.perform(get("/api/catalog/products/{id}", mouse.getId()).with(usuario("Operador")))
                .andExpect(jsonPath("$.stock").value(10));
    }

    @Test
    void lineasRepetidasSeSumanAntesDeValidar() throws Exception {
        mockMvc.perform(post("/api/catalog/stock/descontar").with(usuario("Operador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + movimiento(notebook.getId(), 3) + ","
                                + movimiento(notebook.getId(), 3) + "]}"))
                .andExpect(status().isConflict());
    }

    @Test
    void descontarProductoInexistenteDevuelve404() throws Exception {
        mockMvc.perform(post("/api/catalog/stock/descontar").with(usuario("Operador"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + movimiento(9999, 1) + "]}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void clienteNoMueveStock() throws Exception {
        mockMvc.perform(post("/api/catalog/stock/descontar").with(usuario("Cliente"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + movimiento(mouse.getId(), 1) + "]}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void reponerDevuelveStock() throws Exception {
        mockMvc.perform(post("/api/catalog/stock/reponer").with(usuario("Admin"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[" + movimiento(notebook.getId(), 4) + "]}"))
                .andExpect(status().isOk());
        mockMvc.perform(get("/api/catalog/products/{id}", notebook.getId()).with(usuario("Admin")))
                .andExpect(jsonPath("$.stock").value(9));
    }
}
