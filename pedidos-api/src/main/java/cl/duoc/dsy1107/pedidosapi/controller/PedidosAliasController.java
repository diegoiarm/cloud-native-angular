package cl.duoc.dsy1107.pedidosapi.controller;

import cl.duoc.dsy1107.pedidosapi.dto.PedidoResponse;
import cl.duoc.dsy1107.pedidosapi.model.EstadoPedido;
import cl.duoc.dsy1107.pedidosapi.service.Actor;
import cl.duoc.dsy1107.pedidosapi.service.PedidoService;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Alias de compatibilidad: la guía y la pauta del curso prueban GET /api/pedidos,
 * mientras que el documento de alcance mínimo define /api/orders. Esta ruta devuelve
 * exactamente lo mismo que el listado de pedidos, con las mismas reglas de acceso.
 */
@RestController
public class PedidosAliasController {

    private final PedidoService service;

    public PedidosAliasController(PedidoService service) {
        this.service = service;
    }

    @GetMapping("/api/pedidos")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public List<PedidoResponse> listar(@RequestParam(required = false) EstadoPedido estado,
            JwtAuthenticationToken authentication) {
        return service.listar(Actor.desde(authentication), estado);
    }
}
