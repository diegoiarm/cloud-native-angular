package cl.duoc.dsy1107.catalogoapi.controller;

import cl.duoc.dsy1107.catalogoapi.dto.MovimientoStockRequest;
import cl.duoc.dsy1107.catalogoapi.service.ProductoService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Movimientos de stock que usa pedidos-api al aceptar o cancelar un pedido.
 * pedidos-api reenvía el token del usuario, por eso se permiten los roles que cambian estados.
 */
@RestController
@RequestMapping("/api/catalog/stock")
public class StockController {

    private final ProductoService service;

    public StockController(ProductoService service) {
        this.service = service;
    }

    @PostMapping("/descontar")
    @PreAuthorize("hasAnyRole('Operador', 'Admin')")
    public void descontar(@Valid @RequestBody MovimientoStockRequest request) {
        service.descontar(request);
    }

    @PostMapping("/reponer")
    @PreAuthorize("hasAnyRole('Operador', 'Admin')")
    public void reponer(@Valid @RequestBody MovimientoStockRequest request) {
        service.reponer(request);
    }
}
