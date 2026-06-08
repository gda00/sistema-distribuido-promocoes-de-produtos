package com.seguranca;

public class DadosEvento {

    private String categoria;
    private String idItem;
    private String valor;
    private String voto;

    private String idPromocao;
    private String emailLoja;
    private String descricao;
    private String status;
    public DadosEvento() {}


    public String getCategoria()  { return categoria; }
    public String getIdItem()     { return idItem; }
    public String getValor()      { return valor; }
    public String getVoto()       { return voto; }
    public String getIdPromocao() { return idPromocao; }
    public String getEmailLoja()  { return emailLoja; }
    public String getDescricao()  { return descricao; }
    public String getStatus()     { return status; }

    public void setIdItem(String idItem)         { this.idItem = idItem; }
    public void setCategoria(String categoria)   { this.categoria = categoria; }
    public void setIdPromocao(String idPromocao) { this.idPromocao = idPromocao; }
    public void setStatus(String status)         { this.status = status; }

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