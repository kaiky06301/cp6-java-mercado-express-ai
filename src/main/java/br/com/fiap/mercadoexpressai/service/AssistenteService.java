package br.com.fiap.mercadoexpressai.service;

import br.com.fiap.mercadoexpressai.dto.ChatResponse;
import br.com.fiap.mercadoexpressai.exception.ProvedorIaException;
import br.com.fiap.mercadoexpressai.model.Conversa;
import br.com.fiap.mercadoexpressai.repository.ConversaRepository;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.retry.NonTransientAiException;
import org.springframework.ai.retry.TransientAiException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.client.ResourceAccessException;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;

@Service
public class AssistenteService {

    static final String RECUSA = "Desculpe, só posso ajudar com assuntos do Mercado Express, como produtos, "
            + "receitas, entregas, pagamentos e trocas. Como posso ajudar na sua compra?";

    // Exemplos de conversa (few-shot): mostram ao modelo o tom e o formato esperados
    private static final List<Message> EXEMPLOS = List.of(
            new UserMessage("Comprei um leite que chegou vencido. O que faço?"),
            new AssistantMessage("Sinto muito pelo transtorno! Perecíveis com problema devem ser informados em até "
                    + "24 horas após a entrega. Abra o app, vá no menu \"Ajuda\" e registre a ocorrência com uma "
                    + "foto do produto que nossa equipe faz a troca ou o reembolso."),
            new UserMessage("Vou fazer um bolo de chocolate, o que preciso comprar?"),
            new AssistantMessage("Ótima escolha! Para um bolo de chocolate simples você vai precisar de:\n"
                    + "- Farinha de trigo\n- Açúcar\n- Ovos\n- Leite\n- Óleo\n- Chocolate em pó\n- Fermento em pó\n"
                    + "Para a cobertura, inclua leite condensado e manteiga. Você encontra tudo no app, na seção Mercearia."),
            new UserMessage("Quais frutas estão boas para comprar no inverno?"),
            new AssistantMessage("No inverno, aproveite as frutas da estação, que costumam estar mais saborosas:\n"
                    + "- Laranja e tangerina\n- Morango\n- Kiwi\n- Caqui\n- Maçã\n"
                    + "Você encontra todas na seção Hortifrúti do app."));

    // Exemplos para o classificador de domínio (few-shot)
    private static final List<Message> EXEMPLOS_CLASSIFICADOR = List.of(
            new UserMessage("Vocês vendem sabão em pó?"), new AssistantMessage("SIM"),
            new UserMessage("Qual a capital da Itália?"), new AssistantMessage("NAO"),
            new UserMessage("Monte uma lista de compras para o jantar de sexta."), new AssistantMessage("SIM"),
            new UserMessage("Como faço um código em Python?"), new AssistantMessage("NAO"),
            new UserMessage("Qual amaciante deixa a roupa mais cheirosa?"), new AssistantMessage("SIM"),
            new UserMessage("Escreva uma música sobre o amor."), new AssistantMessage("NAO"),
            new UserMessage("Olá, tudo bem?"), new AssistantMessage("SIM"));

    private final ChatClient chatClient;
    private final ConversaRepository conversaRepository;
    private final String instrucaoSistema;
    private final String instrucaoClassificador;
    private final String provedor;
    private final String modelo;

    public AssistenteService(ChatClient.Builder chatClientBuilder,
                             ConversaRepository conversaRepository,
                             @Value("classpath:prompts/sistema.st") Resource instrucaoSistema,
                             @Value("classpath:prompts/classificador.st") Resource instrucaoClassificador,
                             @Value("${assistente.provedor}") String provedor,
                             @Value("${spring.ai.ollama.chat.model}") String modelo) {
        this.chatClient = chatClientBuilder.build();
        this.conversaRepository = conversaRepository;
        this.instrucaoSistema = ler(instrucaoSistema);
        this.instrucaoClassificador = ler(instrucaoClassificador);
        this.provedor = provedor;
        this.modelo = modelo;
    }

    public ChatResponse perguntar(String mensagem) {
        String pergunta = mensagem.trim();
        String resposta = consultarModelo(pergunta);

        Conversa conversa = conversaRepository.save(new Conversa(pergunta, resposta, provedor, modelo));
        return ChatResponse.from(conversa);
    }

    private String consultarModelo(String pergunta) {
        String resposta;
        try {
            if (!estaNoDominio(pergunta)) {
                return RECUSA;
            }
            resposta = chatClient.prompt()
                    .system(instrucaoSistema)
                    .messages(EXEMPLOS)
                    .user(comLembrete(pergunta))
                    .call()
                    .content();
        } catch (ResourceAccessException e) {
            // conexão recusada ou tempo esgotado: o servidor do modelo não respondeu
            throw new ProvedorIaException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Não foi possível conectar ao " + provedor + ". Verifique se o serviço está em execução e tente novamente.", e);
        } catch (NonTransientAiException e) {
            throw traduzirErroDoProvedor(e);
        } catch (TransientAiException e) {
            throw new ProvedorIaException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O modelo de IA está indisponível no momento. Tente novamente em instantes.", e);
        } catch (RuntimeException e) {
            throw new ProvedorIaException(HttpStatus.BAD_GATEWAY,
                    "O " + provedor + " não conseguiu responder. Tente novamente.", e);
        }

        if (resposta == null || resposta.isBlank()) {
            throw new ProvedorIaException(HttpStatus.BAD_GATEWAY, "O modelo de IA retornou uma resposta vazia.", null);
        }
        return resposta.trim();
    }

    /**
     * Guardrail: uma chamada curta ao modelo classifica se a pergunta é do domínio (SIM/NAO).
     * Separar "classificar" de "responder" deixa cada tarefa simples o bastante para um modelo local pequeno.
     */
    private boolean estaNoDominio(String pergunta) {
        String veredito = chatClient.prompt()
                .system(instrucaoClassificador)
                .messages(EXEMPLOS_CLASSIFICADOR)
                .user(pergunta)
                .options(ChatOptions.builder().temperature(0.0).maxTokens(3))
                .call()
                .content();
        return veredito == null || !veredito.trim().toUpperCase(Locale.ROOT).startsWith("N");
    }

    /**
     * Modelos locais pequenos "esquecem" a instrução de sistema em conversas longas;
     * repetir a regra de domínio junto da pergunta mantém o assistente no escopo.
     * Só a pergunta original é gravada no histórico.
     */
    static String comLembrete(String pergunta) {
        return pergunta + "\n\n(Lembrete interno: responda em português do Brasil, em até 150 palavras. "
                + "Atenda normalmente, sem pedir desculpas, sem citar preços de produtos e usando apenas as "
                + "informações oficiais da loja.)";
    }

    /**
     * Erros "definitivos" vêm do provedor com o status HTTP no texto (ex.: "404 - model not found").
     * Com Ollama o caso comum é o modelo não instalado; os demais cobrem APIs externas
     * (chave inválida, sem saldo/cota, limite de requisições).
     */
    private ProvedorIaException traduzirErroDoProvedor(NonTransientAiException e) {
        String detalhe = e.getMessage() == null ? "" : e.getMessage().toLowerCase(Locale.ROOT);

        if (detalhe.contains("404") || detalhe.contains("not found")) {
            return new ProvedorIaException(HttpStatus.SERVICE_UNAVAILABLE,
                    "O modelo '" + modelo + "' não foi encontrado no " + provedor + ". Instale-o com: ollama pull " + modelo, e);
        }
        if (detalhe.contains("401") || detalhe.contains("403") || detalhe.contains("unauthorized")) {
            return new ProvedorIaException(HttpStatus.BAD_GATEWAY,
                    "Falha de autenticação no provedor de IA. Verifique a chave configurada na variável de ambiente.", e);
        }
        if (detalhe.contains("402") || detalhe.contains("insufficient") || detalhe.contains("quota")) {
            return new ProvedorIaException(HttpStatus.BAD_GATEWAY,
                    "A conta do provedor de IA está sem saldo ou cota disponível.", e);
        }
        if (detalhe.contains("429") || detalhe.contains("rate limit")) {
            return new ProvedorIaException(HttpStatus.TOO_MANY_REQUESTS,
                    "Limite de requisições do provedor de IA atingido. Aguarde e tente novamente.", e);
        }
        return new ProvedorIaException(HttpStatus.BAD_GATEWAY,
                "O " + provedor + " recusou a requisição. Tente reformular a pergunta.", e);
    }

    private static String ler(Resource resource) {
        try {
            return resource.getContentAsString(StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler a instrução de sistema", e);
        }
    }
}
