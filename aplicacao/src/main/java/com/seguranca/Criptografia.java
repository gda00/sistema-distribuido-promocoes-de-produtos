package com.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.*;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class Criptografia {

    public static KeyPair gerarPardeChaves() throws NoSuchAlgorithmException{
        KeyPairGenerator gerador = KeyPairGenerator.getInstance("RSA");
        gerador.initialize(2048);

        return gerador.generateKeyPair();
    }

    public static String assinarMensagem(String conteudo, PrivateKey chavePrivada) throws Exception{
        Signature assinatura = Signature.getInstance("SHA256withRSA");
        assinatura.initSign(chavePrivada);
        assinatura.update(conteudo.getBytes(StandardCharsets.UTF_8));

        byte[] bytesAssinatura = assinatura.sign();
        return Base64.getEncoder().encodeToString(bytesAssinatura);
    }

    public static boolean validarAssinatura(String conteudo, String assinaturaBase64, PublicKey chavePublica) throws Exception{
        Signature assinatura = Signature.getInstance("SHA256withRSA");
        assinatura.initVerify(chavePublica);
        assinatura.update(conteudo.getBytes(StandardCharsets.UTF_8));
        byte[] byesAssinatura = Base64.getDecoder().decode(assinaturaBase64);
        return assinatura.verify(byesAssinatura);
    }

    public static PublicKey carregarChavePublica(String chaveBase64) throws Exception{
        byte[] bytesChave = Base64.getDecoder().decode(chaveBase64);
        X509EncodedKeySpec spec = new X509EncodedKeySpec(bytesChave);
        KeyFactory kf = KeyFactory.getInstance("RSA");
        return kf.generatePublic(spec);
    }
}
