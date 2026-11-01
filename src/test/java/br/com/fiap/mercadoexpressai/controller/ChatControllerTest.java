package br.com.fiap.mercadoexpressai.controller;

import br.com.fiap.mercadoexpressai.dto.ChatResponse;
import br.com.fiap.mercadoexpressai.exception.ProvedorIaException;
import br.com.fiap.mercadoexpressai.service.AssistenteService;
import br.com.fiap.mercadoexpressai.service.HistoricoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Testa a camada web (validação e tratamento de erros) com o serviço simulado.
 */
@WebMvcTest(controllers = {ChatController.class, HistoricoController.class})
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AssistenteService assistenteService;

    @MockitoBean
    private HistoricoService historicoService;

    private void postarChat(String json, int statusEsperado, String mensagemEsperada) throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().is(statusEsperado))
                .andExpect(jsonPath("$.mensagem").value(mensagemEsperada));
    }

    @Test
    void perguntaValidaRetornaCamposObrigatorios() throws Exception {
        when(assistenteService.perguntar("Vocês aceitam Pix?")).thenReturn(new ChatResponse(
                7L, "Vocês aceitam Pix?", "Sim, aceitamos Pix.", "Ollama", "qwen2.5:3b",
                LocalDateTime.of(2026, 11, 1, 10, 30)));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensagem\":\"Vocês aceitam Pix?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.resposta").value("Sim, aceitamos Pix."))
                .andExpect(jsonPath("$.provedor").value("Ollama"))
                .andExpect(jsonPath("$.modelo").value("qwen2.5:3b"))
                .andExpect(jsonPath("$.dataHora").value("2026-11-01T10:30:00"));
    }

    @Test
    void mensagemVaziaRetorna400() throws Exception {
        postarChat("{\"mensagem\":\"\"}", 400, "A mensagem não pode estar vazia.");
        verifyNoInteractions(assistenteService);
    }

    @Test
    void mensagemSoComEspacosRetorna400() throws Exception {
        postarChat("{\"mensagem\":\"    \"}", 400, "A mensagem não pode estar vazia.");
    }

    @Test
    void campoMensagemAusenteRetorna400() throws Exception {
        postarChat("{}", 400, "A mensagem não pode estar vazia.");
    }

    @Test
    void mensagemAcimaDoTamanhoMaximoRetorna400() throws Exception {
        postarChat("{\"mensagem\":\"" + "a".repeat(1001) + "\"}", 400,
                "A mensagem deve ter no máximo 1000 caracteres.");
        verifyNoInteractions(assistenteService);
    }

    @Test
    void jsonMalFormadoRetorna400() throws Exception {
        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON).content("{mensagem"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensagem").exists());
    }

    @Test
    void provedorIndisponivelRetorna503ComMensagemCompreensivel() throws Exception {
        when(assistenteService.perguntar(anyString())).thenThrow(new ProvedorIaException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Não foi possível conectar ao Ollama. Verifique se o serviço está em execução e tente novamente.",
                new RuntimeException("Connection refused: localhost/127.0.0.1:11434")));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensagem\":\"Olá\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensagem").value(
                        "Não foi possível conectar ao Ollama. Verifique se o serviço está em execução e tente novamente."));
    }

    @Test
    void limiteDoProvedorRetorna429() throws Exception {
        when(assistenteService.perguntar(anyString())).thenThrow(new ProvedorIaException(
                HttpStatus.TOO_MANY_REQUESTS, "Limite de requisições do provedor de IA atingido.", null));

        mockMvc.perform(post("/api/chat").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"mensagem\":\"Olá\"}"))
                .andExpect(status().isTooManyRequests());
    }
}
