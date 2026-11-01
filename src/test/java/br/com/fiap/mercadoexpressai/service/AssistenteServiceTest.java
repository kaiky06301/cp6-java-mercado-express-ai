package br.com.fiap.mercadoexpressai.service;

import br.com.fiap.mercadoexpressai.dto.ChatResponse;
import br.com.fiap.mercadoexpressai.exception.ProvedorIaException;
import br.com.fiap.mercadoexpressai.model.Conversa;
import br.com.fiap.mercadoexpressai.repository.ConversaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.ResourceAccessException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Testa o serviço com o ChatModel simulado (Mockito): não depende de rede, créditos nem do Ollama.
 */
class AssistenteServiceTest {

    private ChatModel chatModel;
    private ConversaRepository repository;
    private AssistenteService service;

    @BeforeEach
    void setUp() {
        chatModel = mock(ChatModel.class);
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
        repository = mock(ConversaRepository.class);
        when(repository.save(any(Conversa.class))).thenAnswer(invocacao -> {
            Conversa conversa = invocacao.getArgument(0);
            conversa.setId(1L);
            return conversa;
        });
        service = new AssistenteService(ChatClient.builder(chatModel), repository,
                new ByteArrayResource("Você é a Mel, assistente do Mercado Express.".getBytes()),
                new ByteArrayResource("Responda SIM ou NAO.".getBytes()),
                "Ollama", "qwen2.5:3b");
    }

    private static org.springframework.ai.chat.model.ChatResponse gerado(String texto) {
        return new org.springframework.ai.chat.model.ChatResponse(List.of(new Generation(new AssistantMessage(texto))));
    }

    /** 1ª chamada = classificador de domínio (SIM); 2ª chamada = resposta do assistente. */
    private void provedorResponde(String texto) {
        when(chatModel.call(any(Prompt.class))).thenReturn(gerado("SIM"), gerado(texto));
    }

    private void provedorFalha(RuntimeException erro) {
        when(chatModel.call(any(Prompt.class))).thenThrow(erro);
    }

    @Test
    void devolveRespostaEGravaHistoricoComProvedorEModelo() {
        provedorResponde("  Aceitamos Pix, crédito, débito e vale-alimentação.  ");

        ChatResponse resposta = service.perguntar("  Quais formas de pagamento?  ");

        assertThat(resposta.id()).isEqualTo(1L);
        assertThat(resposta.pergunta()).isEqualTo("Quais formas de pagamento?");
        assertThat(resposta.resposta()).isEqualTo("Aceitamos Pix, crédito, débito e vale-alimentação.");
        assertThat(resposta.provedor()).isEqualTo("Ollama");
        assertThat(resposta.modelo()).isEqualTo("qwen2.5:3b");
        assertThat(resposta.dataHora()).isNotNull();
        verify(repository).save(any(Conversa.class));
    }

    @Test
    void enviaInstrucaoDeSistemaExemplosEPerguntaAoModelo() {
        provedorResponde("ok");
        ArgumentCaptor<Prompt> prompt = ArgumentCaptor.forClass(Prompt.class);

        service.perguntar("Vocês entregam em Osasco?");

        verify(chatModel, times(2)).call(prompt.capture());
        var mensagens = prompt.getAllValues().get(1).getInstructions();
        assertThat(mensagens.getFirst().getMessageType()).isEqualTo(MessageType.SYSTEM);
        assertThat(mensagens.getFirst().getText()).contains("Mercado Express");
        assertThat(mensagens.getLast().getMessageType()).isEqualTo(MessageType.USER);
        assertThat(mensagens.getLast().getText()).startsWith("Vocês entregam em Osasco?");
    }

    @Test
    void perguntaForaDoDominioRecebeRecusaSemGerarResposta() {
        when(chatModel.call(any(Prompt.class))).thenReturn(gerado("NAO"));

        ChatResponse resposta = service.perguntar("Quem ganhou a Copa de 2002?");

        assertThat(resposta.resposta()).isEqualTo(AssistenteService.RECUSA);
        verify(chatModel, times(1)).call(any(Prompt.class));
        verify(repository).save(any(Conversa.class));
    }

    @Test
    void ollamaForaDoArViraServicoIndisponivelSemGravar() {
        provedorFalha(new ResourceAccessException("Connection refused: localhost/127.0.0.1:11434"));

        assertThatThrownBy(() -> service.perguntar("Olá"))
                .isInstanceOfSatisfying(ProvedorIaException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("Não foi possível conectar ao Ollama");
                    assertThat(e.getMessage()).doesNotContain("11434");
                });
        verify(repository, never()).save(any());
    }

    @Test
    void modeloNaoInstaladoOrientaOllamaPull() {
        provedorFalha(new NonTransientAiException("HTTP 404 - {\"error\":\"model 'qwen2.5:3b' not found\"}"));

        assertThatThrownBy(() -> service.perguntar("Olá"))
                .isInstanceOfSatisfying(ProvedorIaException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(e.getMessage()).contains("ollama pull qwen2.5:3b");
                });
    }

    @Test
    void chaveInvalidaViraFalhaDeAutenticacao() {
        provedorFalha(new NonTransientAiException("HTTP 401 - Unauthorized: invalid api key sk-segredo"));

        assertThatThrownBy(() -> service.perguntar("Olá"))
                .isInstanceOfSatisfying(ProvedorIaException.class, e -> {
                    assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY);
                    assertThat(e.getMessage()).contains("autenticação").doesNotContain("sk-segredo");
                });
    }

    @Test
    void semSaldoViraMensagemDeCota() {
        provedorFalha(new NonTransientAiException("HTTP 402 - insufficient_quota"));

        assertThatThrownBy(() -> service.perguntar("Olá"))
                .isInstanceOfSatisfying(ProvedorIaException.class,
                        e -> assertThat(e.getMessage()).contains("sem saldo ou cota"));
    }

    @Test
    void limiteDeRequisicoesViraTooManyRequests() {
        provedorFalha(new NonTransientAiException("HTTP 429 - rate limit reached"));

        assertThatThrownBy(() -> service.perguntar("Olá"))
                .isInstanceOfSatisfying(ProvedorIaException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void falhaTemporariaDoModeloViraServicoIndisponivel() {
        provedorFalha(new TransientAiException("HTTP 503 - model is loading"));

        assertThatThrownBy(() -> service.perguntar("Olá"))
                .isInstanceOfSatisfying(ProvedorIaException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
    }

    @Test
    void respostaVaziaDoModeloNaoEGravada() {
        provedorResponde("   ");

        assertThatThrownBy(() -> service.perguntar("Olá"))
                .isInstanceOfSatisfying(ProvedorIaException.class,
                        e -> assertThat(e.getStatus()).isEqualTo(HttpStatus.BAD_GATEWAY));
        verify(repository, never()).save(any());
    }
}
