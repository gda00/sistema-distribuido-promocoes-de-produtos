package com.microsservico_ranking;

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
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class MicrosservicoRanking {

    private final static int HOT_DEAL_SCORE = 2;
    private final static Map<String, Integer> RANKING = new ConcurrentHashMap<>();
    private final static Set<String> HOT_DEAL = ConcurrentHashMap.newKeySet();

    private final static String HOST = "localhost";
    private final static String CLASS_NAME = MicrosservicoRanking.class.getSimpleName();

    private final static String EXCHANGE_NAME = "promocoes";
    private final static String EXCHANGE_TYPE = "topic";

    private final static String ENTRY_QUEUE_NAME = "fila.ranking";
    private final static String ENTRY_ROUTING_KEY = "promocao.voto";
    private final static String OUTPUT_ROUTING_KEY = "promocao.destaque";

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
        channel.queueBind(ENTRY_QUEUE_NAME, EXCHANGE_NAME, ENTRY_ROUTING_KEY);
        channel.basicQos(PREFETCH_COUNT);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String envelope = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try{
                promocaoDestaque(envelope, channel);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            } finally{
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };
        channel.basicConsume(ENTRY_QUEUE_NAME, false, deliverCallback, consumerTag -> {});
    }

    private static void promocaoDestaque(String message, Channel channel) throws TimeoutException {
        EnvelopeUtil.Envelope envelopeRecebido = EnvelopeUtil.Envelope.separar(message);

        try{
            String chavePublicaBase64 = GerenciadorDeChaves.buscarChave(envelopeRecebido.getProdutor());
            PublicKey chavePublicaRecebida = Criptografia.carregarChavePublica(chavePublicaBase64);

            if(Criptografia.validarAssinatura(envelopeRecebido.getDados(),envelopeRecebido.getAssinatura(), chavePublicaRecebida)){
                publicarVoto(envelopeRecebido, channel);
            }
            else{
                System.err.println("Assinatura inválida, mensagem descartada");
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void publicarVoto(EnvelopeUtil.Envelope envelopeRecebido, Channel channel) throws Exception {
        DadosEvento dados = new Gson().fromJson(envelopeRecebido.getDados(), DadosEvento.class);
        int novoSaldo = RANKING.getOrDefault(dados.getIdItem(), 0) + (dados.getVoto().equalsIgnoreCase("positivo") ? 1 : -1);

        RANKING.put(dados.getIdItem(), novoSaldo);

        if(novoSaldo >= HOT_DEAL_SCORE && !HOT_DEAL.contains(dados.getIdItem())){
            String novaAssinatura = Criptografia.assinarMensagem(envelopeRecebido.getDados(), KEYPAIR.getPrivate());

            EnvelopeUtil.Envelope envelopeRetorno = new EnvelopeUtil.Envelope(CLASS_NAME, envelopeRecebido.getDados(), novaAssinatura);
            String jsonSaida = envelopeRetorno.toJson();

            channel.basicPublish(EXCHANGE_NAME, OUTPUT_ROUTING_KEY, null, jsonSaida.getBytes(StandardCharsets.UTF_8));
            HOT_DEAL.add(dados.getIdItem());
        }
    }
}