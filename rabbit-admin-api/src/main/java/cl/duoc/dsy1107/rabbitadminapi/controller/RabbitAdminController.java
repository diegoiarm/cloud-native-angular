package cl.duoc.dsy1107.rabbitadminapi.controller;

import cl.duoc.dsy1107.rabbitadminapi.dto.BindingRequest;
import cl.duoc.dsy1107.rabbitadminapi.dto.ColaRequest;
import cl.duoc.dsy1107.rabbitadminapi.dto.ColaResponse;
import cl.duoc.dsy1107.rabbitadminapi.dto.ExchangeRequest;
import cl.duoc.dsy1107.rabbitadminapi.dto.Nombres;
import cl.duoc.dsy1107.rabbitadminapi.dto.OperacionResponse;
import cl.duoc.dsy1107.rabbitadminapi.dto.PurgaResponse;
import cl.duoc.dsy1107.rabbitadminapi.service.RabbitAdminService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST del administrador de RabbitMQ. Solo el rol Admin.
 *
 *   GET    /api/admin/rabbit/queues/{nombre}          Ready y consumers de la cola
 *   POST   /api/admin/rabbit/queues                   crear cola (TTL, max-length, DLX opcionales)
 *   DELETE /api/admin/rabbit/queues/{nombre}          eliminar cola
 *   DELETE /api/admin/rabbit/queues/{nombre}/mensajes purgar cola
 *   POST   /api/admin/rabbit/exchanges                crear exchange direct|topic
 *   DELETE /api/admin/rabbit/exchanges/{nombre}       eliminar exchange
 *   POST   /api/admin/rabbit/bindings                 crear binding
 *   DELETE /api/admin/rabbit/bindings                 eliminar binding
 *
 * La lógica vive en RabbitAdminService; aquí solo se validan las entradas.
 */
@RestController
@RequestMapping("/api/admin/rabbit")
@PreAuthorize("hasRole('Admin')")
public class RabbitAdminController {

    private final RabbitAdminService service;

    public RabbitAdminController(RabbitAdminService service) {
        this.service = service;
    }

    @GetMapping("/queues/{nombre}")
    public ColaResponse consultarCola(@PathVariable
            @Pattern(regexp = Nombres.PATRON, message = "nombre " + Nombres.MENSAJE) String nombre) {
        return service.consultarCola(nombre);
    }

    @PostMapping("/queues")
    @ResponseStatus(HttpStatus.CREATED)
    public OperacionResponse crearCola(@Valid @RequestBody ColaRequest request) {
        service.crearCola(request);
        return new OperacionResponse("Cola creada", request.nombre());
    }

    @DeleteMapping("/queues/{nombre}")
    public OperacionResponse eliminarCola(@PathVariable
            @Pattern(regexp = Nombres.PATRON, message = "nombre " + Nombres.MENSAJE) String nombre) {
        service.eliminarCola(nombre);
        return new OperacionResponse("Cola eliminada", nombre);
    }

    @DeleteMapping("/queues/{nombre}/mensajes")
    public PurgaResponse purgarCola(@PathVariable
            @Pattern(regexp = Nombres.PATRON, message = "nombre " + Nombres.MENSAJE) String nombre) {
        return new PurgaResponse(nombre, service.purgarCola(nombre));
    }

    @PostMapping("/exchanges")
    @ResponseStatus(HttpStatus.CREATED)
    public OperacionResponse crearExchange(@Valid @RequestBody ExchangeRequest request) {
        service.crearExchange(request);
        return new OperacionResponse("Exchange creado", request.nombre());
    }

    @DeleteMapping("/exchanges/{nombre}")
    public OperacionResponse eliminarExchange(@PathVariable
            @Pattern(regexp = Nombres.PATRON, message = "nombre " + Nombres.MENSAJE) String nombre) {
        service.eliminarExchange(nombre);
        return new OperacionResponse("Exchange eliminado", nombre);
    }

    @PostMapping("/bindings")
    @ResponseStatus(HttpStatus.CREATED)
    public OperacionResponse crearBinding(@Valid @RequestBody BindingRequest request) {
        service.crearBinding(request);
        return new OperacionResponse("Binding creado", RabbitAdminService.descripcion(request));
    }

    @DeleteMapping("/bindings")
    public OperacionResponse eliminarBinding(@Valid @RequestBody BindingRequest request) {
        service.eliminarBinding(request);
        return new OperacionResponse("Binding eliminado", RabbitAdminService.descripcion(request));
    }
}
