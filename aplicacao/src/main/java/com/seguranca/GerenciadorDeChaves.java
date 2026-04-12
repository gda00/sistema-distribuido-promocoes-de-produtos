package com.seguranca;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class GerenciadorDeChaves {
    public static String buscarChave(String caminhoArquivo, String nomeServico) throws IOException {
        try (BufferedReader br = new BufferedReader(new FileReader(caminhoArquivo))) {
            String linha;
            while ((linha = br.readLine()) != null) {

                if (linha.trim().isEmpty()) continue;

                String[] partes = linha.split(":");

                if (partes.length == 2 && partes[0].trim().equals(nomeServico)) {
                    return partes[1].trim();
                }
            }
        }
        throw new IllegalArgumentException("Chave não encontrada para o serviço: " + nomeServico);
    }

}
