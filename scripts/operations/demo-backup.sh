#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=recovery-common.sh
source "${SCRIPT_DIRECTORY}/recovery-common.sh"

if [[ "$#" -ne 1 ]]; then
  echo "用法：demo-backup.sh <空的绝对备份目录>" >&2
  exit 2
fi

readonly BACKUP_DIRECTORY="$1"
readonly SOURCE_ENV_FILE="${FOUNDATION_SOURCE_ENV_FILE:-${RECOVERY_DEFAULT_ENV_FILE}}"
readonly BACKUP_STARTED_AT="$(date -u +%Y-%m-%dT%H:%M:%SZ)"
readonly BACKUP_STARTED_EPOCH="$(recovery_epoch_seconds)"
SOURCE_SERVICES_STOPPED=false

restart_source_services() {
  if [[ "${SOURCE_SERVICES_STOPPED}" == "true" ]]; then
    source_compose start \
      control-plane \
      query-runtime \
      ingestion-worker \
      console-web \
      query-web \
      >/dev/null 2>&1 || true
  fi
}

trap restart_source_services EXIT

require_recovery_command docker
require_recovery_command jq
require_recovery_command shasum
require_recovery_command curl
load_recovery_environment "${SOURCE_ENV_FILE}"
require_empty_backup_directory "${BACKUP_DIRECTORY}"

source_compose ps --status running --services |
  sort >"${BACKUP_DIRECTORY}/source-running-services.txt"
if [[ "$(wc -l <"${BACKUP_DIRECTORY}/source-running-services.txt" | tr -d ' ')" -ne 8 ]]; then
  recovery_fail "源环境未运行全部八个服务"
  exit 1
fi

# 停止所有写入口，保证数据库归档和对象副本属于同一个静止时间窗。
source_compose stop \
  console-web \
  query-web \
  query-runtime \
  ingestion-worker \
  control-plane \
  >/dev/null
SOURCE_SERVICES_STOPPED=true

source_compose exec -T postgres \
  pg_dump \
  --username "${FOUNDATION_POSTGRES_USER}" \
  --dbname "${FOUNDATION_POSTGRES_DATABASE}" \
  --format custom \
  --compress 9 \
  --no-owner \
  --no-privileges \
  >"${BACKUP_DIRECTORY}/database.dump"

source_compose exec -T postgres \
  pg_restore \
  --list \
  <"${BACKUP_DIRECTORY}/database.dump" \
  >"${BACKUP_DIRECTORY}/database.contents"

source_compose exec -T postgres \
  psql \
  --username "${FOUNDATION_POSTGRES_USER}" \
  --dbname "${FOUNDATION_POSTGRES_DATABASE}" \
  --no-psqlrc \
  --set ON_ERROR_STOP=1 \
  --command "${RECOVERY_OBJECT_MANIFEST_SQL}" \
  >"${BACKUP_DIRECTORY}/objects.tsv"

source_compose exec -T postgres \
  psql \
  --username "${FOUNDATION_POSTGRES_USER}" \
  --dbname "${FOUNDATION_POSTGRES_DATABASE}" \
  --no-psqlrc \
  --set ON_ERROR_STOP=1 \
  --command "${RECOVERY_FACT_COUNT_SQL}" \
  >"${BACKUP_DIRECTORY}/facts.tsv"

mkdir -p "${BACKUP_DIRECTORY}/objects"

docker run \
  --rm \
  --network "${RECOVERY_SOURCE_NETWORK_NAME}" \
  --volume "${BACKUP_DIRECTORY}:/backup" \
  --env FOUNDATION_MINIO_USER \
  --env FOUNDATION_MINIO_PASSWORD \
  --env RECOVERY_BUCKET="${RECOVERY_BUCKET}" \
  --entrypoint /bin/sh \
  "${RECOVERY_MINIO_IMAGE}" \
  -eu -c '
    mc alias set source http://minio:9000 \
      "${FOUNDATION_MINIO_USER}" \
      "${FOUNDATION_MINIO_PASSWORD}" >/dev/null
    tab="$(printf "\t")"
    while IFS="${tab}" read -r object_key expected_sha expected_size; do
      case "${object_key}" in
        foundation-v1/tenants/*/content.txt) ;;
        *) echo "对象键不符合受控目录：${object_key}" >&2; exit 1 ;;
      esac
      case "${object_key}" in
        *".."*) echo "对象键包含非法上级目录：${object_key}" >&2; exit 1 ;;
      esac
      target="/backup/objects/${object_key}"
      mkdir -p "$(dirname "${target}")"
      mc cp --quiet "source/${RECOVERY_BUCKET}/${object_key}" "${target}"
      actual_sha="$(sha256sum "${target}")"
      actual_sha="${actual_sha%% *}"
      actual_size="$(wc -c <"${target}" | tr -d " ")"
      if [ "${actual_sha}" != "${expected_sha}" ]; then
        echo "对象摘要不一致：${object_key}" >&2
        exit 1
      fi
      if [ "${actual_size}" != "${expected_size}" ]; then
        echo "对象大小不一致：${object_key}" >&2
        exit 1
      fi
    done </backup/objects.tsv
    mc find "source/${RECOVERY_BUCKET}" --print "{}" |
      wc -l |
      tr -d " " >/backup/source-object-count.txt
  '

readonly DATABASE_SHA256="$(recovery_sha256 "${BACKUP_DIRECTORY}/database.dump")"
readonly FACTS_SHA256="$(recovery_sha256 "${BACKUP_DIRECTORY}/facts.tsv")"
readonly OBJECT_MANIFEST_SHA256="$(recovery_sha256 "${BACKUP_DIRECTORY}/objects.tsv")"
readonly REFERENCED_OBJECT_COUNT="$(
  awk 'NF > 0 {count++} END {print count + 0}' "${BACKUP_DIRECTORY}/objects.tsv"
)"
readonly SOURCE_OBJECT_COUNT="$(tr -d '[:space:]' <"${BACKUP_DIRECTORY}/source-object-count.txt")"

docker image inspect \
  trusted-rag/control-plane:foundation-v1 \
  trusted-rag/query-runtime:foundation-v1 \
  trusted-rag/ingestion-worker:foundation-v1 \
  trusted-rag/console-web:foundation-v1 \
  trusted-rag/query-web:foundation-v1 \
  --format '{{index .RepoTags 0}}	{{.Id}}' \
  >"${BACKUP_DIRECTORY}/images.tsv"

readonly BACKUP_COMPLETED_EPOCH="$(recovery_epoch_seconds)"
readonly BACKUP_DURATION_SECONDS="$((BACKUP_COMPLETED_EPOCH - BACKUP_STARTED_EPOCH))"
readonly GIT_COMMIT="$(
  git -C "${RECOVERY_REPOSITORY_ROOT}" rev-parse HEAD
)"

jq -n \
  --arg schemaVersion "1" \
  --arg createdAt "${BACKUP_STARTED_AT}" \
  --arg gitCommit "${GIT_COMMIT}" \
  --arg databaseSha256 "${DATABASE_SHA256}" \
  --arg factsSha256 "${FACTS_SHA256}" \
  --arg objectManifestSha256 "${OBJECT_MANIFEST_SHA256}" \
  --argjson referencedObjectCount "${REFERENCED_OBJECT_COUNT}" \
  --argjson sourceObjectCount "${SOURCE_OBJECT_COUNT}" \
  --argjson backupDurationSeconds "${BACKUP_DURATION_SECONDS}" \
  '{
    schemaVersion: ($schemaVersion | tonumber),
    createdAt: $createdAt,
    gitCommit: $gitCommit,
    databaseSha256: $databaseSha256,
    factsSha256: $factsSha256,
    objectManifestSha256: $objectManifestSha256,
    referencedObjectCount: $referencedObjectCount,
    sourceObjectCount: $sourceObjectCount,
    backupDurationSeconds: $backupDurationSeconds,
    redisIncluded: false,
    secretsIncludedInEvidence: false
  }' >"${BACKUP_DIRECTORY}/private-manifest.json"

find "${BACKUP_DIRECTORY}" -type d -exec chmod 700 {} +
find "${BACKUP_DIRECTORY}" -type f -exec chmod 600 {} +

source_compose start \
  control-plane \
  query-runtime \
  ingestion-worker \
  console-web \
  query-web \
  >/dev/null
SOURCE_SERVICES_STOPPED=false
trap - EXIT

wait_for_http_endpoint "http://127.0.0.1:18080/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18081/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18082/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:13000/healthz"
wait_for_http_endpoint "http://127.0.0.1:13001/healthz"

echo "演示备份完成：数据库、${REFERENCED_OBJECT_COUNT} 个受引用对象和脱敏事实清单已生成"
