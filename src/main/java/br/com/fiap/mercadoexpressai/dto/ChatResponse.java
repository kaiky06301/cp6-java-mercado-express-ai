package br.com.fiap.mercadoexpressai.dto;

import br.com.fiap.mercadoexpressai.model.Conversa;

import java.time.LocalDateTime;

public record ChatResponse(
        Long id,
        String pergunta,
        String resposta,
        String provedor,
        String modelo,
        LocalDateTime dataHora) {

    public static ChatResponse from(Conversa conversa) {
        return new ChatResponse(conversa.getId(), conversa.getPergunta(), conversa.getResposta(),
                conversa.getProvedor(), conversa.getModelo(), conversa.getDataHora());
    }
}
