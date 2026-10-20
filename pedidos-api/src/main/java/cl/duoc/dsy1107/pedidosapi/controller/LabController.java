package cl.duoc.dsy1107.pedidosapi.controller;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import cl.duoc.dsy1107.pedidosapi.dto.LabAceptarPedidoRequest;
import cl.duoc.dsy1107.pedidosapi.dto.LabAceptarPedidoResponse;
import cl.duoc.dsy1107.pedidosapi.mensajeria.PedidoEvento;
import cl.duoc.dsy1107.pedidosapi.mensajeria.PedidoEventoPublisher;
import jakarta.validation.Valid;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Laboratorio 1, Actividad B (documento 04): provocar una DLQ de forma controlada.
 *
 * Publica dos eventos de pedido con las cabeceras fallo-* que marcan qué cola
 * debe fallar: pedido.aceptado llega a notificaciones (Topic) y cocina (Direct);
 * pedido.despachado llega a notificaciones (Topic) y documentos (Direct).
 *
 * El controller solo se registra con pedidos360.lab.habilitado=true. En local
 * (application-local.yml) se llama sin token; en la nube, para la demostración
 * EP4-08, se habilita con PEDIDOS360_LAB_HABILITADO=true y SecurityConfig exige
 * un token con rol Admin, porque el puerto 8080 de la EC2 es alcanzable desde
 * internet. La ruta queda fuera del API Gateway (infra/api-gateway-openapi.yaml).
 */
@RestController
@RequestMapping("/api/lab/pedidos")
@ConditionalOnProperty(name = "pedidos360.lab.habilitado", havingValue = "true")
public class LabController {

    private final PedidoEventoPublisher productor;

    public LabController(PedidoEventoPublisher productor) {
        this.productor = productor;
    }

    @PostMapping("/aceptar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public LabAceptarPedidoResponse aceptar(@Valid @RequestBody LabAceptarPedidoRequest request) {
        Map<String, Object> cabeceras = Map.of(
                RabbitMQConfig.HEADER_FALLO_NOTIFICACIONES, request.falloNotificaciones(),
                RabbitMQConfig.HEADER_FALLO_COCINA, request.falloCocina(),
                RabbitMQConfig.HEADER_FALLO_DOCUMENTOS, request.falloDocumentos());

        PedidoEvento evento = new PedidoEvento(
                request.pedidoId(),
                "ACEPTADO",
                request.clienteEmail(),
                LocalDateTime.now());

        productor.publicar(RabbitMQConfig.PEDIDO_ACEPTADO, evento, cabeceras);
        productor.publicar(RabbitMQConfig.PEDIDO_DESPACHADO, evento, cabeceras);

        List<String> colasConFallo = new ArrayList<>();
        if (request.falloNotificaciones()) {
            colasConFallo.add(RabbitMQConfig.NOTIFICACIONES);
        }
        if (request.falloCocina()) {
            colasConFallo.add(RabbitMQConfig.COCINA);
        }
        if (request.falloDocumentos()) {
            colasConFallo.add(RabbitMQConfig.DOCUMENTOS);
        }

        return new LabAceptarPedidoResponse(
                "Eventos publicados con fallo controlado; la cola marcada reintenta dos veces "
                        + "y agota en su DLQ. Verifique Ready/Unacked/Consumers en la "
                        + "Management UI y corra el monitor.",
                List.of(RabbitMQConfig.PEDIDO_ACEPTADO, RabbitMQConfig.PEDIDO_DESPACHADO),
                colasConFallo);
    }
}
