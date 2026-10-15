package br.com.rabbitmq.consumidor.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.io.Serializable;

public class Mensagem implements Serializable {

    private static final long serialVersionUID = 1L;

    @JsonProperty("id")
    private String id;

    @JsonProperty("timestamp")
    private String timestamp;

    @JsonProperty("tipo")
    private TipoMensagem tipo;

    @JsonProperty("conteudo")
    private String conteudo;

    public Mensagem() {
    }

    public Mensagem(String id, String timestamp, TipoMensagem tipo, String conteudo) {
        this.id = id;
        this.timestamp = timestamp;
        this.tipo = tipo;
        this.conteudo = conteudo;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }

    public TipoMensagem getTipo() {
        return tipo;
    }

    public void setTipo(TipoMensagem tipo) {
        this.tipo = tipo;
    }

    public String getConteudo() {
        return conteudo;
    }

    public void setConteudo(String conteudo) {
        this.conteudo = conteudo;
    }

    @Override
    public String toString() {
        return "Mensagem{" +
                "id='" + id + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", tipo=" + tipo +
                ", conteudo='" + conteudo + '\'' +
                '}';
    }
}
