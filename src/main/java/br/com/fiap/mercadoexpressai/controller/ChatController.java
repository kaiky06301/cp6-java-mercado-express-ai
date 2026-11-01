package br.com.fiap.mercadoexpressai.controller;

import br.com.fiap.mercadoexpressai.dto.ChatRequest;
import br.com.fiap.mercadoexpressai.dto.ChatResponse;
import br.com.fiap.mercadoexpressai.service.AssistenteService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final AssistenteService assistenteService;

    public ChatController(AssistenteService assistenteService) {
        this.assistenteService = assistenteService;
    }

    @PostMapping
    public ChatResponse perguntar(@Valid @RequestBody ChatRequest request) {
        return assistenteService.perguntar(request.mensagem());
    }
}
