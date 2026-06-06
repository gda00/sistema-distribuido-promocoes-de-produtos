package com.microsservico_gateway.service;

import com.google.gson.Gson;
import com.microsservico_gateway.config.RabbitMQConfig;
import com.seguranca.*;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.security.*;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class GatewayService {

    private static final String CLASS_NAME = "MicrosservicoGateway";

    private final RabbitTemplate rabbitTemplate;
    private final Gson gson = new Gson();
    private final KeyPair keyPair;
    private final List<String> promocoesValidadas = new CopyOnWriteArrayList<>();

    public GatewayService(RabbitTemplate rabbitTemplate) throws NoSuchAlgorithmException, IOException {
        this.rabbitTemplate = rabbitTemplate;
        this.keyPair = Criptografia.gerarPardeChaves();

        String chavePublicaBase64 = Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded());
        GerenciadorDeChaves.salvarChave(CLASS_NAME, chavePublicaBase64);
    }

    public void cadastrarPromocao(DadosEvento dados) throws Exception {
        dados.setCategoria("categoria." + dados.getCategoria());
        publicarEvento(RabbitMQConfig.ROUTING_KEY_RECEBIDA, gson.toJson(dados));
    }

    public void votar(DadosEvento dados) throws Exception {
        dados.setCategoria("categoria." + dados.getCategoria());
        publicarEvento(RabbitMQConfig.ROUTING_KEY_VOTO, gson.toJson(dados));
    }

    public List<String> listarPromocoes() {
        return Collections.unmodifiableList(promocoesValidadas);
    }

    @RabbitListener(queues = RabbitMQConfig.ENTRY_QUEUE_NAME)
    public void receberPromocaoPublicada(String message) {
        try {
            EnvelopeUtil.Envelope envelope = EnvelopeUtil.Envelope.separar(message);
            String chaveBase64 = GerenciadorDeChaves.buscarChave(envelope.getProdutor());
            PublicKey chave = Criptografia.carregarChavePublica(chaveBase64);

            if (Criptografia.validarAssinatura(envelope.getDados(), envelope.getAssinatura(), chave)) {
                promocoesValidadas.add(envelope.getDados());
            } else {
                System.err.println("[Gateway] Assinatura inválida — promoção descartada.");
            }
        } catch (Exception e) {
            System.err.println("[Gateway] Erro ao processar mensagem: " + e.getMessage());
        }
    }

    private void publicarEvento(String routingKey, String dados) throws Exception {
        String assinatura = Criptografia.assinarMensagem(dados, keyPair.getPrivate());
        EnvelopeUtil.Envelope envelope = new EnvelopeUtil.Envelope(CLASS_NAME, dados, assinatura);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, envelope.toJson());
        System.out.println("[Gateway] Evento publicado: " + routingKey);
    }
}