package br.com.fiap.mercadoexpressai.controller;

import br.com.fiap.mercadoexpressai.repository.ConversaRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Fluxo completo (controller -> service -> JPA/H2) com o modelo de IA simulado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class HistoricoIntegracaoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ConversaRepository repository;

    @MockitoBean
    private ChatModel chatModel;

    @BeforeEach
    void limpar() {
        repository.deleteAll();
        when(chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
    }

    private void perguntar(String pergunta, String resposta) throws Exception {
        // 1ª chamada: classificador de domínio; 2ª: resposta do assistente
        when(chatModel.call(any(Prompt.class))).thenReturn(
                new ChatResponse(List.of(new Generation(new AssistantMessage("SIM")))),
                new ChatResponse(List.of(new Generation(new AssistantMessage(resposta)))));
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensagem\":\"" + pergunta + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provedor").value("Ollama"));
    }

    @Test
    void gravaConversasEListaHistoricoOrdenadoPorData() throws Exception {
        perguntar("Primeira pergunta", "Primeira resposta");
        perguntar("Segunda pergunta", "Segunda resposta");

        assertThat(repository.count()).isEqualTo(2);

        mockMvc.perform(get("/api/historico"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].pergunta").value("Segunda pergunta"))
                .andExpect(jsonPath("$[1].pergunta").value("Primeira pergunta"))
                .andExpect(jsonPath("$[0].resposta").value("Segunda resposta"))
                .andExpect(jsonPath("$[0].modelo").exists())
                .andExpect(jsonPath("$[0].dataHora").exists());

        mockMvc.perform(get("/api/historico").param("ordem", "asc"))
                .andExpect(jsonPath("$[0].pergunta").value("Primeira pergunta"));
    }

    @Test
    void falhaDoProvedorNaoGravaNoHistorico() throws Exception {
        when(chatModel.call(any(Prompt.class)))
                .thenThrow(new org.springframework.web.client.ResourceAccessException("Connection refused"));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensagem\":\"Olá\"}"))
                .andExpect(status().isServiceUnavailable());

        assertThat(repository.count()).isZero();
    }
}
