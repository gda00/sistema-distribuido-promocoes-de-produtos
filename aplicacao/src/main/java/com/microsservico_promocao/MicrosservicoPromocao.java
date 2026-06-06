package com.microsservico_promocao;

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

public class MicrosservicoPromocao {

    private final static String HOST = "localhost";
    private final static String CLASS_NAME = MicrosservicoPromocao.class.getSimpleName();

    private final static String EXCHANGE_NAME = "promocoes";
    private final static String EXCHANGE_TYPE = "topic";

    private final static String ENTRY_QUEUE_NAME  = "fila.promocao";
    private final static String ENTRY_ROUTING_KEY = "promocao.recebida";
    private final static String OUTPUT_ROUTING_KEY = "promocao.publicada";

    private final static int PREFETCH_COUNT = 1;

    // ---------------------------------------------------------------
    // Persistência em memória: id → promoção validada
    // (representa o "Banco de Dados" do MS Promoção no diagrama)
    // ---------------------------------------------------------------
    private static final Map<String, DadosEvento> PROMOCOES_SALVAS = new ConcurrentHashMap<>();

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
            try {
                publicarPromocao(envelope, channel);
            } catch (TimeoutException e) {
                throw new RuntimeException(e);
            } finally {
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };

        channel.basicConsume(ENTRY_QUEUE_NAME, false, deliverCallback, consumerTag -> {});
        System.out.println("[MS Promoção] Aguardando eventos em " + ENTRY_ROUTING_KEY);
    }

    private static void publicarPromocao(String message, Channel channel) throws TimeoutException {
        EnvelopeUtil.Envelope envelopeRecebido = EnvelopeUtil.Envelope.separar(message);

        try {
            // 1. Buscar chave pública do produtor (Gateway ou Loja) e validar assinatura
            String chavePublicaBase64 = GerenciadorDeChaves.buscarChave(envelopeRecebido.getProdutor());
            PublicKey chavePublicaRecebida = Criptografia.carregarChavePublica(chavePublicaBase64);

            if (!Criptografia.validarAssinatura(
                    envelopeRecebido.getDados(),
                    envelopeRecebido.getAssinatura(),
                    chavePublicaRecebida)) {
                System.err.println("[MS Promoção] Assinatura inválida — promoção descartada.");
                return;
            }

            // 2. Deserializar e persistir localmente
            Gson gson = new Gson();
            DadosEvento dados = gson.fromJson(envelopeRecebido.getDados(), DadosEvento.class);

            if (dados.getIdPromocao() != null) {
                PROMOCOES_SALVAS.put(dados.getIdPromocao(), dados);
                System.out.println("[MS Promoção] Promoção salva: " + dados.getIdPromocao()
                        + " | " + dados.getIdItem());
            }

            // 3. Re-assinar com a chave privada deste MS e publicar como "promocao.publicada"
            String novaAssinatura = Criptografia.assinarMensagem(
                    envelopeRecebido.getDados(), KEYPAIR.getPrivate());

            EnvelopeUtil.Envelope envelopeRetorno = new EnvelopeUtil.Envelope(
                    CLASS_NAME, envelopeRecebido.getDados(), novaAssinatura);

            channel.basicPublish(EXCHANGE_NAME, OUTPUT_ROUTING_KEY, null,
                    envelopeRetorno.toJson().getBytes(StandardCharsets.UTF_8));

            System.out.println("[MS Promoção] Evento publicado: " + OUTPUT_ROUTING_KEY);

        } catch (Exception e) {
            System.err.println("[MS Promoção] Erro ao processar promoção: " + e.getMessage());
            throw new RuntimeException(e);
        }
    }

    // Acessível por outros componentes na mesma JVM se necessário
    public static Map<String, DadosEvento> getPromocoesSalvas() {
        return PROMOCOES_SALVAS;
    }
}