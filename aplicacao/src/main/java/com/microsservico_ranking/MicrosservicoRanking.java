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

    // score atual de cada promoção
    private final static Map<String, Integer> RANKING = new ConcurrentHashMap<>();

    // promoções que ESTÃO em destaque no momento
    private final static Set<String> HOT_DEAL = ConcurrentHashMap.newKeySet();

    // cache de dados completos da promoção (idItem → DadosEvento com emailLoja, idPromocao, etc.)
    // populado ao consumir "promocao.publicada"
    private final static Map<String, DadosEvento> CACHE_PROMOCOES = new ConcurrentHashMap<>();

    private final static String HOST       = "localhost";
    private final static String CLASS_NAME = MicrosservicoRanking.class.getSimpleName();

    private final static String EXCHANGE_NAME = "promocoes";
    private final static String EXCHANGE_TYPE = "topic";

    private final static String QUEUE_RANKING   = "fila.ranking";
    private final static String ROUTING_VOTO    = "promocao.voto";
    private final static String ROUTING_PUBLICADA = "promocao.publicada"; // para popular o cache
    private final static String ROUTING_DESTAQUE = "promocao.destaque";

    private final static int PREFETCH_COUNT = 1;

    private final static KeyPair KEYPAIR;

    static {
        try {
            KEYPAIR = Criptografia.gerarPardeChaves();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException(e);
        }
    }

    private final static String CHAVE_PUBLICA_BASE64 =
            Base64.getEncoder().encodeToString(KEYPAIR.getPublic().getEncoded());

    public static void main(String[] args) throws IOException, TimeoutException {
        GerenciadorDeChaves.salvarChave(CLASS_NAME, CHAVE_PUBLICA_BASE64);

        ConnectionFactory factory = new ConnectionFactory();
        factory.setHost(HOST);
        Connection connection = factory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE);
        channel.queueDeclare(QUEUE_RANKING, true, false, false, null);

        // escuta votos E promoções publicadas (para cache de emailLoja)
        channel.queueBind(QUEUE_RANKING, EXCHANGE_NAME, ROUTING_VOTO);
        channel.queueBind(QUEUE_RANKING, EXCHANGE_NAME, ROUTING_PUBLICADA);

        channel.basicQos(PREFETCH_COUNT);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String envelope = new String(delivery.getBody(), StandardCharsets.UTF_8);
            String routingKey = delivery.getEnvelope().getRoutingKey();
            try {
                processarEvento(envelope, routingKey, channel);
            } catch (Exception e) {
                System.err.println("[MS Ranking] Erro: " + e.getMessage());
            } finally {
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };

        channel.basicConsume(QUEUE_RANKING, false, deliverCallback, consumerTag -> {});
        System.out.println("[MS Ranking] Aguardando eventos...");
    }

    private static void processarEvento(String message, String routingKey, Channel channel)
            throws Exception {

        EnvelopeUtil.Envelope envelope = EnvelopeUtil.Envelope.separar(message);

        // validar assinatura
        String chaveBase64 = GerenciadorDeChaves.buscarChave(envelope.getProdutor());
        PublicKey chavePublica = Criptografia.carregarChavePublica(chaveBase64);

        if (!Criptografia.validarAssinatura(envelope.getDados(), envelope.getAssinatura(), chavePublica)) {
            System.err.println("[MS Ranking] Assinatura inválida — mensagem descartada.");
            return;
        }

        DadosEvento dados = new Gson().fromJson(envelope.getDados(), DadosEvento.class);

        if (ROUTING_PUBLICADA.equals(routingKey)) {
            // popular cache com dados completos da promoção (tem emailLoja, idPromocao, valor, etc.)
            if (dados.getIdItem() != null) {
                CACHE_PROMOCOES.put(dados.getIdItem(), dados);
                System.out.println("[MS Ranking] Cache atualizado para: " + dados.getIdItem());
            }
        } else if (ROUTING_VOTO.equals(routingKey)) {
            processarVoto(dados, channel);
        }
    }

    private static void processarVoto(DadosEvento voto, Channel channel) throws Exception {
        String idItem = voto.getIdItem();

        int novoScore = RANKING.getOrDefault(idItem, 0)
                + ("positivo".equalsIgnoreCase(voto.getVoto()) ? 1 : -1);
        RANKING.put(idItem, novoScore);

        boolean eraHotDeal = HOT_DEAL.contains(idItem);

        System.out.println("[MS Ranking] " + idItem + " | score: " + novoScore
                + " | hotdeal: " + eraHotDeal);

        if (novoScore >= HOT_DEAL_SCORE && !eraHotDeal) {

            HOT_DEAL.add(idItem);
            publicarDestaque(idItem, "ADICIONADO", channel);

        } else if (novoScore < HOT_DEAL_SCORE && eraHotDeal) {
            HOT_DEAL.remove(idItem);
            publicarDestaque(idItem, "REMOVIDO", channel);
        }

    }

    private static void publicarDestaque(String idItem, String status, Channel channel)
            throws Exception {

        // enriquecer com dados completos do cache (tem emailLoja, idPromocao, valor, categoria)
        DadosEvento dados = CACHE_PROMOCOES.getOrDefault(idItem, new DadosEvento());
        dados.setStatus(status);
        dados.setIdItem(idItem); // garantir que está preenchido

        String dadosJson  = new Gson().toJson(dados);
        String assinatura = Criptografia.assinarMensagem(dadosJson, KEYPAIR.getPrivate());
        EnvelopeUtil.Envelope envelope = new EnvelopeUtil.Envelope(CLASS_NAME, dadosJson, assinatura);

        channel.basicPublish(EXCHANGE_NAME, ROUTING_DESTAQUE, null,
                envelope.toJson().getBytes(StandardCharsets.UTF_8));

        System.out.println("[MS Ranking] Destaque publicado: " + idItem + " → " + status);
    }
}