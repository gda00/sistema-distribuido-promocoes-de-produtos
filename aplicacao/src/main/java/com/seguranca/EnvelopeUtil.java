package com.seguranca;
import com.google.gson.Gson;

public class EnvelopeUtil {

    public static class Envelope{
        private String produtor;
        private String dados;
        private String assinatura;

        public String getProdutor() {return produtor;}
        public String getDados() {return dados;}
        public String getAssinatura() {return assinatura;}

        public static Envelope separar(String mensagemJsonCompleta) {
            Gson gson = new Gson();

            return gson.fromJson(mensagemJsonCompleta, Envelope.class);
        }

    }
}
