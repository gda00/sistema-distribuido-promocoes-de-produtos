package com.seguranca;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class GerenciadorDeChaves {

    private final static String NOME_DO_ARQUIVO = "chavesPublicas.txt";

    public static String buscarChave(String nomeServico) throws IOException {
        InputStream is = GerenciadorDeChaves.class.getClassLoader().getResourceAsStream(NOME_DO_ARQUIVO);

        if (is == null) {
            throw new IOException("Arquivo não encontrado no resources: " + NOME_DO_ARQUIVO);
        }

        try (BufferedReader br = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                if (linha.trim().isEmpty()) continue;

                String[] partes = linha.split(":", 2);

                if (partes.length == 2 && partes[0].trim().equals(nomeServico)) {
                    return partes[1].trim();
                }
            }
        }
        throw new IllegalArgumentException("Chave não encontrada para o serviço: " + nomeServico);
    }

    public static void salvarChave(String nomeServico, String chavePublicaBase64) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(NOME_DO_ARQUIVO, true))) {
            String novaLinha = nomeServico + ":" + chavePublicaBase64;
            writer.write(novaLinha);
            writer.newLine();
            writer.flush();
        }
    }
}