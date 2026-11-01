package br.com.fiap.mercadoexpressai.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChatRequest(
        @NotBlank(message = "A mensagem não pode estar vazia.")
        @Size(max = ChatRequest.TAMANHO_MAXIMO,
                message = "A mensagem deve ter no máximo " + ChatRequest.TAMANHO_MAXIMO + " caracteres.")
        String mensagem) {

    public static final int TAMANHO_MAXIMO = 1000;
}
