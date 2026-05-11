#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${AI_PLATFORM_ENV_FILE:-${DEPLOY_DIR}/.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  ENV_FILE="${DEPLOY_DIR}/.env.example"
fi

set -a
# shellcheck disable=SC1090
source "${ENV_FILE}"
set +a

docker compose --env-file "${ENV_FILE}" -f "${DEPLOY_DIR}/docker-compose.yml" ps
docker compose --env-file "${ENV_FILE}" -f "${DEPLOY_DIR}/docker-compose.yml" exec -T postgres \
  pg_isready -U "${AI_PLATFORM_POSTGRES_USER}" -d "${AI_PLATFORM_POSTGRES_DB}"
docker compose --env-file "${ENV_FILE}" -f "${DEPLOY_DIR}/docker-compose.yml" exec -T redis redis-cli ping
docker compose --env-file "${ENV_FILE}" -f "${DEPLOY_DIR}/docker-compose.yml" exec -T minio mc ready local
