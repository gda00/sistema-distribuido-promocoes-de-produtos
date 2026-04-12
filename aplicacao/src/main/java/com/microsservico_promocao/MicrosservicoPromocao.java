package com.microsservico_promocao;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.seguranca.Criptografia;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.util.concurrent.TimeoutException;

public class MicrosservicoPromocao {

    private final static String HOST = "localhost";

    private final static String EXCHANGE_NAME = "promocoes";
    private final static String EXCHANGE_TYPE = "topic";

    private final static String ENTRY_QUEUE_NAME = "fila.promocao";
    private final static String ENTRY_ROUTING_KEY = "promocao.recebida";
    private final static String OUTPUT_ROUTING_KEY = "promocao.publicada";

    private final static int PREFETCH_COUNT = 1;
    private final static KeyPair KEYPAIR;

    static {
        try {
            KEYPAIR = Criptografia.gerarPardeChaves();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    public static void main(String[] args) throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(HOST);
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE);
        channel.queueDeclare(ENTRY_QUEUE_NAME, true, false, false, null);
        channel.queueBind(ENTRY_QUEUE_NAME, EXCHANGE_NAME, ENTRY_ROUTING_KEY);
        channel.basicQos(PREFETCH_COUNT);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try{
                publicarPromocao(message);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            } finally{
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };
        channel.basicConsume(ENTRY_QUEUE_NAME, false, deliverCallback, consumerTag -> {});
    }

    private static void publicarPromocao(String message) throws IOException, TimeoutException {
        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(HOST);
        try(Connection connection = connectionFactory.newConnection(); Channel channel = connection.createChannel()){
            channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE);
            channel.basicPublish(EXCHANGE_NAME, OUTPUT_ROUTING_KEY, null, message.getBytes(StandardCharsets.UTF_8));
        }
    }
}