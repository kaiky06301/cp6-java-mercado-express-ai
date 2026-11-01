-- CP6 Java Advanced - Mercado Express AI
-- Consultas úteis para conferir os registros no banco (usadas no vídeo).

-- Histórico completo, mais recentes primeiro
SELECT id, data_hora, provedor, modelo, pergunta, LEFT(resposta, 80) AS resposta
FROM historico_chat
ORDER BY data_hora DESC;

-- Quantidade de perguntas por modelo
SELECT provedor, modelo, COUNT(*) AS total
FROM historico_chat
GROUP BY provedor, modelo;
