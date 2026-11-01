package br.com.fiap.mercadoexpressai.dto;

import java.time.LocalDateTime;

public record ErroResponse(int status, String erro, String mensagem, LocalDateTime dataHora) {

    public ErroResponse(int status, String erro, String mensagem) {
        this(status, erro, mensagem, LocalDateTime.now());
    }
}
