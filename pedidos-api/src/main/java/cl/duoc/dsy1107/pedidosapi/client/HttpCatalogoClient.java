package cl.duoc.dsy1107.pedidosapi.client;

import cl.duoc.dsy1107.pedidosapi.exception.CatalogoNoDisponibleException;
import cl.duoc.dsy1107.pedidosapi.exception.ProductoNoEncontradoException;
import cl.duoc.dsy1107.pedidosapi.exception.StockInsuficienteException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class HttpCatalogoClient implements CatalogoClient {

    private final RestClient restClient;

    public HttpCatalogoClient(RestClient.Builder builder, @Value("${catalogo.url}") String catalogoUrl) {
        this.restClient = builder
                .baseUrl(catalogoUrl)
                // Reenvía el Access Token del usuario: catálogo aplica sus propias reglas de acceso.
                .requestInitializer(request -> tokenActual()
                        .ifPresent(token -> request.getHeaders().setBearerAuth(token)))
                .build();
    }

    @Override
    public ProductoCatalogo obtenerProducto(Long productoId) {
        try {
            return restClient.get()
                    .uri("/api/catalog/products/{id}", productoId)
                    .retrieve()
                    .body(ProductoCatalogo.class);
        } catch (HttpClientErrorException.NotFound e) {
            throw new ProductoNoEncontradoException(productoId);
        } catch (RestClientException e) {
            throw new CatalogoNoDisponibleException(e);
        }
    }

    @Override
    public void descontarStock(List<MovimientoStock> movimientos) {
        enviarMovimientos("/api/catalog/stock/descontar", movimientos);
    }

    @Override
    public void reponerStock(List<MovimientoStock> movimientos) {
        enviarMovimientos("/api/catalog/stock/reponer", movimientos);
    }

    private void enviarMovimientos(String uri, List<MovimientoStock> movimientos) {
        try {
            restClient.post()
                    .uri(uri)
                    .body(Map.of("items", movimientos))
                    .retrieve()
                    .toBodilessEntity();
        } catch (HttpClientErrorException e) {
            if (e.getStatusCode().isSameCodeAs(HttpStatus.CONFLICT)) {
                throw new StockInsuficienteException(detalle(e.getResponseBodyAsString()));
            }
            if (e.getStatusCode().isSameCodeAs(HttpStatus.NOT_FOUND)) {
                throw new ProductoNoEncontradoException(null);
            }
            throw new CatalogoNoDisponibleException(e);
        } catch (RestClientException e) {
            throw new CatalogoNoDisponibleException(e);
        }
    }

    // catalogo-api responde {"status":409,"mensaje":"Stock insuficiente para ..."}.
    private static String detalle(String cuerpo) {
        if (cuerpo == null || cuerpo.isBlank()) {
            return null;
        }
        try {
            var nodo = new ObjectMapper().readTree(cuerpo).get("mensaje");
            return nodo == null ? cuerpo : nodo.asText();
        } catch (Exception e) {
            return cuerpo;
        }
    }

    private static Optional<String> tokenActual() {
        if (SecurityContextHolder.getContext().getAuthentication() instanceof JwtAuthenticationToken jwt) {
            return Optional.of(jwt.getToken().getTokenValue());
        }
        return Optional.empty();
    }
}
