package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Reintentos limitados de la Semana 10: intento inicial + dos reintentos.
 *
 * Ante un fallo, publica una copia en la cola <dominio>.retry, donde espera el TTL
 * de 5 s y regresa sola a su cola principal. Esta clase no confirma mensajes: le
 * dice al consumer si el reintento quedó programado y el consumer decide el ACK
 * (la copia ya está en .retry) o el NACK sin requeue (el DLX lo lleva a la DLQ).
 */
@Component
public class Reintentos {

    private static final Logger log = LoggerFactory.getLogger(Reintentos.class);

    private final RabbitTemplate rabbitTemplate;

    public Reintentos(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    /** El laboratorio marca el fallo controlado con la cabecera fallo-<dominio>. */
    public boolean fallo(String dominio, Map<String, Object> headers) {
        Object valor = headers.get("fallo-" + dominio);
        return Boolean.TRUE.equals(valor) || "true".equalsIgnoreCase(String.valueOf(valor));
    }

    /**
     * Programa el siguiente intento del mensaje fallido.
     *
     * @return true si la copia quedó en la cola .retry; false si los intentos se
     *         agotaron o no se pudo publicar el reintento, y el mensaje debe ir a la DLQ.
     */
    public boolean reintentar(String colaPrincipal, String dominio, String payload,
            Map<String, Object> headers, Exception causa) {
        int intento = intentos(headers);
        if (intento >= RabbitMQConfig.MAX_INTENTOS) {
            log.error("[{}] intento {}/{} fallido ({}); mensaje agotado, NACK hacia la DLQ {}: {}",
                    dominio.toUpperCase(), intento, RabbitMQConfig.MAX_INTENTOS,
                    causa.getMessage(), dlqDe(colaPrincipal), payload);
            return false;
        }

        String colaRetry = colaPrincipal.replace(".queue", ".retry");
        Map<String, Object> copia = new HashMap<>(headers);
        copia.put(RabbitMQConfig.HEADER_INTENTOS, intento + 1);
        Message mensaje = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .setContentType(MessageProperties.CONTENT_TYPE_TEXT_PLAIN)
                .copyHeaders(copia)
                .build();
        try {
            rabbitTemplate.send("", colaRetry, mensaje);
        } catch (AmqpException e) {
            // Sin reintento posible el mensaje no debe quedar Unacked: va a la DLQ.
            log.error("[{}] no se pudo publicar el reintento en {} ({}); NACK hacia la DLQ {}",
                    dominio.toUpperCase(), colaRetry, e.getMessage(), dlqDe(colaPrincipal));
            return false;
        }
        log.warn("[{}] intento {}/{} fallido ({}); {} ms en {} antes del reintento",
                dominio.toUpperCase(), intento, RabbitMQConfig.MAX_INTENTOS,
                causa.getMessage(), RabbitMQConfig.RETRY_TTL_MS, colaRetry);
        return true;
    }

    private int intentos(Map<String, Object> headers) {
        Object valor = headers.get(RabbitMQConfig.HEADER_INTENTOS);
        return valor instanceof Number numero ? numero.intValue() : 1;
    }

    private static String dlqDe(String colaPrincipal) {
        return colaPrincipal.replace(".queue", ".dlq");
    }
}
