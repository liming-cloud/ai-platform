#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
DEPLOY_DIR="$(cd "${SCRIPT_DIR}/.." && pwd)"
ENV_FILE="${AI_PLATFORM_ENV_FILE:-${DEPLOY_DIR}/.env}"

if [[ ! -f "${ENV_FILE}" ]]; then
  ENV_FILE="${DEPLOY_DIR}/.env.example"
fi

docker compose --env-file "${ENV_FILE}" -f "${DEPLOY_DIR}/docker-compose.yml" down
