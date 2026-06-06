package com.seguranca;

public class DadosEvento {

    // Campos originais
    private String categoria;   // ex: "categoria.livros"
    private String idItem;      // nome/título do produto em promoção
    private String valor;       // ex: "R$ 20,00"
    private String voto;        // "positivo" ou "negativo" (só usado em eventos de voto)

    // Campos novos
    private String idPromocao;  // UUID gerado pela Loja — identifica a promoção unicamente
    private String emailLoja;   // e-mail da loja para notificações
    private String descricao;   // descrição opcional da promoção

    public DadosEvento() {}

    // Construtor original mantido para compatibilidade
    public DadosEvento(String categoria, String idItem, String valor, String voto) {
        this.categoria = categoria;
        this.idItem = idItem;
        this.valor = valor;
        this.voto = voto;
    }

    // Construtor completo (usado pela Loja ao cadastrar)
    public DadosEvento(String idPromocao, String categoria, String idItem,
                       String valor, String emailLoja, String descricao) {
        this.idPromocao = idPromocao;
        this.categoria = categoria;
        this.idItem = idItem;
        this.valor = valor;
        this.emailLoja = emailLoja;
        this.descricao = descricao;
    }

    // Getters originais
    public String getCategoria()  { return categoria; }
    public String getIdItem()     { return idItem; }
    public String getValor()      { return valor; }
    public String getVoto()       { return voto; }

    // Getters novos
    public String getIdPromocao() { return idPromocao; }
    public String getEmailLoja()  { return emailLoja; }
    public String getDescricao()  { return descricao; }

    // Setters (originais + novos)
    public void setIdItem(String idItem)         { this.idItem = idItem; }
    public void setCategoria(String categoria)   { this.categoria = categoria; }
    public void setIdPromocao(String idPromocao) { this.idPromocao = idPromocao; }
    public void setEmailLoja(String emailLoja)   { this.emailLoja = emailLoja; }
    public void setVoto(String voto)             { this.voto = voto; }
    public void setDescricao(String descricao)   { this.descricao = descricao; }

    @Override
    public String toString() {
        return "DadosEvento{" +
                "idPromocao='" + idPromocao + '\'' +
                ", categoria='" + categoria + '\'' +
                ", idItem='" + idItem + '\'' +
                ", valor='" + valor + '\'' +
                ", emailLoja='" + emailLoja + '\'' +
                ", descricao='" + descricao + '\'' +
                ", voto='" + voto + '\'' +
                '}';
    }
}