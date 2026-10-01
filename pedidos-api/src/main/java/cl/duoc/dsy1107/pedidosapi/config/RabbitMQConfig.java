package cl.duoc.dsy1107.pedidosapi.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Topología de RabbitMQ para los eventos de Pedidos360.
 *
 * El Exchange enruta por coincidencia exacta de routing key (DirectExchange) y cada
 * Binding conecta una cola con una clave concreta:
 *
 *   pedido.creado     -> notificaciones.queue
 *   pedido.aceptado   -> notificaciones.queue, cocina.queue
 *   pedido.despachado -> notificaciones.queue, despacho.queue
 *
 * Exchange y colas son durables: el mensaje sobrevive a la caída del consumer y a
 * reinicios del broker, que es el requisito del broker para las pruebas de la semana.
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "pedidos360.direct.exchange";
    public static final String NOTIFICACIONES = "notificaciones.queue";
    public static final String COCINA = "cocina.queue";
    public static final String DESPACHO = "despacho.queue";

    public static final String PEDIDO_CREADO = "pedido.creado";
    public static final String PEDIDO_ACEPTADO = "pedido.aceptado";
    public static final String PEDIDO_DESPACHADO = "pedido.despachado";

    @Bean
    DirectExchange pedidosExchange() {
        return new DirectExchange(EXCHANGE, true, false);
    }

    @Bean
    Queue notificacionesQueue() {
        return new Queue(NOTIFICACIONES, true);
    }

    @Bean
    Queue cocinaQueue() {
        return new Queue(COCINA, true);
    }

    @Bean
    Queue despachoQueue() {
        return new Queue(DESPACHO, true);
    }

    @Bean
    Binding notificaCreado(DirectExchange pedidosExchange, Queue notificacionesQueue) {
        return BindingBuilder.bind(notificacionesQueue).to(pedidosExchange).with(PEDIDO_CREADO);
    }

    @Bean
    Binding notificaAceptado(DirectExchange pedidosExchange, Queue notificacionesQueue) {
        return BindingBuilder.bind(notificacionesQueue).to(pedidosExchange).with(PEDIDO_ACEPTADO);
    }

    @Bean
    Binding cocinaAceptado(DirectExchange pedidosExchange, Queue cocinaQueue) {
        return BindingBuilder.bind(cocinaQueue).to(pedidosExchange).with(PEDIDO_ACEPTADO);
    }

    @Bean
    Binding notificaDespachado(DirectExchange pedidosExchange, Queue notificacionesQueue) {
        return BindingBuilder.bind(notificacionesQueue).to(pedidosExchange).with(PEDIDO_DESPACHADO);
    }

    @Bean
    Binding despachoDespachado(DirectExchange pedidosExchange, Queue despachoQueue) {
        return BindingBuilder.bind(despachoQueue).to(pedidosExchange).with(PEDIDO_DESPACHADO);
    }
}
