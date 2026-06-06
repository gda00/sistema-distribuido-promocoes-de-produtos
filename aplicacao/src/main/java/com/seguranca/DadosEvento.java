package com.seguranca;

public class DadosEvento {

    private String categoria;
    private String idItem;
    private String valor;
    private String voto;

    private String idPromocao;
    private String emailLoja;
    private String descricao;
    public DadosEvento() {}

    public DadosEvento(String categoria, String idItem, String valor, String voto) {
        this.categoria = categoria;
        this.idItem = idItem;
        this.valor = valor;
        this.voto = voto;
    }

    public DadosEvento(String idPromocao, String categoria, String idItem,
                       String valor, String emailLoja, String descricao) {
        this.idPromocao = idPromocao;
        this.categoria = categoria;
        this.idItem = idItem;
        this.valor = valor;
        this.emailLoja = emailLoja;
        this.descricao = descricao;
    }

    public String getCategoria()  { return categoria; }
    public String getIdItem()     { return idItem; }
    public String getValor()      { return valor; }
    public String getVoto()       { return voto; }
    public String getIdPromocao() { return idPromocao; }
    public String getEmailLoja()  { return emailLoja; }
    public String getDescricao()  { return descricao; }

    public void setIdItem(String idItem)         { this.idItem = idItem; }
    public void setCategoria(String categoria)   { this.categoria = categoria; }
    public void setIdPromocao(String idPromocao) { this.idPromocao = idPromocao; }
    public void setEmailLoja(String emailLoja)   { this.emailLoja = emailLoja; }
    public void setVoto(String voto)             { this.voto = voto; }
    public void setDescricao(String descricao)   { this.descricao = descricao; }
    public void setValor(String valor)           { this.valor = valor; }

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