package com.clientes;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.seguranca.DadosEvento;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

public class ProcessoCliente1 {
    private final static String HOST = "localhost";
    private final static String EXCHANGE_NAME = "promocoes";
    private final static String EXCHANGE_TYPE = "topic";

    public static void main (String [] args){
        Scanner scanner = new Scanner(System.in);
        System.out.println("--- SELEÇÃO DE PERFIL DE CLIENTE ---");
        System.out.println("Escolha entre: 1, 2, 3 ou 4: ");

        //String idEscolhido = scanner.nextLine();
        String idEscolhido = "1";
        List<String> interesses = carregarInteresse(idEscolhido);
        if (interesses == null) {
            System.err.println("ID inválido! Encerrando.");
            return;
        }

        try {
            iniciarConsumidor(idEscolhido, interesses);
        } catch (Exception e) {
            System.err.println("Erro na conexão: " + e.getMessage());
        }
    }

    private static List<String> carregarInteresse(String id){
        switch (id) {
            case "1": return Arrays.asList("promocao.categoria.livros", "promocao.categoria.games");
            case "2": return Arrays.asList("promocao.destaque");
            case "3": return Arrays.asList("promocao.categoria.eletronicos", "promocao.categoria.vestuario", "promocao.destaque");
            case "4": return Arrays.asList("promocao.*");
            default: return null;
        }
    }

    private static void iniciarConsumidor(String idCliente, List<String> routingKeys) throws IOException, TimeoutException{
        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE);

        String queueName = channel.queueDeclare().getQueue();

        System.out.println("\n[OK] Cliente " + idCliente + " online.");

        for(String rk: routingKeys){
            channel.queueBind(queueName, EXCHANGE_NAME, rk);
            System.out.println("-> Escutando: " + rk);
        }
        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String mensagem = new String(delivery.getBody(), StandardCharsets.UTF_8);
            String rk = delivery.getEnvelope().getRoutingKey();

            System.out.println("\n[NOTIFICAÇÃO - " + rk + "]");

            try {
                Gson gson = new Gson();
                DadosEvento dadosEvento = gson.fromJson(mensagem, DadosEvento.class);
                System.out.println("Item: "+ dadosEvento.getIdItem());
                System.out.println("Valor: "+ dadosEvento.getValor());
            } catch (Exception e) {
                System.out.println("  -> Mensagem: " + mensagem);
            }
            System.out.println("--------------------------------");
        };

        channel.basicConsume(queueName, true, deliverCallback, consumerTag -> {});
    }
}
