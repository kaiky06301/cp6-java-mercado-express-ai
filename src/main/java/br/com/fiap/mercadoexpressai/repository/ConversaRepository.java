package br.com.fiap.mercadoexpressai.repository;

import br.com.fiap.mercadoexpressai.model.Conversa;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConversaRepository extends JpaRepository<Conversa, Long> {
}
