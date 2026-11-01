-- CP6 Java Advanced - Mercado Express AI
-- Consultas úteis para conferir os registros no banco (usadas no vídeo).

-- Histórico completo, mais recentes primeiro (textos resumidos para caber na tela)
SELECT id,
       TO_CHAR(data_hora, 'DD/MM/YYYY HH24:MI:SS') AS data_hora,
       provedor,
       modelo,
       LEFT(pergunta, 40) AS pergunta,
       LEFT(REPLACE(resposta, E'\n', ' '), 40) AS resposta
FROM historico_chat
ORDER BY data_hora DESC;

-- Quantidade de perguntas por modelo
SELECT provedor, modelo, COUNT(*) AS total
FROM historico_chat
GROUP BY provedor, modelo;
