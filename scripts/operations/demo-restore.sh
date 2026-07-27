#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=recovery-common.sh
source "${SCRIPT_DIRECTORY}/recovery-common.sh"

if [[ "$#" -ne 2 ]]; then
  echo "用法：demo-restore.sh <备份绝对目录> <受控恢复项目名>" >&2
  exit 2
fi

readonly BACKUP_DIRECTORY="$1"
readonly RECOVERY_NAME="$2"
readonly SOURCE_ENV_FILE="${FOUNDATION_SOURCE_ENV_FILE:-${RECOVERY_DEFAULT_ENV_FILE}}"
readonly RESTORE_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
readonly RESTORE_STARTED_EPOCH="$(recovery_epoch_seconds)"

require_recovery_command docker
require_recovery_command curl
require_recovery_command jq
require_recovery_command shasum
load_recovery_environment "${SOURCE_ENV_FILE}"
require_existing_backup_directory "${BACKUP_DIRECTORY}"
export_recovery_environment "${RECOVERY_NAME}"
require_absent_recovery_resources

readonly EXPECTED_DATABASE_SHA256="$(
  jq -er '.databaseSha256' "${BACKUP_DIRECTORY}/private-manifest.json"
)"
if [[ "$(recovery_sha256 "${BACKUP_DIRECTORY}/database.dump")" != "${EXPECTED_DATABASE_SHA256}" ]]; then
  recovery_fail "数据库归档摘要与备份清单不一致"
  exit 1
fi

recovery_compose up --detach postgres redis minio >/dev/null

for _ in {1..60}; do
  if recovery_compose exec -T postgres \
    pg_isready \
    --username "${FOUNDATION_POSTGRES_USER}" \
    --dbname "${FOUNDATION_POSTGRES_DATABASE}" \
    >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

recovery_compose exec -T postgres \
  pg_restore \
  --username "${FOUNDATION_POSTGRES_USER}" \
  --dbname "${FOUNDATION_POSTGRES_DATABASE}" \
  --clean \
  --if-exists \
  --no-owner \
  --no-privileges \
  --exit-on-error \
  <"${BACKUP_DIRECTORY}/database.dump"

docker run \
  --rm \
  --network "${FOUNDATION_NETWORK_NAME}" \
  --volume "${BACKUP_DIRECTORY}:/backup:ro" \
  --env FOUNDATION_MINIO_USER \
  --env FOUNDATION_MINIO_PASSWORD \
  --env RECOVERY_BUCKET="${RECOVERY_BUCKET}" \
  --entrypoint /bin/sh \
  "${RECOVERY_MINIO_IMAGE}" \
  -eu -c '
    mc alias set target http://minio:9000 \
      "${FOUNDATION_MINIO_USER}" \
      "${FOUNDATION_MINIO_PASSWORD}" >/dev/null
    mc mb --ignore-existing "target/${RECOVERY_BUCKET}" >/dev/null
    tab="$(printf "\t")"
    while IFS="${tab}" read -r object_key expected_sha expected_size; do
      source_file="/backup/objects/${object_key}"
      if [ ! -f "${source_file}" ]; then
        echo "备份对象文件不存在：${object_key}" >&2
        exit 1
      fi
      actual_sha="$(sha256sum "${source_file}")"
      actual_sha="${actual_sha%% *}"
      actual_size="$(wc -c <"${source_file}" | tr -d " ")"
      if [ "${actual_sha}" != "${expected_sha}" ] ||
        [ "${actual_size}" != "${expected_size}" ]; then
        echo "备份对象在恢复前校验失败：${object_key}" >&2
        exit 1
      fi
      mc cp \
        --quiet \
        --attr "sha256=${expected_sha}" \
        "${source_file}" \
        "target/${RECOVERY_BUCKET}/${object_key}"
    done </backup/objects.tsv
  '

readonly REDIS_KEYS_BEFORE_APPLICATIONS="$(
  recovery_compose exec -T redis \
    redis-cli \
    --no-auth-warning \
    -a "${FOUNDATION_REDIS_PASSWORD}" \
    DBSIZE |
    tr -d '[:space:]'
)"
if [[ "${REDIS_KEYS_BEFORE_APPLICATIONS}" != "0" ]]; then
  recovery_fail "恢复环境 Redis 在应用启动前不是空状态"
  exit 1
fi

recovery_compose up --detach >/dev/null

wait_for_http_endpoint \
  "http://127.0.0.1:${FOUNDATION_CONTROL_PLANE_PORT}/actuator/health/readiness"
wait_for_http_endpoint \
  "http://127.0.0.1:${FOUNDATION_QUERY_RUNTIME_PORT}/actuator/health/readiness"
wait_for_http_endpoint \
  "http://127.0.0.1:${FOUNDATION_INGESTION_WORKER_PORT}/actuator/health/readiness"
wait_for_http_endpoint \
  "http://127.0.0.1:${FOUNDATION_CONSOLE_WEB_PORT}/healthz"
wait_for_http_endpoint \
  "http://127.0.0.1:${FOUNDATION_QUERY_WEB_PORT}/healthz"

readonly RESTORE_COMPLETED_EPOCH="$(recovery_epoch_seconds)"
readonly RESTORE_DURATION_SECONDS="$((RESTORE_COMPLETED_EPOCH - RESTORE_STARTED_EPOCH))"

jq -n \
  --arg recoveryName "${RECOVERY_NAME}" \
  --arg restoreStartedAt "${RESTORE_STARTED_AT}" \
  --argjson restoreStartedEpoch "${RESTORE_STARTED_EPOCH}" \
  --argjson restoreDurationSeconds "${RESTORE_DURATION_SECONDS}" \
  --argjson redisKeysBeforeApplications "${REDIS_KEYS_BEFORE_APPLICATIONS}" \
  --arg controlPlanePort "${FOUNDATION_CONTROL_PLANE_PORT}" \
  --arg queryRuntimePort "${FOUNDATION_QUERY_RUNTIME_PORT}" \
  --arg ingestionWorkerPort "${FOUNDATION_INGESTION_WORKER_PORT}" \
  --arg consoleWebPort "${FOUNDATION_CONSOLE_WEB_PORT}" \
  --arg queryWebPort "${FOUNDATION_QUERY_WEB_PORT}" \
  '{
    recoveryName: $recoveryName,
    restoreStartedAt: $restoreStartedAt,
    restoreStartedEpoch: $restoreStartedEpoch,
    restoreDurationSeconds: $restoreDurationSeconds,
    redisKeysBeforeApplications: $redisKeysBeforeApplications,
    ports: {
      controlPlane: ($controlPlanePort | tonumber),
      queryRuntime: ($queryRuntimePort | tonumber),
      ingestionWorker: ($ingestionWorkerPort | tonumber),
      consoleWeb: ($consoleWebPort | tonumber),
      queryWeb: ($queryWebPort | tonumber)
    }
  }' >"${BACKUP_DIRECTORY}/restore-state.json"
chmod 600 "${BACKUP_DIRECTORY}/restore-state.json"

echo "隔离恢复环境已启动：${RECOVERY_NAME}，恢复用时 ${RESTORE_DURATION_SECONDS} 秒"
