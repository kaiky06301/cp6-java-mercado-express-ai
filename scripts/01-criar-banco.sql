-- CP6 Java Advanced - Mercado Express AI
-- Cria o usuário e o banco no PostgreSQL local.
-- Execute conectado como superusuário (ex.: psql -U postgres -f scripts/01-criar-banco.sql)
-- Obs.: se usar o docker-compose.yml do projeto, este passo já é feito automaticamente.

CREATE USER mercado WITH PASSWORD 'mercado123';
CREATE DATABASE mercado_express_ai OWNER mercado;
GRANT ALL PRIVILEGES ON DATABASE mercado_express_ai TO mercado;
