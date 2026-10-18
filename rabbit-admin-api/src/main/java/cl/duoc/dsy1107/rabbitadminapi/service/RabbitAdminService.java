package cl.duoc.dsy1107.rabbitadminapi.service;

import cl.duoc.dsy1107.rabbitadminapi.dto.BindingRequest;
import cl.duoc.dsy1107.rabbitadminapi.dto.ColaRequest;
import cl.duoc.dsy1107.rabbitadminapi.dto.ColaResponse;
import cl.duoc.dsy1107.rabbitadminapi.dto.ExchangeRequest;
import cl.duoc.dsy1107.rabbitadminapi.exception.AdminRabbitException;
import java.util.Properties;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Exchange;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * Administración de RabbitMQ: único punto del microservicio que conoce el broker.
 *
 * El controller solo traduce HTTP; aquí viven las reglas de la administración:
 * existencia previa del recurso, protección de la topología de Pedidos360,
 * coherencia entre tipo de exchange y routing key, y traducción de los errores
 * del broker a códigos HTTP (404, 409, 400 o 503).
 */
@Service
public class RabbitAdminService {

    private static final Logger log = LoggerFactory.getLogger(RabbitAdminService.class);

    private final AmqpAdmin amqpAdmin;
    private final Pattern topologiaProtegida;

    public RabbitAdminService(AmqpAdmin amqpAdmin,
            @Value("${rabbit-admin.topologia-protegida}") String topologiaProtegida) {
        this.amqpAdmin = amqpAdmin;
        this.topologiaProtegida = Pattern.compile(topologiaProtegida);
    }

    public ColaResponse consultarCola(String nombre) {
        Properties propiedades = propiedadesExistentes(nombre);
        return new ColaResponse(
                nombre,
                entero(propiedades.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)),
                entero(propiedades.get(RabbitAdmin.QUEUE_CONSUMER_COUNT)));
    }

    public void crearCola(ColaRequest request) {
        // Redeclarar una cola protegida con otros argumentos rompería pedidos-api
        // (PRECONDITION_FAILED), así que tampoco se permite crearla desde aquí.
        proteger(request.nombre());
        boolean durable = request.durable() == null || request.durable();
        QueueBuilder cola = durable
                ? QueueBuilder.durable(request.nombre())
                : QueueBuilder.nonDurable(request.nombre());
        if (request.ttlMs() != null) {
            cola.ttl(request.ttlMs());
        }
        if (request.maxLength() != null) {
            cola.maxLength(request.maxLength());
        }
        if (request.deadLetterExchange() != null) {
            cola.deadLetterExchange(request.deadLetterExchange());
        }
        ejecutar(() -> amqpAdmin.declareQueue(cola.build()),
                "No fue posible crear la cola '" + request.nombre() + "'");
        log.info("[ADMIN] cola creada: {}", request.nombre());
    }

    public void eliminarCola(String nombre) {
        proteger(nombre);
        propiedadesExistentes(nombre);
        ejecutar(() -> amqpAdmin.deleteQueue(nombre),
                "No fue posible eliminar la cola '" + nombre + "'");
        log.info("[ADMIN] cola eliminada: {}", nombre);
    }

    /**
     * Vacía los mensajes Ready de una cola. Se permite también en la topología
     * protegida: limpiar una DLQ ya diagnosticada es una tarea normal de operación.
     */
    public int purgarCola(String nombre) {
        propiedadesExistentes(nombre);
        int eliminados = obtener(() -> amqpAdmin.purgeQueue(nombre),
                "No fue posible purgar la cola '" + nombre + "'");
        log.info("[ADMIN] cola purgada: {} ({} mensajes)", nombre, eliminados);
        return eliminados;
    }

    public void crearExchange(ExchangeRequest request) {
        proteger(request.nombre());
        boolean durable = request.durable() == null || request.durable();
        Exchange exchange = "topic".equals(request.tipo())
                ? new TopicExchange(request.nombre(), durable, false)
                : new DirectExchange(request.nombre(), durable, false);
        ejecutar(() -> amqpAdmin.declareExchange(exchange),
                "No fue posible crear el exchange '" + request.nombre() + "'");
        log.info("[ADMIN] exchange {} creado: {}", request.tipo(), request.nombre());
    }

    public void eliminarExchange(String nombre) {
        proteger(nombre);
        ejecutar(() -> amqpAdmin.deleteExchange(nombre),
                "No fue posible eliminar el exchange '" + nombre + "'");
        log.info("[ADMIN] exchange eliminado: {}", nombre);
    }

    public void crearBinding(BindingRequest request) {
        Binding binding = binding(request);
        ejecutar(() -> amqpAdmin.declareBinding(binding),
                "No fue posible crear el binding de " + descripcion(request));
        log.info("[ADMIN] binding creado: {}", descripcion(request));
    }

    public void eliminarBinding(BindingRequest request) {
        Binding binding = binding(request);
        ejecutar(() -> amqpAdmin.removeBinding(binding),
                "No fue posible eliminar el binding de " + descripcion(request));
        log.info("[ADMIN] binding eliminado: {}", descripcion(request));
    }

    public static String descripcion(BindingRequest request) {
        return request.exchange() + " -> " + request.cola() + " (" + request.routingKey() + ")";
    }

    // Las colas protegidas no reciben ni pierden bindings: cambiaría qué eventos procesa
    // pedidos-api. Una cola de laboratorio sí puede enlazarse a un exchange de Pedidos360.
    private Binding binding(BindingRequest request) {
        proteger(request.cola());
        boolean comodin = request.routingKey().contains("*") || request.routingKey().contains("#");
        if (comodin && "direct".equals(request.exchangeTipo())) {
            throw new AdminRabbitException(
                    "Los comodines * y # solo funcionan en un exchange topic; en un direct "
                            + "la routing key debe coincidir exactamente.");
        }
        return new Binding(request.cola(), Binding.DestinationType.QUEUE,
                request.exchange(), request.routingKey(), null);
    }

    private void proteger(String nombre) {
        if (topologiaProtegida.matcher(nombre).matches()) {
            throw new AdminRabbitException(
                    "'" + nombre + "' forma parte de la topología de Pedidos360 y no puede "
                            + "modificarse desde el administrador.",
                    HttpStatus.CONFLICT);
        }
    }

    private Properties propiedadesExistentes(String nombre) {
        Properties propiedades = obtener(() -> amqpAdmin.getQueueProperties(nombre),
                "No fue posible consultar la cola '" + nombre + "'");
        if (propiedades == null || propiedades.isEmpty()) {
            throw new AdminRabbitException(
                    "No existe la cola '" + nombre + "' en el broker.", HttpStatus.NOT_FOUND);
        }
        return propiedades;
    }

    private static int entero(Object valor) {
        return valor instanceof Number numero ? numero.intValue() : 0;
    }

    private static void ejecutar(Runnable operacion, String mensajeError) {
        obtener(() -> {
            operacion.run();
            return null;
        }, mensajeError);
    }

    private static <T> T obtener(Supplier<T> operacion, String mensajeError) {
        try {
            return operacion.get();
        } catch (AmqpConnectException e) {
            throw new AdminRabbitException(
                    "RabbitMQ no está disponible: " + mensajeError.toLowerCase(),
                    HttpStatus.SERVICE_UNAVAILABLE);
        } catch (AmqpException e) {
            String detalle = e.getCause() != null ? e.getCause().getMessage() : e.getMessage();
            throw new AdminRabbitException(mensajeError + ": " + detalle);
        }
    }
}
