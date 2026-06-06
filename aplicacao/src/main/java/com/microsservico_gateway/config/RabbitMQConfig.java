package com.microsservico_gateway.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE_NAME        = "promocoes";
    public static final String ENTRY_QUEUE_NAME     = "fila.gateway";
    public static final String ROUTING_KEY_ENTRADA  = "promocao.publicada";
    public static final String ROUTING_KEY_RECEBIDA = "promocao.recebida";
    public static final String ROUTING_KEY_VOTO     = "promocao.voto";

    @Bean
    public TopicExchange exchange() {
        return new TopicExchange(EXCHANGE_NAME, false, false);
    }

    @Bean
    public Queue gatewayQueue() {
        return new Queue(ENTRY_QUEUE_NAME, false);
    }

    @Bean
    public Binding binding(Queue gatewayQueue, TopicExchange exchange) {
        return BindingBuilder
                .bind(gatewayQueue)
                .to(exchange)
                .with(ROUTING_KEY_ENTRADA);
    }
}