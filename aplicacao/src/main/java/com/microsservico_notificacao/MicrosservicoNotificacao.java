package com.microsservico_notificacao;

import com.google.gson.Gson;
import com.rabbitmq.client.Channel;
import com.rabbitmq.client.Connection;
import com.rabbitmq.client.ConnectionFactory;
import com.rabbitmq.client.DeliverCallback;
import com.seguranca.Criptografia;
import com.seguranca.DadosEvento;
import com.seguranca.EnvelopeUtil;
import com.seguranca.GerenciadorDeChaves;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.PublicKey;
import java.util.Base64;
import java.util.concurrent.TimeoutException;

public class MicrosservicoNotificacao {

    private final static String HOST = "localhost";
    private final static String CLASS_NAME = MicrosservicoNotificacao.class.getSimpleName();

    private final static String EXCHANGE_NAME = "promocoes";
    private final static String EXCHANGE_TYPE = "topic";

    private final static String ENTRY_QUEUE_NAME = "fila.notificacao";
    private final static String ENTRY_ROUTING_KEY_1 = "promocao.publicada";
    private final static String ENTRY_ROUTING_KEY_2 = "promocao.destaque";

    private final static int PREFETCH_COUNT = 1;
    private final static KeyPair KEYPAIR;

    static {
        try {
            KEYPAIR = Criptografia.gerarPardeChaves();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final static String CHAVE_PUBLICA_BASE64 = Base64.getEncoder().encodeToString(KEYPAIR.getPublic().getEncoded());

    public static void main(String[] args) throws IOException, TimeoutException {
        GerenciadorDeChaves.salvarChave(CLASS_NAME, CHAVE_PUBLICA_BASE64);

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(HOST);
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE);
        channel.queueDeclare(ENTRY_QUEUE_NAME, true, false, false, null);
        channel.queueBind(ENTRY_QUEUE_NAME, EXCHANGE_NAME, ENTRY_ROUTING_KEY_1);
        channel.queueBind(ENTRY_QUEUE_NAME, EXCHANGE_NAME, ENTRY_ROUTING_KEY_2);
        channel.basicQos(PREFETCH_COUNT);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String envelope = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try{
                publicarNotificacao(envelope, channel);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            } finally{
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };
        channel.basicConsume(ENTRY_QUEUE_NAME, false, deliverCallback, consumerTag -> {});
    }

    private static void publicarNotificacao(String message, Channel channel) throws TimeoutException {
        EnvelopeUtil.Envelope envelopeRecebido = EnvelopeUtil.Envelope.separar(message);

        try{
            String chavePublicaBase64 = GerenciadorDeChaves.buscarChave(envelopeRecebido.getProdutor());
            PublicKey chavePublicaRecebida = Criptografia.carregarChavePublica(chavePublicaBase64);

            if(Criptografia.validarAssinatura(envelopeRecebido.getDados(),envelopeRecebido.getAssinatura(), chavePublicaRecebida)){
                Gson gson = new Gson();
                DadosEvento dados = new Gson().fromJson(envelopeRecebido.getDados(), DadosEvento.class);
                //String categoria = dados.getCategoria().toLowerCase();
                //String routingKeyDestino = "promocao." + categoria;
                //System.out.println("[" + CLASS_NAME + "] Nova notificação para "+routingKeyDestino+".");
                if(envelopeRecebido.getProdutor().equals("MicrosservicoRanking")){
                    dados.setIdItem("[HOT DEAL] " + dados.getIdItem());
                }
                String jsonModificado = gson.toJson(dados);
                String routingKeyDestino = "promocao." + dados.getCategoria();

                channel.basicPublish(EXCHANGE_NAME, routingKeyDestino, null,jsonModificado.getBytes(StandardCharsets.UTF_8));
                System.out.println("[" + CLASS_NAME + "] Conteúdo enviado:\n"+envelopeRecebido.getDados()+"\n");
            }
            else{
                System.err.println("Assinatura inválida, mensagem descartada");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
