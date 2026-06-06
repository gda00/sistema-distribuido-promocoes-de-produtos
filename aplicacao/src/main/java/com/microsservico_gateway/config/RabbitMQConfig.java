package com.microsservico_gateway.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME          = "promocoes";
    public static final String GATEWAY_QUEUE          = "fila.gateway";
    public static final String SSE_QUEUE              = "fila.gateway.sse";
    public static final String ROUTING_KEY_PUBLICADA  = "promocao.publicada";
    public static final String ROUTING_KEY_RECEBIDA   = "promocao.recebida";
    public static final String ROUTING_KEY_VOTO       = "promocao.voto";
    public static final String ROUTING_KEY_CATEGORIA  = "promocao.categoria";
    public static final String ROUTING_KEY_HOTDEAL    = "notificacao.hotdeal";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME, false, false);
    }

    @Bean
    public Queue gatewayQueue() {
        return new Queue(GATEWAY_QUEUE, false);
    }

    @Bean
    public Queue sseQueue() {
        return new Queue(SSE_QUEUE, false);
    }

    @Bean
    public Binding bindingPublicada(Queue gatewayQueue, TopicExchange exchange) {
        return BindingBuilder.bind(gatewayQueue).to(exchange).with(ROUTING_KEY_PUBLICADA);
    }

    @Bean
    public Binding bindingCategoria(Queue sseQueue, TopicExchange exchange) {
        return BindingBuilder.bind(sseQueue).to(exchange).with(ROUTING_KEY_CATEGORIA);
    }

    @Bean
    public Binding bindingHotdeal(Queue sseQueue, TopicExchange exchange) {
        return BindingBuilder.bind(sseQueue).to(exchange).with(ROUTING_KEY_HOTDEAL);
    }
}