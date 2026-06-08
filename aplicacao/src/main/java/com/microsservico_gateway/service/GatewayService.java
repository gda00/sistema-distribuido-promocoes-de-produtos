package com.microsservico_gateway.service;

import com.google.gson.Gson;
import com.microsservico_gateway.config.RabbitMQConfig;
import com.seguranca.*;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.util.*;
import java.util.concurrent.*;

@Service
public class GatewayService {

    private static final String CLASS_NAME = "MicrosservicoGateway";

    private final RabbitTemplate rabbitTemplate;
    private final Gson gson = new Gson();
    private final KeyPair keyPair;

    private final ConcurrentHashMap<String, String> promocoesValidadas = new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, SseEmitter> emitters      = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Set<String>> interesses    = new ConcurrentHashMap<>();

    public GatewayService(RabbitTemplate rabbitTemplate) throws NoSuchAlgorithmException, IOException {
        this.rabbitTemplate = rabbitTemplate;

        this.keyPair = Criptografia.gerarPardeChaves();
        String chavePublicaBase64 = Base64.getEncoder()
                .encodeToString(keyPair.getPublic().getEncoded());
        GerenciadorDeChaves.salvarChave(CLASS_NAME, chavePublicaBase64);
    }

    public void cadastrarPromocao(DadosEvento dados) throws Exception {
        if (dados.getIdPromocao() == null || dados.getIdPromocao().isBlank()) {
            dados.setIdPromocao(UUID.randomUUID().toString());
        }
        dados.setCategoria("categoria." + dados.getCategoria());
        publicarEvento(RabbitMQConfig.ROUTING_KEY_RECEBIDA, gson.toJson(dados));
    }

    public void votar(String idItem, DadosEvento voto) throws Exception {
        voto.setIdItem(idItem);
        voto.setCategoria("categoria." + voto.getCategoria());
        publicarEvento(RabbitMQConfig.ROUTING_KEY_VOTO, gson.toJson(voto));
    }

    public List<String> listarPromocoes() {
        return new ArrayList<>(promocoesValidadas.values());
    }

    public SseEmitter novaConexao(String clienteId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(clienteId, emitter);
        interesses.putIfAbsent(clienteId, ConcurrentHashMap.newKeySet());

        Runnable cleanup = () -> {
            emitters.remove(clienteId);
            interesses.remove(clienteId);
        };
        emitter.onCompletion(cleanup);
        emitter.onTimeout(cleanup);
        emitter.onError(e -> cleanup.run());

        try {
            emitter.send(SseEmitter.event().name("ping").data("ok"));
        } catch (IOException ignored) {
        }

        return emitter;
    }

    public void adicionarInteresse(String clienteId, String categoria) {
        interesses.computeIfAbsent(clienteId, k -> ConcurrentHashMap.newKeySet())
                .add("categoria." + categoria);
    }

    public void removerInteresse(String clienteId, String categoria) {
        Set<String> cats = interesses.get(clienteId);
        if (cats != null) cats.remove("categoria." + categoria);
    }

    @RabbitListener(queues = RabbitMQConfig.GATEWAY_QUEUE)
    public void receberPromocaoPublicada(String message) {
        try {
            EnvelopeUtil.Envelope envelope = EnvelopeUtil.Envelope.separar(message);
            String chaveBase64 = GerenciadorDeChaves.buscarChave(envelope.getProdutor());
            PublicKey chave = Criptografia.carregarChavePublica(chaveBase64);

            if (Criptografia.validarAssinatura(envelope.getDados(), envelope.getAssinatura(), chave)) {
                DadosEvento dados = gson.fromJson(envelope.getDados(), DadosEvento.class);
                String id = dados.getIdPromocao();
                if (id != null && !id.isBlank()) {
                    String anterior = promocoesValidadas.putIfAbsent(id, envelope.getDados());
                    if (anterior != null) {
                        System.out.println("[Gateway] Promoção " + id + " já existe — ignorada.");
                    }
                }
            } else {
                System.err.println("[Gateway] Assinatura inválida — promoção descartada.");
            }
        } catch (Exception e) {
            System.err.println("[Gateway] Erro: " + e.getMessage());
        }
    }

    //tirar cepa
    @RabbitListener(queues = RabbitMQConfig.SSE_QUEUE)
    public void receberEventoSSE(Message message) {
        String routingKey = message.getMessageProperties().getReceivedRoutingKey();
        String body = new String(message.getBody(), StandardCharsets.UTF_8);

        try {
            EnvelopeUtil.Envelope envelope = EnvelopeUtil.Envelope.separar(body);
            String chaveBase64 = GerenciadorDeChaves.buscarChave(envelope.getProdutor());
            PublicKey chave = Criptografia.carregarChavePublica(chaveBase64);

            if (!Criptografia.validarAssinatura(envelope.getDados(), envelope.getAssinatura(), chave)) {
                System.err.println("[Gateway SSE] Assinatura inválida — evento descartado.");
                return;
            }

            DadosEvento evento = gson.fromJson(envelope.getDados(), DadosEvento.class);

            if (RabbitMQConfig.ROUTING_KEY_HOTDEAL.equals(routingKey)) {
                transmitirParaTodos("notificacao.hotdeal", evento);        // hot deal → todos os clientes
            } else {
                transmitirParaCategoria("promocao.categoria", evento);   // categoria → clientes inscritos
            }
        } catch (Exception e) {
            System.err.println("[Gateway SSE] Erro: " + e.getMessage());
        }
    }

    private void transmitirParaTodos(String eventName, DadosEvento evento) {
        String payload = gson.toJson(evento);
        emitters.forEach((id, emitter) -> enviar(id, emitter, eventName, payload));
    }

    private void transmitirParaCategoria(String eventName, DadosEvento evento) {
        String categoriaEvento = evento.getCategoria(); // ex: "categoria.livros"
        String payload = gson.toJson(evento);

        emitters.forEach((clienteId, emitter) -> {
            Set<String> cats = interesses.getOrDefault(clienteId, Set.of());
            if (cats.contains(categoriaEvento)) {
                enviar(clienteId, emitter, eventName, payload);
            }
        });
    }

    private void enviar(String clienteId, SseEmitter emitter, String eventName, String payload) {
        try {
            emitter.send(SseEmitter.event().name(eventName).data(payload));
        } catch (IOException e) {
            emitters.remove(clienteId);
            interesses.remove(clienteId);
        }
    }

    private void publicarEvento(String routingKey, String dados) throws Exception {
        String assinatura = Criptografia.assinarMensagem(dados, keyPair.getPrivate());
        EnvelopeUtil.Envelope envelope = new EnvelopeUtil.Envelope(CLASS_NAME, dados, assinatura);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE_NAME, routingKey, envelope.toJson());
        System.out.println("[Gateway] Evento publicado: " + routingKey);
    }
}