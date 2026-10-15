package cl.duoc.dsy1107.pedidosapi.controller;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import cl.duoc.dsy1107.pedidosapi.dto.AdminBindingRequest;
import cl.duoc.dsy1107.pedidosapi.dto.AdminColaRequest;
import cl.duoc.dsy1107.pedidosapi.dto.AdminColaResponse;
import cl.duoc.dsy1107.pedidosapi.dto.AdminExchangeRequest;
import cl.duoc.dsy1107.pedidosapi.dto.AdminRabbitResponse;
import cl.duoc.dsy1107.pedidosapi.exception.AdminRabbitException;
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.regex.Pattern;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Administrador de RabbitMQ (EP4-09 del guion del video).
 *
 * Crea y elimina queues, exchanges y bindings, y consulta las propiedades de una
 * cola en el broker. Es la evidencia de administración desde código: la lista de
 * colas y las métricas en vivo se complementan con la Management UI y con
 * monitor/monitor_queues.py (documento 04).
 *
 * Rutas:
 *   GET    /api/admin/rabbit/topologia        topología declarada por la aplicación
 *   GET    /api/admin/rabbit/queues/{nombre}  propiedades de la cola en el broker
 *   POST   /api/admin/rabbit/queues           crear cola
 *   DELETE /api/admin/rabbit/queues/{nombre}  eliminar cola
 *   POST   /api/admin/rabbit/exchanges        crear exchange direct|topic
 *   DELETE /api/admin/rabbit/exchanges/{n}    eliminar exchange
 *   POST   /api/admin/rabbit/bindings         crear binding
 *   DELETE /api/admin/rabbit/bindings         eliminar binding
 *
 * Solo el rol Admin. La topología documentada (pedidos360.*) no puede eliminarse
 * desde aquí: esa evidencia se protege para no romper el laboratorio.
 */
@RestController
@RequestMapping("/api/admin/rabbit")
@PreAuthorize("hasRole('Admin')")
public class RabbitAdminController {

    private static final Pattern NOMBRE =
            Pattern.compile("^[a-zA-Z0-9._-]{1,255}$");

    private static final Pattern TOPOLOGIA_PROTEGIDA = Pattern.compile(
            "^pedidos360\\.(notificaciones|cocina|documentos)\\.(queue|retry|dlq)$"
                    + "|^pedidos360\\.(direct|topic|dlx)\\.exchange$");

    private final AmqpAdmin amqpAdmin;

    public RabbitAdminController(AmqpAdmin amqpAdmin) {
        this.amqpAdmin = amqpAdmin;
    }

    /** Topología que declara la aplicación al conectarse (documento 03). */
    @GetMapping("/topologia")
    public Map<String, Object> topologia() {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("exchanges", List.of(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.TOPIC_EXCHANGE,
                RabbitMQConfig.DLX_EXCHANGE));
        mapa.put("colas", List.of(
                RabbitMQConfig.NOTIFICACIONES,
                RabbitMQConfig.COCINA,
                RabbitMQConfig.DOCUMENTOS));
        mapa.put("dlq", List.of(
                RabbitMQConfig.NOTIFICACIONES_DLQ,
                RabbitMQConfig.COCINA_DLQ,
                RabbitMQConfig.DOCUMENTOS_DLQ));
        mapa.put("retry", List.of(
                RabbitMQConfig.NOTIFICACIONES_RETRY,
                RabbitMQConfig.COCINA_RETRY,
                RabbitMQConfig.DOCUMENTOS_RETRY));

        List<Map<String, String>> bindings = new ArrayList<>();
        bindings.add(binding(RabbitMQConfig.EXCHANGE, RabbitMQConfig.PEDIDO_ACEPTADO,
                RabbitMQConfig.COCINA));
        bindings.add(binding(RabbitMQConfig.EXCHANGE, RabbitMQConfig.PEDIDO_DESPACHADO,
                RabbitMQConfig.DOCUMENTOS));
        bindings.add(binding(RabbitMQConfig.TOPIC_EXCHANGE, "pedido.#",
                RabbitMQConfig.NOTIFICACIONES));
        bindings.add(binding(RabbitMQConfig.DLX_EXCHANGE, RabbitMQConfig.NOTIFICACIONES_DLQ,
                RabbitMQConfig.NOTIFICACIONES_DLQ));
        bindings.add(binding(RabbitMQConfig.DLX_EXCHANGE, RabbitMQConfig.COCINA_DLQ,
                RabbitMQConfig.COCINA_DLQ));
        bindings.add(binding(RabbitMQConfig.DLX_EXCHANGE, RabbitMQConfig.DOCUMENTOS_DLQ,
                RabbitMQConfig.DOCUMENTOS_DLQ));
        mapa.put("bindings", bindings);
        return mapa;
    }

    /** Propiedades de una cola en el broker (Ready, Consumers, nombre). */
    @GetMapping("/queues/{nombre}")
    public AdminColaResponse cola(@PathVariable String nombre) {
        validarNombre(nombre);
        Properties propiedades;
        try {
            propiedades = amqpAdmin.getQueueProperties(nombre);
        } catch (AmqpException e) {
            throw new AdminRabbitException(
                    "No existe la cola '" + nombre + "' en el broker.", HttpStatus.NOT_FOUND);
        }
        if (propiedades == null || propiedades.isEmpty()) {
            throw new AdminRabbitException(
                    "No existe la cola '" + nombre + "' en el broker.", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        propiedades.forEach((clave, valor) -> mapa.put(String.valueOf(clave), valor));
        return new AdminColaResponse(nombre, mapa);
    }

    @PostMapping("/queues")
    public AdminRabbitResponse crearCola(@Valid @RequestBody AdminColaRequest request) {
        validarNombre(request.nombre());
        boolean durable = request.durable() == null || request.durable();
        ejecutar(() -> amqpAdmin.declareQueue(new Queue(request.nombre(), durable)),
                "No fue posible crear la cola '" + request.nombre() + "'");
        return new AdminRabbitResponse("Cola creada", request.nombre());
    }

    @DeleteMapping("/queues/{nombre}")
    public AdminRabbitResponse eliminarCola(@PathVariable String nombre) {
        validarNombre(nombre);
        proteger(nombre);
        ejecutar(() -> amqpAdmin.deleteQueue(nombre),
                "No fue posible eliminar la cola '" + nombre + "'");
        return new AdminRabbitResponse("Cola eliminada", nombre);
    }

    @PostMapping("/exchanges")
    public AdminRabbitResponse crearExchange(@Valid @RequestBody AdminExchangeRequest request) {
        validarNombre(request.nombre());
        boolean durable = request.durable() == null || request.durable();
        Exchange exchange = request.tipo().equals("topic")
                ? new TopicExchange(request.nombre(), durable, false)
                : new DirectExchange(request.nombre(), durable, false);
        ejecutar(() -> amqpAdmin.declareExchange(exchange),
                "No fue posible crear el exchange '" + request.nombre() + "'");
        return new AdminRabbitResponse("Exchange creado", request.nombre());
    }

    @DeleteMapping("/exchanges/{nombre}")
    public AdminRabbitResponse eliminarExchange(@PathVariable String nombre) {
        validarNombre(nombre);
        proteger(nombre);
        ejecutar(() -> amqpAdmin.deleteExchange(nombre),
                "No fue posible eliminar el exchange '" + nombre + "'");
        return new AdminRabbitResponse("Exchange eliminado", nombre);
    }

    @PostMapping("/bindings")
    public AdminRabbitResponse crearBinding(@Valid @RequestBody AdminBindingRequest request) {
        validarNombre(request.cola());
        validarNombre(request.exchange());
        proteger(request.cola());
        Binding binding = construirBinding(request);
        ejecutar(() -> amqpAdmin.declareBinding(binding),
                "No fue posible crear el binding de " + request.cola()
                        + " hacia " + request.exchange());
        return new AdminRabbitResponse(
                "Binding creado", request.cola() + " -> " + request.exchange()
                        + " (" + request.routingKey() + ")");
    }

    @DeleteMapping("/bindings")
    public AdminRabbitResponse eliminarBinding(@Valid @RequestBody AdminBindingRequest request) {
        validarNombre(request.cola());
        validarNombre(request.exchange());
        proteger(request.cola());
        Binding binding = construirBinding(request);
        ejecutar(() -> amqpAdmin.removeBinding(binding),
                "No fue posible eliminar el binding de " + request.cola()
                        + " hacia " + request.exchange());
        return new AdminRabbitResponse(
                "Binding eliminado", request.cola() + " -> " + request.exchange()
                        + " (" + request.routingKey() + ")");
    }

    private static Binding construirBinding(AdminBindingRequest request) {
        Queue cola = new Queue(request.cola(), false);
        if ("topic".equals(request.exchangeTipo())) {
            return BindingBuilder.bind(cola)
                    .to(new TopicExchange(request.exchange(), true, false))
                    .with(request.routingKey());
        }
        return BindingBuilder.bind(cola)
                .to(new DirectExchange(request.exchange(), true, false))
                .with(request.routingKey());
    }

    private static void validarNombre(String nombre) {
        if (nombre == null || !NOMBRE.matcher(nombre).matches()) {
            throw new AdminRabbitException(
                    "Nombre inválido: solo letras, números, punto, guion y guion bajo.");
        }
    }

    /** La topología del laboratorio (documento 03/04) no se elimina desde la API. */
    private static void proteger(String nombre) {
        if (TOPOLOGIA_PROTEGIDA.matcher(nombre).matches()) {
            throw new AdminRabbitException(
                    "'" + nombre + "' forma parte de la topología documentada de la "
                            + "Semana 10 y no puede eliminarse desde el administrador.",
                    HttpStatus.CONFLICT);
        }
    }

    private static void ejecutar(Runnable operacion, String mensajeError) {
        try {
            operacion.run();
        } catch (AmqpException e) {
            String detalle = e.getCause() != null
                    ? e.getCause().getMessage()
                    : e.getMessage();
            throw new AdminRabbitException(mensajeError + ": " + detalle);
        }
    }

    private static Map<String, String> binding(String exchange, String routingKey, String cola) {
        Map<String, String> mapa = new LinkedHashMap<>();
        mapa.put("exchange", exchange);
        mapa.put("routingKey", routingKey);
        mapa.put("cola", cola);
        return mapa;
    }
}
