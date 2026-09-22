package cl.duoc.dsy1107.pedidosapi.controller;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cl.duoc.dsy1107.pedidosapi.client.CatalogoClient;
import cl.duoc.dsy1107.pedidosapi.client.MovimientoStock;
import cl.duoc.dsy1107.pedidosapi.client.ProductoCatalogo;
import cl.duoc.dsy1107.pedidosapi.config.JwtAuthorityConverter;
import cl.duoc.dsy1107.pedidosapi.exception.StockInsuficienteException;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PedidoFlujoTests {

    private static final String PEDIDO_JSON =
            "{\"items\":[{\"productoId\":1,\"cantidad\":2},{\"productoId\":2,\"cantidad\":1}]}";

    @Autowired
    MockMvc mockMvc;

    // El catálogo es otro servicio: aquí se simula su respuesta.
    @MockitoBean
    CatalogoClient catalogo;

    @BeforeEach
    void catalogoConProductos() {
        when(catalogo.obtenerProducto(1L))
                .thenReturn(new ProductoCatalogo(1L, "Notebook", new BigDecimal("500000"), 10));
        when(catalogo.obtenerProducto(2L))
                .thenReturn(new ProductoCatalogo(2L, "Mouse", new BigDecimal("20000"), 10));
    }

    // Token con los mismos claims que emite Entra ID, convertido con el converter real.
    private static JwtRequestPostProcessor usuario(String oid, String rol) {
        return jwt().jwt(j -> j.claim("oid", oid)
                        .claim("preferred_username", oid + "@p360.cl")
                        .claim("scp", "Pedidos.Read")
                        .claim("roles", List.of(rol)))
                .authorities(new JwtAuthorityConverter());
    }

    private long crearPedido(JwtRequestPostProcessor quien) throws Exception {
        String json = mockMvc.perform(post("/api/orders").with(quien)
                        .contentType(MediaType.APPLICATION_JSON).content(PEDIDO_JSON))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return ((Number) JsonPath.read(json, "$.id")).longValue();
    }

    private ResultActions cambiarEstado(long id, String estado, JwtRequestPostProcessor quien)
            throws Exception {
        return mockMvc.perform(put("/api/orders/{id}/status", id).with(quien)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"estado\":\"" + estado + "\"}"));
    }

    @Test
    void clienteCreaPedidoConPreciosDelCatalogo() throws Exception {
        mockMvc.perform(post("/api/orders").with(usuario("cli1", "Cliente"))
                        .contentType(MediaType.APPLICATION_JSON).content(PEDIDO_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("CREADO"))
                .andExpect(jsonPath("$.total").value(1020000))
                .andExpect(jsonPath("$.cliente").value("cli1@p360.cl"))
                .andExpect(jsonPath("$.items", hasSize(2)))
                .andExpect(jsonPath("$.siguientesEstados", hasSize(2)));
    }

    @Test
    void adminNoCreaPedidos() throws Exception {
        mockMvc.perform(post("/api/orders").with(usuario("adm", "Admin"))
                        .contentType(MediaType.APPLICATION_JSON).content(PEDIDO_JSON))
                .andExpect(status().isForbidden());
    }

    @Test
    void pedidoSinItemsDevuelve400() throws Exception {
        mockMvc.perform(post("/api/orders").with(usuario("cli1", "Cliente"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"items\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void clienteVeSoloSusPedidosYOperadorVeTodos() throws Exception {
        crearPedido(usuario("cli1", "Cliente"));
        long ajeno = crearPedido(usuario("cli2", "Cliente"));

        mockMvc.perform(get("/api/orders").with(usuario("cli1", "Cliente")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        mockMvc.perform(get("/api/orders/{id}", ajeno).with(usuario("cli1", "Cliente")))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/orders").with(usuario("op", "Operador")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void clienteNoPuedeCambiarEstado() throws Exception {
        long id = crearPedido(usuario("cli1", "Cliente"));
        cambiarEstado(id, "ACEPTADO", usuario("cli1", "Cliente"))
                .andExpect(status().isForbidden());
    }

    @Test
    void noSePuedeDespacharSinAceptar() throws Exception {
        long id = crearPedido(usuario("cli1", "Cliente"));
        cambiarEstado(id, "DESPACHADO", usuario("op", "Operador"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }

    @Test
    void aceptarDescuentaStockYPermiteAvanzarHastaEntregado() throws Exception {
        long id = crearPedido(usuario("cli1", "Cliente"));

        cambiarEstado(id, "ACEPTADO", usuario("op", "Operador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("ACEPTADO"));
        verify(catalogo).descontarStock(List.of(
                new MovimientoStock(1L, 2), new MovimientoStock(2L, 1)));

        cambiarEstado(id, "EN_PREPARACION", usuario("op", "Operador")).andExpect(status().isOk());
        cambiarEstado(id, "DESPACHADO", usuario("op", "Operador")).andExpect(status().isOk());
        cambiarEstado(id, "ENTREGADO", usuario("op", "Operador"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.siguientesEstados", hasSize(0)));
    }

    @Test
    void sinStockElPedidoNoSeAcepta() throws Exception {
        long id = crearPedido(usuario("cli1", "Cliente"));
        doThrow(new StockInsuficienteException("Notebook")).when(catalogo).descontarStock(anyList());

        cambiarEstado(id, "ACEPTADO", usuario("op", "Operador"))
                .andExpect(status().isConflict());
        mockMvc.perform(get("/api/orders/{id}", id).with(usuario("op", "Operador")))
                .andExpect(jsonPath("$.estado").value("CREADO"));
    }

    @Test
    void cancelarPedidoAceptadoReponeStock() throws Exception {
        long id = crearPedido(usuario("cli1", "Cliente"));
        cambiarEstado(id, "ACEPTADO", usuario("op", "Operador")).andExpect(status().isOk());
        cambiarEstado(id, "CANCELADO", usuario("adm", "Admin")).andExpect(status().isOk());
        verify(catalogo).reponerStock(anyList());
    }

    @Test
    void cancelarPedidoCreadoNoTocaStock() throws Exception {
        long id = crearPedido(usuario("cli1", "Cliente"));
        cambiarEstado(id, "CANCELADO", usuario("op", "Operador")).andExpect(status().isOk());
        verify(catalogo, never()).reponerStock(any());
    }
}
