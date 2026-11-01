package br.com.fiap.mercadoexpressai.exception;

import org.springframework.http.HttpStatus;

/**
 * Falha ao consultar o provedor de IA. Carrega o status HTTP que será devolvido
 * ao cliente e uma mensagem segura (sem chaves, URLs internas ou stack trace).
 */
public class ProvedorIaException extends RuntimeException {

    private final HttpStatus status;

    public ProvedorIaException(HttpStatus status, String mensagem, Throwable causa) {
        super(mensagem, causa);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
