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
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeoutException;

public class MicrosservicoNotificacao {

    private final static String HOST = "localhost";
    private final static String CLASS_NAME = MicrosservicoNotificacao.class.getSimpleName();

    private final static String EXCHANGE_NAME = "promocoes";
    private final static String EXCHANGE_TYPE = "topic";

    private final static String ENTRY_QUEUE_NAME     = "fila.notificacao";
    private final static String ENTRY_ROUTING_KEY_PUBLICADA = "promocao.publicada";
    private final static String ENTRY_ROUTING_KEY_DESTAQUE  = "promocao.destaque";

    private final static String OUTPUT_ROUTING_KEY_CATEGORIA = "promocao.categoria";
    private final static String OUTPUT_ROUTING_KEY_HOTDEAL   = "notificacao.hotdeal";

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

    private final static Map<String, String> EMAIL_POR_ITEM = new ConcurrentHashMap<>();

    public static void main(String[] args) throws IOException, TimeoutException {
        GerenciadorDeChaves.salvarChave(CLASS_NAME, CHAVE_PUBLICA_BASE64);

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(HOST);
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE);
        channel.queueDeclare(ENTRY_QUEUE_NAME, true, false, false, null);

        channel.queueBind(ENTRY_QUEUE_NAME, EXCHANGE_NAME, ENTRY_ROUTING_KEY_PUBLICADA);
        channel.queueBind(ENTRY_QUEUE_NAME, EXCHANGE_NAME, ENTRY_ROUTING_KEY_DESTAQUE);

        channel.basicQos(PREFETCH_COUNT);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String envelope = new String(delivery.getBody(), StandardCharsets.UTF_8);
            String routingKeyRecebido = delivery.getEnvelope().getRoutingKey();

            try {
                processarEvento(envelope, routingKeyRecebido, channel);
            } catch (Exception e) {
                System.err.println("[MS Notificação] Erro ao processar evento: " + e.getMessage());
            } finally {
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };

        channel.basicConsume(ENTRY_QUEUE_NAME, false, deliverCallback, consumerTag -> {});
        System.out.println("[MS Notificação] Aguardando eventos...");
    }

    private static void processarEvento(String message, String routingKeyRecebido, Channel channel)
            throws Exception {

        EnvelopeUtil.Envelope envelopeRecebido = EnvelopeUtil.Envelope.separar(message);

        String chavePublicaBase64 = GerenciadorDeChaves.buscarChave(envelopeRecebido.getProdutor());
        PublicKey chavePublica = Criptografia.carregarChavePublica(chavePublicaBase64);

        if (!Criptografia.validarAssinatura(
                envelopeRecebido.getDados(),
                envelopeRecebido.getAssinatura(),
                chavePublica)) {
            System.err.println("[MS Notificação] Assinatura inválida — mensagem descartada.");
            return;
        }

        Gson gson = new Gson();
        DadosEvento dados = gson.fromJson(envelopeRecebido.getDados(), DadosEvento.class);

        if (ENTRY_ROUTING_KEY_PUBLICADA.equals(routingKeyRecebido)) {
            tratarPromocaoPublicada(dados, envelopeRecebido.getDados(), channel);
        } else if (ENTRY_ROUTING_KEY_DESTAQUE.equals(routingKeyRecebido)) {
            tratarPromocaoDestaque(dados, envelopeRecebido.getDados(), channel);
        } else {
            System.out.println("[MS Notificação] Routing key desconhecida: " + routingKeyRecebido);
        }
    }

    private static void tratarPromocaoPublicada(DadosEvento dados, String dadosJson, Channel channel)
            throws Exception {

        System.out.println("[MS Notificação] Promoção publicada: " + dados.getIdItem());

        if (dados.getEmailLoja() != null && dados.getIdItem() != null) {
            EMAIL_POR_ITEM.put(dados.getIdItem(), dados.getEmailLoja());
        }

        ServicoEmail.enviarEmailPromocaoAprovada(
                dados.getEmailLoja(),
                dados.getIdItem(),
                dados.getIdPromocao());

        publicarParaGateway(channel, OUTPUT_ROUTING_KEY_CATEGORIA, dadosJson);
    }


    private static void tratarPromocaoDestaque(DadosEvento dados, String dadosJson, Channel channel)
            throws Exception {

        String status = dados.getStatus(); // "ADICIONADO" ou "REMOVIDO"
        System.out.println("[MS Notificação] Destaque recebido: " + dados.getIdItem()
                + " | status: " + status);

        String email = dados.getEmailLoja() != null
                ? dados.getEmailLoja()
                : EMAIL_POR_ITEM.get(dados.getIdItem());

        if ("ADICIONADO".equals(status)) {

            ServicoEmail.enviarEmailHotDeal(email, dados.getIdItem(), dados.getIdPromocao());
        }

        publicarParaGateway(channel, OUTPUT_ROUTING_KEY_HOTDEAL, dadosJson);
    }

    private static void publicarParaGateway(Channel channel, String routingKey, String dadosJson)
            throws Exception {

        String assinatura = Criptografia.assinarMensagem(dadosJson, KEYPAIR.getPrivate());
        EnvelopeUtil.Envelope envelopeSaida = new EnvelopeUtil.Envelope(CLASS_NAME, dadosJson, assinatura);

        channel.basicPublish(EXCHANGE_NAME, routingKey, null,
                envelopeSaida.toJson().getBytes(StandardCharsets.UTF_8));

        System.out.println("[MS Notificação] Evento publicado: " + routingKey);
    }
}