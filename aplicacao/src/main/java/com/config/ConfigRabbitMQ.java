package com.config;

import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;

public class ConfigRabbitMQ {
    private static final String NOME_EXCHANGE = "Promocoes";
    private static Connection connection;

    public static Connection getConnection() throws Exception{
        if(connection == null || !connection.isOpen()) {
            ConnectionFactory factory = new ConnectionFactory();
            factory.setHost("localhost");
            connection = factory.newConnection();
        }
        return connection;
    }

    public static void inicializarInfraestrutura() throws Exception{
        Connection connection1 = getConnection();
        Channel channel = connection1.createChannel();
        channel.exchangeDeclare(NOME_EXCHANGE, "topic", true);

        System.out.println("Infraestrutura inicializada...");
        System.out.println();

        channel.close();
    }
}
