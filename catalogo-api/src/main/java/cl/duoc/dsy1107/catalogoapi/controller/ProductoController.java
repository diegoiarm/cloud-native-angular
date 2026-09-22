package cl.duoc.dsy1107.catalogoapi.controller;

import cl.duoc.dsy1107.catalogoapi.dto.ProductoRequest;
import cl.duoc.dsy1107.catalogoapi.model.Producto;
import cl.duoc.dsy1107.catalogoapi.service.ProductoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Catálogo de productos. Todas las rutas exigen el scope Pedidos.Read (SecurityConfig):
 * los tres actores pueden consultar, solo Admin crea y edita.
 */
@RestController
@RequestMapping("/api/catalog/products")
public class ProductoController {

    private final ProductoService service;

    public ProductoController(ProductoService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public List<Producto> listar() {
        return service.listar();
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public Producto obtener(@PathVariable Long id) {
        return service.obtener(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('Admin')")
    public Producto crear(@Valid @RequestBody ProductoRequest request) {
        return service.crear(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('Admin')")
    public Producto actualizar(@PathVariable Long id, @Valid @RequestBody ProductoRequest request) {
        return service.actualizar(id, request);
    }
}
