package br.com.fiap.mercadoexpressai.controller;

import br.com.fiap.mercadoexpressai.dto.ChatResponse;
import br.com.fiap.mercadoexpressai.service.HistoricoService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/historico")
public class HistoricoController {

    private final HistoricoService historicoService;

    public HistoricoController(HistoricoService historicoService) {
        this.historicoService = historicoService;
    }

    /**
     * Lista o histórico ordenado por data e hora.
     * ordem=desc (padrão) traz as mais recentes primeiro; ordem=asc, as mais antigas.
     */
    @GetMapping
    public List<ChatResponse> listar(@RequestParam(defaultValue = "desc") String ordem) {
        Sort.Direction direcao = "asc".equalsIgnoreCase(ordem) ? Sort.Direction.ASC : Sort.Direction.DESC;
        return historicoService.listar(direcao);
    }
}
