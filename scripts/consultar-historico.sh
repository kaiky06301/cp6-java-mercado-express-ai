#!/bin/sh
# Mostra os registros do histórico no PostgreSQL do docker-compose (container postgres-cp6-ai)
docker exec -i postgres-cp6-ai psql -U mercado -d mercado_express_ai < "$(dirname "$0")/03-consultas.sql"
