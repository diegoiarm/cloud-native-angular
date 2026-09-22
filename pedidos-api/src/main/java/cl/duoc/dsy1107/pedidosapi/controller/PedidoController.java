package cl.duoc.dsy1107.pedidosapi.controller;

import cl.duoc.dsy1107.pedidosapi.dto.CambiarEstadoRequest;
import cl.duoc.dsy1107.pedidosapi.dto.CrearPedidoRequest;
import cl.duoc.dsy1107.pedidosapi.dto.PedidoResponse;
import cl.duoc.dsy1107.pedidosapi.model.EstadoPedido;
import cl.duoc.dsy1107.pedidosapi.service.Actor;
import cl.duoc.dsy1107.pedidosapi.service.PedidoService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.*;

/**
 * Gestión de pedidos. Todas las rutas exigen el scope Pedidos.Read (SecurityConfig);
 * aquí se aplica la autorización por App Role según la matriz de actores.
 */
@RestController
@RequestMapping("/api/orders")
public class PedidoController {

    private final PedidoService service;

    public PedidoController(PedidoService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('Cliente', 'Operador')")
    public PedidoResponse crear(@Valid @RequestBody CrearPedidoRequest request,
            JwtAuthenticationToken authentication) {
        return service.crear(request, Actor.desde(authentication));
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public List<PedidoResponse> listar(@RequestParam(required = false) EstadoPedido estado,
            JwtAuthenticationToken authentication) {
        return service.listar(Actor.desde(authentication), estado);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('Admin', 'Operador', 'Cliente')")
    public PedidoResponse obtener(@PathVariable Long id, JwtAuthenticationToken authentication) {
        return service.obtener(id, Actor.desde(authentication));
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasAnyRole('Operador', 'Admin')")
    public PedidoResponse cambiarEstado(@PathVariable Long id,
            @Valid @RequestBody CambiarEstadoRequest request) {
        return service.cambiarEstado(id, request.estado());
    }
}
