package com.microsservico_gateway;

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
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeoutException;

public class MicrosservicoGateway {

    private final static String HOST = "localhost";
    private final static String CLASS_NAME = MicrosservicoGateway.class.getSimpleName();

    private final static String EXCHANGE_NAME = "promocoes";
    private final static String EXCHANGE_TYPE = "topic";

    private final static String ENTRY_QUEUE_NAME = "fila.gateway";
    private final static String ENTRY_ROUTING_KEY = "promocao.publicada";

    private final static String OUTPUT_ROUTING_KEY_RECEBIDA = "promocao.recebida";
    private final static String OUTPUT_ROUTING_KEY_VOTO = "promocao.voto";

    private final static KeyPair KEYPAIR;

    static {
        try {
            KEYPAIR = Criptografia.gerarPardeChaves();
        } catch (NoSuchAlgorithmException e){
            throw new RuntimeException(e);
        }
    }

    private final static String CHAVE_PUBLICA_BASE64 = Base64.getEncoder().encodeToString(KEYPAIR.getPublic().getEncoded());

    private static final List<String> promocoesValidadasLocal = new ArrayList<>();

    public static void main(String[] args) throws IOException, TimeoutException {
        GerenciadorDeChaves.salvarChave(CLASS_NAME, CHAVE_PUBLICA_BASE64);

        ConnectionFactory connectionFactory = new ConnectionFactory();
        connectionFactory.setHost(HOST);
        Connection connection = connectionFactory.newConnection();
        Channel channel = connection.createChannel();

        channel.exchangeDeclare(EXCHANGE_NAME, EXCHANGE_TYPE);
        channel.queueDeclare(ENTRY_QUEUE_NAME, false, false, false, null);
        channel.queueBind(ENTRY_QUEUE_NAME, EXCHANGE_NAME, ENTRY_ROUTING_KEY);

        DeliverCallback deliverCallback = (consumerTag, delivery) -> {
            String message = new String(delivery.getBody(), StandardCharsets.UTF_8);
            try{
                processarPromocaoPublicada(message);
            } catch (Exception e) {
                System.err.println("Erro ao processar mensagem recebida: " + e.getMessage());
            } finally {
                channel.basicAck(delivery.getEnvelope().getDeliveryTag(), false);
            }
        };
        channel.basicConsume(ENTRY_QUEUE_NAME, false, deliverCallback, consumerTag -> {});

        iniciarMenu(channel);
    }

    private static void processarPromocaoPublicada(String message){
        try{
            EnvelopeUtil.Envelope envelopeRecebido = EnvelopeUtil.Envelope.separar(message);

            String chavePublicaBase64 = GerenciadorDeChaves.buscarChave(envelopeRecebido.getProdutor());
            PublicKey chavePublica = Criptografia.carregarChavePublica(chavePublicaBase64);

            if(Criptografia.validarAssinatura(envelopeRecebido.getDados(), envelopeRecebido.getAssinatura(), chavePublica)){
                promocoesValidadasLocal.add(envelopeRecebido.getDados());
            } else {
                System.err.println("\n[AVISO] Assinatura inválida! Promoção descartada...");
            }

        } catch (Exception e){
            System.out.println("\nErro na validação da promoção: " + e.getMessage());
        }
    }

    private static void iniciarMenu(Channel channel){
        Scanner scanner = new Scanner(System.in);

        while (true){
            System.out.println("\n------- MICROSSERVIÇO GATEWAY -------");
            System.out.println("1. Cadastrar nova promoção");
            System.out.println("2. Votar em uma promoção");
            System.out.println("3. Listar promoções ativas");
            System.out.println("0. Sair");
            System.out.print("Escolha uma opção: ");

            String opcao = scanner.nextLine();
            Gson gson = new Gson();

            try {
                switch (opcao){
                    case "1":
                        System.out.println("Digite os dados da promoção: ");
                        System.out.println("Ex: {\"categoria\":\"livros\",\"idItem\":\"Lua Nova\",\"valor\":\"R$ 20,00\"}");
                        String dadosPromocao = scanner.nextLine();
                        if (!dadosPromocao.trim().startsWith("{") || !dadosPromocao.trim().endsWith("}")) {
                            System.out.println("[Erro] O formato precisa ser um JSON válido (começar com '{' e terminar com '}'). Tente novamente.");
                            break;
                        }
                        DadosEvento dadosPub = gson.fromJson(dadosPromocao, DadosEvento.class);
                        dadosPub.setCategoria("categoria."+dadosPub.getCategoria());

                        publicarEvento(channel, OUTPUT_ROUTING_KEY_RECEBIDA, gson.toJson(dadosPub));
                        break;
                    case "2":
                        System.out.println("Digite os dados do voto: ");
                        System.out.println("\"Ex: {\"categoria\":\"livros\",\"idItem\":\"Lua Nova\",\"valor\":\"R$ 20,00\",\"voto\":\"positivo\"}");
                        String dadosVoto = scanner.nextLine();
                        if (!dadosVoto.trim().startsWith("{") || !dadosVoto.trim().endsWith("}")) {
                            System.out.println("[Erro] O formato precisa ser um JSON válido (começar com '{' e terminar com '}'). Tente novamente.");
                            break;
                        }
                        DadosEvento dadosPubVoto = gson.fromJson(dadosVoto, DadosEvento.class);
                        dadosPubVoto.setCategoria("categoria."+dadosPubVoto.getCategoria());

                        publicarEvento(channel, OUTPUT_ROUTING_KEY_VOTO, gson.toJson(dadosPubVoto));
                        break;
                    case "3":
                        System.out.println("\n--- Promocoes Ativas ---");
                        if(promocoesValidadasLocal.isEmpty()){
                            System.out.println("Nenhuma promoção validada até o momento :( ");
                        } else{
                            promocoesValidadasLocal.forEach(System.out::println);
                        }
                        break;
                    case "0":
                        System.exit(0);
                    default:
                        System.out.println("Opçcao invalida");
                }
            }catch (Exception e){
                System.err.println("Erro ao realizar operacao: " + e.getMessage());
            }
        }
    }
    private static void publicarEvento(Channel channel, String routingKey, String dados) throws Exception {
        String assinatura = Criptografia.assinarMensagem(dados, KEYPAIR.getPrivate());
        EnvelopeUtil.Envelope envelopeSaida = new EnvelopeUtil.Envelope(CLASS_NAME, dados, assinatura);
        String jsonSaida = envelopeSaida.toJson();

        channel.basicPublish(EXCHANGE_NAME, routingKey, null, jsonSaida.getBytes(StandardCharsets.UTF_8));
        System.out.println("=> Evento publicado: " + routingKey);
    }

}
