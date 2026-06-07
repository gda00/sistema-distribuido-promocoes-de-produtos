package com.microsservico_notificacao;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class ServicoEmail {
    private static final String RESEND_API_URL = "https://api.resend.com/emails";

    // Lê da variável de ambiente; se não definida, usa valor padrão de teste
    private static final String API_KEY = System.getenv().getOrDefault(
            "RESEND_API_KEY", "SEM_CHAVE_CONFIGURADA");

    private static final String REMETENTE = System.getenv().getOrDefault(
            "RESEND_FROM", "onboarding@resend.dev");

    private static final HttpClient HTTP_CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /**
     * Envia um e-mail para a loja notificando que a promoção foi aprovada.
     */
    public static void enviarEmailPromocaoAprovada(String emailLoja, String nomeItem, String idPromocao) {
        if (emailLoja == null || emailLoja.isBlank()) {
            System.out.println("[EmailService] emailLoja não informado — e-mail não enviado.");
            return;
        }

        String assunto = "✅ Sua promoção foi aprovada!";
        String corpo = "<h2>Promoção aprovada</h2>"
                + "<p>Sua promoção <strong>" + nomeItem + "</strong> foi validada e publicada no sistema.</p>"
                + "<p><small>ID da promoção: " + idPromocao + "</small></p>";

        enviar(emailLoja, assunto, corpo);
    }

    /**
     * Envia um e-mail para a loja notificando que a promoção virou hot deal.
     */
    public static void enviarEmailHotDeal(String emailLoja, String nomeItem, String idPromocao) {
        if (emailLoja == null || emailLoja.isBlank()) {
            System.out.println("[EmailService] emailLoja não informado — e-mail não enviado.");
            return;
        }

        String assunto = "🔥 Sua promoção virou Hot Deal!";
        String corpo = "<h2>Hot Deal!</h2>"
                + "<p>Sua promoção <strong>" + nomeItem + "</strong> atingiu o limite de votos positivos "
                + "e agora está em destaque no sistema.</p>"
                + "<p><small>ID da promoção: " + idPromocao + "</small></p>";

        enviar(emailLoja, assunto, corpo);
    }

    // Método interno: monta o JSON e faz a chamada HTTP para o Resend
    private static void enviar(String destinatario, String assunto, String corpoHtml) {
        if (API_KEY.equals("SEM_CHAVE_CONFIGURADA")) {
            System.out.println("[EmailService] RESEND_API_KEY não configurada — e-mail simulado para: " + destinatario);
            return;
        }
        // Monta o JSON manualmente (sem dependência extra)
        String json = "{"
                + "\"from\":\"" + REMETENTE + "\","
                + "\"to\":[\"" + destinatario + "\"],"
                + "\"subject\":\"" + assunto + "\","
                + "\"html\":\"" + corpoHtml.replace("\"", "\\\"") + "\""
                + "}";

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(RESEND_API_URL))
                .header("Authorization", "Bearer " + API_KEY)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(json))
                .timeout(Duration.ofSeconds(15))
                .build();

        try {
            HttpResponse<String> response = HTTP_CLIENT.send(
                    request, HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() == 200 || response.statusCode() == 201) {
                System.out.println("[EmailService] E-mail enviado para: " + destinatario
                        + " | Assunto: " + assunto);
            } else {
                System.err.println("[EmailService] Falha ao enviar e-mail. Status: "
                        + response.statusCode() + " | Body: " + response.body());
            }
        } catch (Exception e) {
            System.err.println("[EmailService] Erro na chamada HTTP: " + e.getMessage());
        }
    }
}
