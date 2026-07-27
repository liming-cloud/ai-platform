#!/usr/bin/env bash

readonly RECOVERY_REPOSITORY_ROOT="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." >/dev/null 2>&1
  pwd
)"
readonly RECOVERY_COMPOSE_FILE="${RECOVERY_REPOSITORY_ROOT}/deploy/compose/compose.yml"
readonly RECOVERY_DEFAULT_ENV_FILE="${RECOVERY_REPOSITORY_ROOT}/deploy/compose/.env.example"
readonly RECOVERY_SOURCE_PROJECT_NAME="trusted-rag-foundation-v1"
readonly RECOVERY_SOURCE_NETWORK_NAME="trusted-rag-foundation-v1"
readonly RECOVERY_SOURCE_POSTGRES_VOLUME="trusted-rag-foundation-v1-postgres"
readonly RECOVERY_SOURCE_REDIS_VOLUME="trusted-rag-foundation-v1-redis"
readonly RECOVERY_SOURCE_MINIO_VOLUME="trusted-rag-foundation-v1-minio"
readonly RECOVERY_MINIO_IMAGE="minio/minio:RELEASE.2025-09-07T16-13-09Z@sha256:14cea493d9a34af32f524e538b8346cf79f3321eff8e708c1e2960462bd8936e"
readonly RECOVERY_BUCKET="foundation-v1-private"
readonly RECOVERY_NAME_PATTERN='^trusted-rag-foundation-v1-recovery-[a-z0-9][a-z0-9-]{2,47}$'
readonly RECOVERY_BACKUP_PREFIX="${TMPDIR:-/tmp}/trusted-rag-foundation-v1-backup."

readonly RECOVERY_OBJECT_MANIFEST_SQL="
COPY (
  SELECT object_key, sha256, byte_size
  FROM knowledge_data.text_data_source_content
  ORDER BY object_key
) TO STDOUT WITH (FORMAT CSV, DELIMITER E'\t', HEADER FALSE);
"

readonly RECOVERY_FACT_COUNT_SQL="
COPY (
  SELECT fact, count_value
  FROM (
    SELECT 10 AS sequence, 'flyway_migration' AS fact, count(*)::BIGINT AS count_value
      FROM public.flyway_schema_history WHERE success
    UNION ALL SELECT 20, 'tenant', count(*) FROM organization_access.tenant
    UNION ALL SELECT 30, 'membership', count(*) FROM organization_access.membership_credential
    UNION ALL SELECT 40, 'permission', count(*) FROM organization_access.membership_permission
    UNION ALL SELECT 50, 'knowledge_space', count(*) FROM knowledge_data.knowledge_space
    UNION ALL SELECT 60, 'text_data_source', count(*) FROM knowledge_data.text_data_source
    UNION ALL SELECT 70, 'text_content', count(*) FROM knowledge_data.text_data_source_content
    UNION ALL SELECT 80, 'index_version', count(*) FROM knowledge_data.index_version
    UNION ALL SELECT 90, 'index_chunk', count(*) FROM knowledge_data.index_chunk
    UNION ALL SELECT 100, 'provider_connection', count(*) FROM model_runtime.provider_connection
    UNION ALL SELECT 110, 'model_deployment', count(*) FROM model_runtime.model_deployment
    UNION ALL SELECT 120, 'assistant', count(*) FROM assistant_delivery.assistant
    UNION ALL SELECT 130, 'assistant_version', count(*) FROM assistant_delivery.assistant_version
    UNION ALL SELECT 140, 'query_run', count(*) FROM trusted_query.query_run
    UNION ALL SELECT 150, 'query_citation', count(*) FROM trusted_query.query_citation
    UNION ALL SELECT 160, 'query_event', count(*) FROM trusted_query.query_run_event
  ) AS facts
  ORDER BY sequence
) TO STDOUT WITH (FORMAT CSV, DELIMITER E'\t', HEADER FALSE);
"

recovery_fail() {
  echo "$1" >&2
  return 1
}

require_recovery_command() {
  command -v "$1" >/dev/null 2>&1 ||
    recovery_fail "缺少恢复所需命令：$1"
}

require_recovery_name() {
  local recovery_name="${1:-}"
  if [[ ! "${recovery_name}" =~ ${RECOVERY_NAME_PATTERN} ]]; then
    recovery_fail "恢复项目名不符合受控前缀和字符规则"
    return
  fi
  if [[ "${recovery_name}" == "${RECOVERY_SOURCE_PROJECT_NAME}" ]]; then
    recovery_fail "恢复项目名不能等于源项目名"
  fi
}

require_empty_backup_directory() {
  local backup_directory="${1:-}"
  if [[ "${backup_directory}" != /* ]] || [[ ! -d "${backup_directory}" ]]; then
    recovery_fail "备份目录必须是已经存在的绝对目录"
    return
  fi
  if [[ -n "$(find "${backup_directory}" -mindepth 1 -maxdepth 1 -print -quit)" ]]; then
    recovery_fail "备份目录必须为空"
    return
  fi
  chmod 700 "${backup_directory}"
}

require_existing_backup_directory() {
  local backup_directory="${1:-}"
  if [[ "${backup_directory}" != /* ]] || [[ ! -d "${backup_directory}" ]]; then
    recovery_fail "备份目录必须是已经存在的绝对目录"
    return
  fi
  local required_file
  for required_file in \
    database.dump \
    database.contents \
    objects.tsv \
    facts.tsv \
    private-manifest.json; do
    if [[ ! -f "${backup_directory}/${required_file}" ]]; then
      recovery_fail "备份目录缺少文件：${required_file}"
      return
    fi
  done
}

load_recovery_environment() {
  local environment_file="${1:-${RECOVERY_DEFAULT_ENV_FILE}}"
  if [[ "${environment_file}" != /* ]]; then
    environment_file="${RECOVERY_REPOSITORY_ROOT}/${environment_file}"
  fi
  if [[ ! -f "${environment_file}" ]]; then
    recovery_fail "恢复环境文件不存在"
    return
  fi
  set -a
  # 环境文件由部署负责人维护，只允许简单的变量赋值。
  # shellcheck disable=SC1090
  source "${environment_file}"
  set +a
  local variable
  for variable in \
    FOUNDATION_POSTGRES_DATABASE \
    FOUNDATION_POSTGRES_USER \
    FOUNDATION_POSTGRES_PASSWORD \
    FOUNDATION_REDIS_PASSWORD \
    FOUNDATION_MINIO_USER \
    FOUNDATION_MINIO_PASSWORD; do
    if [[ -z "${!variable:-}" ]]; then
      recovery_fail "环境文件缺少必需变量：${variable}"
      return
    fi
  done
  export RECOVERY_ENV_FILE="${environment_file}"
}

source_compose() {
  FOUNDATION_COMPOSE_PROJECT_NAME="${RECOVERY_SOURCE_PROJECT_NAME}" \
  FOUNDATION_NETWORK_NAME="${RECOVERY_SOURCE_NETWORK_NAME}" \
  FOUNDATION_POSTGRES_VOLUME_NAME="${RECOVERY_SOURCE_POSTGRES_VOLUME}" \
  FOUNDATION_REDIS_VOLUME_NAME="${RECOVERY_SOURCE_REDIS_VOLUME}" \
  FOUNDATION_MINIO_VOLUME_NAME="${RECOVERY_SOURCE_MINIO_VOLUME}" \
  FOUNDATION_CONTROL_PLANE_PORT="18080" \
  FOUNDATION_QUERY_RUNTIME_PORT="18081" \
  FOUNDATION_INGESTION_WORKER_PORT="18082" \
  FOUNDATION_CONSOLE_WEB_PORT="13000" \
  FOUNDATION_QUERY_WEB_PORT="13001" \
    docker compose \
      --env-file "${RECOVERY_ENV_FILE}" \
      -f "${RECOVERY_COMPOSE_FILE}" \
      "$@"
}

export_recovery_environment() {
  local recovery_name="$1"
  require_recovery_name "${recovery_name}"
  export FOUNDATION_COMPOSE_PROJECT_NAME="${recovery_name}"
  export FOUNDATION_NETWORK_NAME="${recovery_name}"
  export FOUNDATION_POSTGRES_VOLUME_NAME="${recovery_name}-postgres"
  export FOUNDATION_REDIS_VOLUME_NAME="${recovery_name}-redis"
  export FOUNDATION_MINIO_VOLUME_NAME="${recovery_name}-minio"
  export FOUNDATION_CONTROL_PLANE_PORT="${FOUNDATION_RECOVERY_CONTROL_PLANE_PORT:-28080}"
  export FOUNDATION_QUERY_RUNTIME_PORT="${FOUNDATION_RECOVERY_QUERY_RUNTIME_PORT:-28081}"
  export FOUNDATION_INGESTION_WORKER_PORT="${FOUNDATION_RECOVERY_INGESTION_WORKER_PORT:-28082}"
  export FOUNDATION_CONSOLE_WEB_PORT="${FOUNDATION_RECOVERY_CONSOLE_WEB_PORT:-23000}"
  export FOUNDATION_QUERY_WEB_PORT="${FOUNDATION_RECOVERY_QUERY_WEB_PORT:-23001}"
}

recovery_compose() {
  docker compose \
    --env-file "${RECOVERY_ENV_FILE}" \
    -f "${RECOVERY_COMPOSE_FILE}" \
    "$@"
}

require_absent_recovery_resources() {
  local resource
  for resource in \
    "${FOUNDATION_POSTGRES_VOLUME_NAME}" \
    "${FOUNDATION_REDIS_VOLUME_NAME}" \
    "${FOUNDATION_MINIO_VOLUME_NAME}"; do
    if docker volume inspect "${resource}" >/dev/null 2>&1; then
      recovery_fail "恢复目标卷已经存在：${resource}"
      return
    fi
  done
  if docker network inspect "${FOUNDATION_NETWORK_NAME}" >/dev/null 2>&1; then
    recovery_fail "恢复目标网络已经存在：${FOUNDATION_NETWORK_NAME}"
  fi
}

wait_for_http_endpoint() {
  local endpoint="$1"
  local attempts="${2:-60}"
  local attempt
  for ((attempt = 1; attempt <= attempts; attempt++)); do
    if curl --fail --silent "${endpoint}" >/dev/null 2>&1; then
      return 0
    fi
    sleep 1
  done
  recovery_fail "恢复入口健康检查超时：${endpoint}"
}

recovery_sha256() {
  shasum -a 256 "$1" | awk '{print $1}'
}

recovery_epoch_seconds() {
  date +%s
}
