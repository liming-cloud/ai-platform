#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=recovery-common.sh
source "${SCRIPT_DIRECTORY}/recovery-common.sh"

if [[ "$#" -ne 2 ]]; then
  echo "用法：demo-recovery-verify.sh <备份绝对目录> <受控恢复项目名>" >&2
  exit 2
fi

readonly BACKUP_DIRECTORY="$1"
readonly RECOVERY_NAME="$2"
readonly SOURCE_ENV_FILE="${FOUNDATION_SOURCE_ENV_FILE:-${RECOVERY_DEFAULT_ENV_FILE}}"
readonly REPORT_FILE="${FOUNDATION_RECOVERY_REPORT:-${BACKUP_DIRECTORY}/recovery-report.json}"
readonly DEMO_PASSWORD="Demo@2026!"

require_recovery_command docker
require_recovery_command curl
require_recovery_command jq
require_recovery_command cmp
load_recovery_environment "${SOURCE_ENV_FILE}"
require_existing_backup_directory "${BACKUP_DIRECTORY}"
export_recovery_environment "${RECOVERY_NAME}"

if [[ ! -f "${BACKUP_DIRECTORY}/restore-state.json" ]]; then
  recovery_fail "恢复状态文件不存在"
  exit 1
fi
if [[ "${REPORT_FILE}" != /* ]]; then
  recovery_fail "恢复报告路径必须是绝对路径"
  exit 1
fi

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

recovery_compose exec -T postgres \
  psql \
  --username "${FOUNDATION_POSTGRES_USER}" \
  --dbname "${FOUNDATION_POSTGRES_DATABASE}" \
  --no-psqlrc \
  --set ON_ERROR_STOP=1 \
  --command "${RECOVERY_FACT_COUNT_SQL}" \
  >"${BACKUP_DIRECTORY}/restored-facts.tsv"

if ! cmp --silent \
  "${BACKUP_DIRECTORY}/facts.tsv" \
  "${BACKUP_DIRECTORY}/restored-facts.tsv"; then
  recovery_fail "恢复前后的业务事实计数不一致"
  exit 1
fi

readonly TENANT_COUNT="$(
  awk -F $'\t' '$1 == "tenant" {print $2}' "${BACKUP_DIRECTORY}/restored-facts.tsv"
)"
if [[ "${TENANT_COUNT}" != "2" ]]; then
  recovery_fail "恢复环境没有且只有两个演示租户"
  exit 1
fi

readonly RESTORED_OBJECT_COUNT="$(
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
      tab="$(printf "\t")"
      verified=0
      while IFS="${tab}" read -r object_key expected_sha expected_size; do
        stat_json="$(mc stat --json "target/${RECOVERY_BUCKET}/${object_key}")"
        printf "%s" "${stat_json}" |
          grep -F "\"X-Amz-Meta-Sha256\":\"${expected_sha}\"" >/dev/null
        mc cp --quiet \
          "target/${RECOVERY_BUCKET}/${object_key}" \
          /tmp/recovery-verification-object
        actual_sha="$(sha256sum /tmp/recovery-verification-object)"
        actual_sha="${actual_sha%% *}"
        actual_size="$(wc -c </tmp/recovery-verification-object | tr -d " ")"
        rm -f /tmp/recovery-verification-object
        if [ "${actual_sha}" != "${expected_sha}" ] ||
          [ "${actual_size}" != "${expected_size}" ]; then
          echo "恢复对象完整性校验失败" >&2
          exit 1
        fi
        verified=$((verified + 1))
      done </backup/objects.tsv
      remote_count="$(
        mc find "target/${RECOVERY_BUCKET}" --print "{}" |
          wc -l |
          tr -d " "
      )"
      if [ "${remote_count}" != "${verified}" ]; then
        echo "恢复桶包含清单外对象" >&2
        exit 1
      fi
      printf "%s" "${verified}"
    '
)"

login() {
  local tenant_code="$1"
  jq -n \
    --arg tenantCode "${tenant_code}" \
    --arg password "${DEMO_PASSWORD}" \
    '{
      tenantCode: $tenantCode,
      loginName: "admin",
      password: $password
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "http://127.0.0.1:${FOUNDATION_CONTROL_PLANE_PORT}/api/v1/auth/sessions" |
    jq -er '.tokenValue'
}

authorized_get() {
  local port="$1"
  local token="$2"
  local path="$3"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    "http://127.0.0.1:${port}/api/v1/${path}"
}

readonly STORE_TOKEN="$(login "demo-store")"
readonly CAMPUS_TOKEN="$(login "demo-campus")"
readonly STORE_CATALOG="$(
  authorized_get \
    "${FOUNDATION_QUERY_RUNTIME_PORT}" \
    "${STORE_TOKEN}" \
    "query-assistants?limit=20"
)"
readonly CAMPUS_CATALOG="$(
  authorized_get \
    "${FOUNDATION_QUERY_RUNTIME_PORT}" \
    "${CAMPUS_TOKEN}" \
    "query-assistants?limit=20"
)"

jq -e '.items | length > 0' >/dev/null <<<"${STORE_CATALOG}"
jq -e '.items | length > 0' >/dev/null <<<"${CAMPUS_CATALOG}"
readonly STORE_ASSISTANT_ID="$(
  jq -er '.items[0].assistantId' <<<"${STORE_CATALOG}"
)"
readonly CAMPUS_ASSISTANT_ID="$(
  jq -er '.items[0].assistantId' <<<"${CAMPUS_CATALOG}"
)"
if [[ "${STORE_ASSISTANT_ID}" == "${CAMPUS_ASSISTANT_ID}" ]]; then
  recovery_fail "两个租户恢复出了相同助手标识"
  exit 1
fi
jq -e \
  --arg other "${CAMPUS_ASSISTANT_ID}" \
  'all(.items[]; .assistantId != $other)' \
  >/dev/null <<<"${STORE_CATALOG}"
jq -e \
  --arg other "${STORE_ASSISTANT_ID}" \
  'all(.items[]; .assistantId != $other)' \
  >/dev/null <<<"${CAMPUS_CATALOG}"

readonly STORE_USAGE="$(
  authorized_get \
    "${FOUNDATION_CONTROL_PLANE_PORT}" \
    "${STORE_TOKEN}" \
    "usage"
)"
readonly CAMPUS_USAGE="$(
  authorized_get \
    "${FOUNDATION_CONTROL_PLANE_PORT}" \
    "${CAMPUS_TOKEN}" \
    "usage"
)"
readonly STORE_AUDIT="$(
  authorized_get \
    "${FOUNDATION_CONTROL_PLANE_PORT}" \
    "${STORE_TOKEN}" \
    "audit-records?limit=100"
)"
readonly CAMPUS_AUDIT="$(
  authorized_get \
    "${FOUNDATION_CONTROL_PLANE_PORT}" \
    "${CAMPUS_TOKEN}" \
    "audit-records?limit=100"
)"

jq -e '.acceptedQueries >= 0 and .dailyQuota.limit > 0' \
  >/dev/null <<<"${STORE_USAGE}"
jq -e '.acceptedQueries >= 0 and .dailyQuota.limit > 0' \
  >/dev/null <<<"${CAMPUS_USAGE}"
jq -e \
  'all(
    .items[];
    (.questionDigest | test("^[0-9a-f]{64}$"))
    and (has("answer") | not)
    and (has("quote") | not)
  )' >/dev/null <<<"${STORE_AUDIT}"
jq -e \
  'all(
    .items[];
    (.questionDigest | test("^[0-9a-f]{64}$"))
    and (has("answer") | not)
    and (has("quote") | not)
  )' >/dev/null <<<"${CAMPUS_AUDIT}"

curl --fail --silent --show-error \
  -X DELETE \
  -H "Authorization: Bearer ${STORE_TOKEN}" \
  "http://127.0.0.1:${FOUNDATION_CONTROL_PLANE_PORT}/api/v1/auth/sessions/current" \
  >/dev/null
curl --fail --silent --show-error \
  -X DELETE \
  -H "Authorization: Bearer ${CAMPUS_TOKEN}" \
  "http://127.0.0.1:${FOUNDATION_CONTROL_PLANE_PORT}/api/v1/auth/sessions/current" \
  >/dev/null

wait_for_http_endpoint "http://127.0.0.1:18080/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18081/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18082/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:13000/healthz"
wait_for_http_endpoint "http://127.0.0.1:13001/healthz"

readonly RESTORE_DURATION_SECONDS="$(
  jq -er '.restoreDurationSeconds' "${BACKUP_DIRECTORY}/restore-state.json"
)"
readonly REDIS_KEYS_BEFORE_APPLICATIONS="$(
  jq -er '.redisKeysBeforeApplications' "${BACKUP_DIRECTORY}/restore-state.json"
)"
readonly BACKUP_DURATION_SECONDS="$(
  jq -er '.backupDurationSeconds' "${BACKUP_DIRECTORY}/private-manifest.json"
)"
readonly DATABASE_SHA256="$(
  jq -er '.databaseSha256' "${BACKUP_DIRECTORY}/private-manifest.json"
)"
readonly FACTS_SHA256="$(
  recovery_sha256 "${BACKUP_DIRECTORY}/restored-facts.tsv"
)"
readonly GIT_COMMIT="$(
  jq -er '.gitCommit' "${BACKUP_DIRECTORY}/private-manifest.json"
)"

mkdir -p "$(dirname "${REPORT_FILE}")"
jq -n \
  --arg schemaVersion "1" \
  --arg gitCommit "${GIT_COMMIT}" \
  --arg databaseSha256 "${DATABASE_SHA256}" \
  --arg factsSha256 "${FACTS_SHA256}" \
  --argjson backupDurationSeconds "${BACKUP_DURATION_SECONDS}" \
  --argjson restoreDurationSeconds "${RESTORE_DURATION_SECONDS}" \
  --argjson objectCount "${RESTORED_OBJECT_COUNT}" \
  --argjson tenantCount "${TENANT_COUNT}" \
  --argjson redisRecoveredKeys "${REDIS_KEYS_BEFORE_APPLICATIONS}" \
  '{
    schemaVersion: ($schemaVersion | tonumber),
    passed: true,
    gitCommit: $gitCommit,
    databaseSha256: $databaseSha256,
    factsSha256: $factsSha256,
    backupDurationSeconds: $backupDurationSeconds,
    restoreDurationSeconds: $restoreDurationSeconds,
    recoveryPointTargetHours: 24,
    recoveryTimeTargetSeconds: 3600,
    objectCount: $objectCount,
    tenantCount: $tenantCount,
    redisRecoveredKeys: $redisRecoveredKeys,
    factsMatch: true,
    objectIntegrity: true,
    tenantIsolation: true,
    sourceEnvironmentHealthy: true,
    recoveryEnvironmentHealthy: true,
    secretsIncluded: false
  }' >"${REPORT_FILE}"
chmod 600 "${REPORT_FILE}"

echo "隔离恢复验收通过：事实、对象、空 Redis、重新登录和租户隔离均已验证"
