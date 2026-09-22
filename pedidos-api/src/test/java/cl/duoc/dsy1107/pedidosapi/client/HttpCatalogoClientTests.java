package cl.duoc.dsy1107.pedidosapi.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import cl.duoc.dsy1107.pedidosapi.exception.ProductoNoEncontradoException;
import cl.duoc.dsy1107.pedidosapi.exception.StockInsuficienteException;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Verifica que pedidos-api llama a catalogo-api según el contrato acordado.
class HttpCatalogoClientTests {

    MockRestServiceServer catalogo;
    HttpCatalogoClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        catalogo = MockRestServiceServer.bindTo(builder).build();
        client = new HttpCatalogoClient(builder, "http://catalogo");

        Jwt jwt = Jwt.withTokenValue("token-operador")
                .header("alg", "none")
                .claim("sub", "op")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(60))
                .build();
        SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(jwt));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void obtieneProductoReenviandoElToken() {
        catalogo.expect(requestTo("http://catalogo/api/catalog/products/1"))
                .andExpect(method(HttpMethod.GET))
                .andExpect(header("Authorization", "Bearer token-operador"))
                .andRespond(withSuccess(
                        "{\"id\":1,\"nombre\":\"Notebook\",\"precio\":500000,\"stock\":5}",
                        MediaType.APPLICATION_JSON));

        ProductoCatalogo producto = client.obtenerProducto(1L);

        assertThat(producto.nombre()).isEqualTo("Notebook");
        assertThat(producto.precio()).isEqualByComparingTo(new BigDecimal("500000"));
        catalogo.verify();
    }

    @Test
    void productoInexistente() {
        catalogo.expect(requestTo("http://catalogo/api/catalog/products/9"))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        assertThatThrownBy(() -> client.obtenerProducto(9L))
                .isInstanceOf(ProductoNoEncontradoException.class);
    }

    @Test
    void descuentaStockConElCuerpoDelContrato() {
        catalogo.expect(requestTo("http://catalogo/api/catalog/stock/descontar"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json(
                        "{\"items\":[{\"productoId\":1,\"cantidad\":2},{\"productoId\":2,\"cantidad\":1}]}"))
                .andRespond(withSuccess());

        client.descontarStock(List.of(new MovimientoStock(1L, 2), new MovimientoStock(2L, 1)));

        catalogo.verify();
    }

    @Test
    void conflictoDeStockSeTraduceAStockInsuficiente() {
        catalogo.expect(requestTo("http://catalogo/api/catalog/stock/descontar"))
                .andRespond(withStatus(HttpStatus.CONFLICT)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"mensaje\":\"Stock insuficiente para Notebook\"}"));

        assertThatThrownBy(() -> client.descontarStock(List.of(new MovimientoStock(1L, 99))))
                .isInstanceOf(StockInsuficienteException.class)
                .hasMessageContaining("Notebook");
    }

    @Test
    void reponeStock() {
        catalogo.expect(requestTo("http://catalogo/api/catalog/stock/reponer"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess());

        client.reponerStock(List.of(new MovimientoStock(1L, 2)));

        catalogo.verify();
    }
}
