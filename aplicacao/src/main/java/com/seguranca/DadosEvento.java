package com.seguranca;

public class DadosEvento {
    private String categoria;
    private String idItem;
    private String valor;
    private String voto;

    public DadosEvento() {}

    public DadosEvento(String categoria, String idItem, String valor, String voto) {
        this.categoria = categoria;
        this.idItem = idItem;
        this.valor = valor;
        this.voto = voto;
    }

    public String getCategoria() { return categoria; }
    public String getIdItem() { return idItem; }
    public String getValor() { return valor; }
    public String getVoto() { return voto; }
}
