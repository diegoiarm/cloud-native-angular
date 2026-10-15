package cl.duoc.dsy1107.pedidosapi.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de RabbitMQ para los eventos de Pedidos360 (documentos 03, 04 y 05).
 *
 * Exchanges:
 *
 *   pedidos360.direct.exchange  enrutamiento exacto:
 *       pedido.aceptado   -> pedidos360.cocina.queue
 *       pedido.despachado -> pedidos360.documentos.queue
 *   pedidos360.topic.exchange   flujo general con comodín:
 *       pedido.#         -> pedidos360.notificaciones.queue
 *   pedidos360.dlx.exchange     dead-letter exchange:
 *       <cola>.dlq       -> pedidos360.<dominio>.dlq
 *
 * Cada cola principal declare su propio DLX: el mensaje rechazado sin requeue,
 * expirado por la política de TTL o descartado por max-length termina en su DLQ.
 * La cola <dominio>.retry retiene el reintento 5 s y devuelve el mensaje a la
 * cola principal (TTL de la Semana 09); la política de retención del documento 03
 * se limita a las tres principales mediante expresión regular, nunca a .retry ni .dlq.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "pedidos360.direct.exchange";
    public static final String TOPIC_EXCHANGE = "pedidos360.topic.exchange";
    public static final String DLX_EXCHANGE = "pedidos360.dlx.exchange";

    public static final String NOTIFICACIONES = "pedidos360.notificaciones.queue";
    public static final String COCINA = "pedidos360.cocina.queue";
    public static final String DOCUMENTOS = "pedidos360.documentos.queue";

    public static final String NOTIFICACIONES_RETRY = "pedidos360.notificaciones.retry";
    public static final String COCINA_RETRY = "pedidos360.cocina.retry";
    public static final String DOCUMENTOS_RETRY = "pedidos360.documentos.retry";

    public static final String NOTIFICACIONES_DLQ = "pedidos360.notificaciones.dlq";
    public static final String COCINA_DLQ = "pedidos360.cocina.dlq";
    public static final String DOCUMENTOS_DLQ = "pedidos360.documentos.dlq";

    public static final String PEDIDO_CREADO = "pedido.creado";
    public static final String PEDIDO_ACEPTADO = "pedido.aceptado";
    public static final String PEDIDO_DESPACHADO = "pedido.despachado";

    // Cabeceras del laboratorio: marcan el fallo controlado y cuentan los intentos.
    public static final String HEADER_INTENTOS = "x-intentos";
    public static final String HEADER_FALLO_NOTIFICACIONES = "fallo-notificaciones";
    public static final String HEADER_FALLO_COCINA = "fallo-cocina";
    public static final String HEADER_FALLO_DOCUMENTOS = "fallo-documentos";

    // Intento inicial + dos reintentos, con 5 s de espera en la cola .retry.
    public static final int MAX_INTENTOS = 3;
    public static final int RETRY_TTL_MS = 5000;

    @Bean
    DirectExchange pedidosExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    TopicExchange pedidosTopicExchange() {
        return new TopicExchange(TOPIC_EXCHANGE, true, false);
    }

    @Bean
    DirectExchange dlxExchange() {
        return new DirectExchange(DLX_EXCHANGE, true, false);
    }

    @Bean
    Queue notificacionesQueue() {
        return colaPrincipal(NOTIFICACIONES, NOTIFICACIONES_DLQ);
    }

    @Bean
    Queue cocinaQueue() {
        return colaPrincipal(COCINA, COCINA_DLQ);
    }

    @Bean
    Queue documentosQueue() {
        return colaPrincipal(DOCUMENTOS, DOCUMENTOS_DLQ);
    }

    @Bean
    Queue notificacionesRetryQueue() {
        return colaRetry(NOTIFICACIONES_RETRY, NOTIFICACIONES);
    }

    @Bean
    Queue cocinaRetryQueue() {
        return colaRetry(COCINA_RETRY, COCINA);
    }

    @Bean
    Queue documentosRetryQueue() {
        return colaRetry(DOCUMENTOS_RETRY, DOCUMENTOS);
    }

    @Bean
    Queue notificacionesDlq() {
        return new Queue(NOTIFICACIONES_DLQ, true);
    }

    @Bean
    Queue cocinaDlq() {
        return new Queue(COCINA_DLQ, true);
    }

    @Bean
    Queue documentosDlq() {
        return new Queue(DOCUMENTOS_DLQ, true);
    }

    // Direct: enrutamiento exacto por routing key.
    @Bean
    Binding cocinaAceptado(DirectExchange pedidosExchange, Queue cocinaQueue) {
        return BindingBuilder.bind(cocinaQueue).to(pedidosExchange).with(PEDIDO_ACEPTADO);
    }

    @Bean
    Binding documentosDespachado(DirectExchange pedidosExchange, Queue documentosQueue) {
        return BindingBuilder.bind(documentosQueue).to(pedidosExchange).with(PEDIDO_DESPACHADO);
    }

    // Topic: notificaciones recibe todo el flujo de pedidos con el comodín pedido.#.
    @Bean
    Binding notificacionesTopic(TopicExchange pedidosTopicExchange, Queue notificacionesQueue) {
        return BindingBuilder.bind(notificacionesQueue).to(pedidosTopicExchange).with("pedido.#");
    }

    // Dead-letter: cada DLQ recibe los mensajes de su cola principal.
    @Bean
    Binding notificacionesDlqBinding(DirectExchange dlxExchange, Queue notificacionesDlq) {
        return BindingBuilder.bind(notificacionesDlq).to(dlxExchange).with(NOTIFICACIONES_DLQ);
    }

    @Bean
    Binding cocinaDlqBinding(DirectExchange dlxExchange, Queue cocinaDlq) {
        return BindingBuilder.bind(cocinaDlq).to(dlxExchange).with(COCINA_DLQ);
    }

    @Bean
    Binding documentosDlqBinding(DirectExchange dlxExchange, Queue documentosDlq) {
        return BindingBuilder.bind(documentosDlq).to(dlxExchange).with(DOCUMENTOS_DLQ);
    }

    // Cola principal: duradera y con dead-letter hacia su DLQ.
    private static Queue colaPrincipal(String nombre, String dlq) {
        return QueueBuilder.durable(nombre)
                .withArgument("x-dead-letter-exchange", DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", dlq)
                .build();
    }

    // Cola de reintento: el mensaje espera 5 s y vuelve a su cola principal.
    private static Queue colaRetry(String nombre, String principal) {
        return QueueBuilder.durable(nombre)
                .withArgument("x-message-ttl", RETRY_TTL_MS)
                .withArgument("x-dead-letter-exchange", "")
                .withArgument("x-dead-letter-routing-key", principal)
                .build();
    }
}
