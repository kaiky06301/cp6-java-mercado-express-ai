package br.com.fiap.mercadoexpressai.service;

import br.com.fiap.mercadoexpressai.dto.ChatResponse;
import br.com.fiap.mercadoexpressai.repository.ConversaRepository;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class HistoricoService {

    private final ConversaRepository conversaRepository;

    public HistoricoService(ConversaRepository conversaRepository) {
        this.conversaRepository = conversaRepository;
    }

    public List<ChatResponse> listar(Sort.Direction direcao) {
        return conversaRepository.findAll(Sort.by(direcao, "dataHora").and(Sort.by(direcao, "id")))
                .stream()
                .map(ChatResponse::from)
                .toList();
    }
}
