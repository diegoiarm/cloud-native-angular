package cl.duoc.dsy1107.pedidosapi.mensajeria;

import cl.duoc.dsy1107.pedidosapi.config.RabbitMQConfig;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Reintentos limitados de la Semana 10: intento inicial + dos reintentos.
 *
 * El mensaje fallido se publica en la cola <dominio>.retry, donde espera el TTL
 * de 5 s y regresa solo a su cola principal. Al tercer fallo se lanza la excepción
 * que rechaza el mensaje sin requeue: el DLX de la cola principal lo conduce a
 * la DLQ, tal como describe el documento 03.
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

    public void manejarFallo(String colaPrincipal, String dominio, String payload,
            Map<String, Object> headers) {
        int intento = intentos(headers);
        if (intento >= RabbitMQConfig.MAX_INTENTOS) {
            log.error("[{}] intento {}/{} fallido; mensaje agotado, se envia a la DLQ {}",
                    dominio.toUpperCase(), intento, RabbitMQConfig.MAX_INTENTOS,
                    dlqDe(colaPrincipal));
            throw new AmqpRejectAndDontRequeueException(
                    "Intentos agotados para " + colaPrincipal + "; mensaje hacia la DLQ.");
        }

        String colaRetry = colaPrincipal.replace(".queue", ".retry");
        log.warn("[{}] intento {}/{} fallido; {} ms en {} antes del reintento",
                dominio.toUpperCase(), intento, RabbitMQConfig.MAX_INTENTOS,
                RabbitMQConfig.RETRY_TTL_MS, colaRetry);

        Map<String, Object> copia = new HashMap<>(headers);
        copia.put(RabbitMQConfig.HEADER_INTENTOS, intento + 1);
        Message mensaje = MessageBuilder.withBody(payload.getBytes(StandardCharsets.UTF_8))
                .copyHeaders(copia)
                .build();
        rabbitTemplate.send("", colaRetry, mensaje);
    }

    private int intentos(Map<String, Object> headers) {
        Object valor = headers.get(RabbitMQConfig.HEADER_INTENTOS);
        return valor instanceof Number numero ? numero.intValue() : 1;
    }

    private static String dlqDe(String colaPrincipal) {
        return colaPrincipal.replace(".queue", ".dlq");
    }
}
