package com;

import com.microsservico_gateway.MicrosservicoGateway;
import com.microsservico_notificacao.MicrosservicoNotificacao;
import com.microsservico_promocao.MicrosservicoPromocao;
import com.microsservico_ranking.MicrosservicoRanking;
import com.seguranca.GerenciadorDeChaves;

import java.io.IOException;

public class Main {
    public static void main(String[] args) throws IOException {
        GerenciadorDeChaves.limparArquivo();

        new Thread(() -> {
            try {
                MicrosservicoPromocao.main(new String[]{});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                MicrosservicoRanking.main(new String[]{});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        new Thread(() -> {
            try {
                MicrosservicoNotificacao.main(new String[]{});
            } catch (Exception e) {
                e.printStackTrace();
            }
        }).start();

        try {
            MicrosservicoGateway.main(new String[]{});
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}