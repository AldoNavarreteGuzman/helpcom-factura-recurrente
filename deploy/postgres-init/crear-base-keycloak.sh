#!/bin/bash
# Crea, dentro de este mismo contenedor Postgres, la base de datos y el usuario separados
# para Keycloak (docs/despliegue.md). El postgres oficial ejecuta una sola vez, al inicializar
# el volumen de datos vacío, todo script que encuentre en /docker-entrypoint-initdb.d/ — por
# eso esto NO corre de nuevo en reinicios posteriores, solo la primera vez que se crea el
# volumen `postgres_datos`.
#
# POSTGRES_USER/POSTGRES_DB los define la propia imagen oficial (crean la base de la app);
# KEYCLOAK_DB_USER/KEYCLOAK_DB_PASSWORD/KEYCLOAK_DB los pasa este compose como variables de
# entorno adicionales del servicio postgres (ver docker-compose.yml y .env.example).
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER "${KEYCLOAK_DB_USER}" WITH PASSWORD '${KEYCLOAK_DB_PASSWORD}';
    CREATE DATABASE "${KEYCLOAK_DB}" OWNER "${KEYCLOAK_DB_USER}";
    GRANT ALL PRIVILEGES ON DATABASE "${KEYCLOAK_DB}" TO "${KEYCLOAK_DB_USER}";
EOSQL
