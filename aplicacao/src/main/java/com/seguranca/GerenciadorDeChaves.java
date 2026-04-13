package com.seguranca;

import java.io.*;
import java.nio.charset.StandardCharsets;

public class GerenciadorDeChaves {

    private final static String CAMINHO_ARQUIVO = "aplicacao/src/main/java/com/chavesPublicas.txt";

    public static String buscarChave(String nomeServico) throws IOException {
        File arquivo = new File(CAMINHO_ARQUIVO);
        if (!arquivo.exists()) {
            throw new IOException();
        }

        try (BufferedReader br = new BufferedReader(new FileReader(arquivo, StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = br.readLine()) != null) {
                if (linha.trim().isEmpty()) continue;

                String[] partes = linha.split(":", 2);
                if (partes.length == 2 && partes[0].trim().equals(nomeServico)) {
                    return partes[1].trim();
                }
            }
        }
        throw new IllegalArgumentException("Chave não encontrada para: " + nomeServico);
    }

    public static synchronized void salvarChave(String nomeServico, String chavePublica) throws IOException {
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(CAMINHO_ARQUIVO, true))) {
            String novaLinha = nomeServico + ":" + chavePublica;
            writer.write(novaLinha);
            writer.newLine();
            writer.flush();
        }
    }

    public static synchronized void limparArquivo() throws IOException {
        File arquivo = new File(CAMINHO_ARQUIVO);
        if (arquivo.exists()) {
            new FileWriter(arquivo, false).close();
        }
    }
}