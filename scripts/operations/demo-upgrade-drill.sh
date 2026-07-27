#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=recovery-common.sh
source "${SCRIPT_DIRECTORY}/recovery-common.sh"

if [[ "$#" -gt 1 ]]; then
  echo "用法：demo-upgrade-drill.sh [脱敏报告绝对路径]" >&2
  exit 2
fi

readonly REPORT_FILE="${1:-${RECOVERY_REPOSITORY_ROOT}/project/evidence/artifacts/WP-0007-upgrade-drill.json}"
readonly BACKUP_DIRECTORY="$(
  mktemp -d "${RECOVERY_BACKUP_PREFIX}upgrade.XXXXXX"
)"
readonly POST_UPGRADE_FACTS="${BACKUP_DIRECTORY}/post-upgrade-facts.tsv"
readonly EVALUATION_REPORT="${BACKUP_DIRECTORY}/evaluation.json"
readonly IMAGES_REPORT="${BACKUP_DIRECTORY}/images.json"
readonly UPGRADE_STARTED_EPOCH="$(recovery_epoch_seconds)"
UPGRADE_SUCCEEDED=false
ENVIRONMENT_LOADED=false
ROLLBACK_BACKUP_READY=false

cleanup() {
  local exit_code=$?
  if [[ "${ENVIRONMENT_LOADED}" == "true" ]]; then
    source_compose start \
      control-plane \
      query-runtime \
      ingestion-worker \
      console-web \
      query-web \
      >/dev/null 2>&1 || true
  fi
  if [[ "${UPGRADE_SUCCEEDED}" == "true" ]] ||
    [[ "${ROLLBACK_BACKUP_READY}" != "true" ]]; then
    if [[ -d "${BACKUP_DIRECTORY}" ]] &&
      [[ "${BACKUP_DIRECTORY}" == "${RECOVERY_BACKUP_PREFIX}"upgrade.* ]]; then
      rm -rf -- "${BACKUP_DIRECTORY}"
    fi
  else
    echo "升级失败，机密回滚备份已保留：${BACKUP_DIRECTORY}" >&2
  fi
  exit "${exit_code}"
}

trap cleanup EXIT

if [[ "${REPORT_FILE}" != /* ]]; then
  recovery_fail "升级报告路径必须是绝对路径"
  exit 1
fi

require_recovery_command docker
require_recovery_command curl
require_recovery_command jq
require_recovery_command shasum
load_recovery_environment "${RECOVERY_DEFAULT_ENV_FILE}"
ENVIRONMENT_LOADED=true

if [[ "$(source_compose ps --status running --services | wc -l | tr -d ' ')" -ne 8 ]]; then
  recovery_fail "升级前源环境未运行全部八个服务"
  exit 1
fi

"${SCRIPT_DIRECTORY}/demo-backup.sh" "${BACKUP_DIRECTORY}"
ROLLBACK_BACKUP_READY=true

source_compose up --detach --build

wait_for_http_endpoint "http://127.0.0.1:18080/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18081/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18082/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:13000/healthz"
wait_for_http_endpoint "http://127.0.0.1:13001/healthz"

source_compose exec -T postgres \
  psql \
  --username "${FOUNDATION_POSTGRES_USER}" \
  --dbname "${FOUNDATION_POSTGRES_DATABASE}" \
  --no-psqlrc \
  --set ON_ERROR_STOP=1 \
  --command "${RECOVERY_FACT_COUNT_SQL}" \
  >"${POST_UPGRADE_FACTS}"

if ! cmp -s "${BACKUP_DIRECTORY}/facts.tsv" "${POST_UPGRADE_FACTS}"; then
  recovery_fail "升级前后关键业务事实不一致"
  exit 1
fi

EVALUATION_REPORT_PATH="${EVALUATION_REPORT}" \
  make -C "${RECOVERY_REPOSITORY_ROOT}" evaluation-demo

source_compose images --format json |
  jq '
    (if type == "array" then . else [.] end)
    | map({
        repository: .Repository,
        tag: .Tag,
        contentId: .ID,
        platform: .Platform
      })
    | sort_by(.repository)
  ' >"${IMAGES_REPORT}"

readonly RUNNING_SERVICE_COUNT="$(
  source_compose ps --status running --services |
    wc -l |
    tr -d ' '
)"
if [[ "${RUNNING_SERVICE_COUNT}" != "8" ]]; then
  recovery_fail "升级后未运行全部八个服务"
  exit 1
fi

readonly FACTS_BEFORE_SHA256="$(
  recovery_sha256 "${BACKUP_DIRECTORY}/facts.tsv"
)"
readonly FACTS_AFTER_SHA256="$(recovery_sha256 "${POST_UPGRADE_FACTS}")"
readonly MIGRATION_COUNT_BEFORE="$(
  awk -F '\t' '$1 == "flyway_migration" {print $2}' \
    "${BACKUP_DIRECTORY}/facts.tsv"
)"
readonly MIGRATION_COUNT_AFTER="$(
  awk -F '\t' '$1 == "flyway_migration" {print $2}' \
    "${POST_UPGRADE_FACTS}"
)"
readonly UPGRADE_COMPLETED_EPOCH="$(recovery_epoch_seconds)"
readonly GIT_COMMIT="$(
  git -C "${RECOVERY_REPOSITORY_ROOT}" rev-parse HEAD
)"

mkdir -p "$(dirname "${REPORT_FILE}")"
jq -n \
  --slurpfile evaluation "${EVALUATION_REPORT}" \
  --slurpfile images "${IMAGES_REPORT}" \
  --arg gitCommit "${GIT_COMMIT}" \
  --arg databaseBackupSha256 "$(
    jq -er '.databaseSha256' \
      "${BACKUP_DIRECTORY}/private-manifest.json"
  )" \
  --arg factsBeforeSha256 "${FACTS_BEFORE_SHA256}" \
  --arg factsAfterSha256 "${FACTS_AFTER_SHA256}" \
  --argjson migrationCountBefore "${MIGRATION_COUNT_BEFORE}" \
  --argjson migrationCountAfter "${MIGRATION_COUNT_AFTER}" \
  --argjson serviceCount "${RUNNING_SERVICE_COUNT}" \
  --argjson durationSeconds "$((UPGRADE_COMPLETED_EPOCH - UPGRADE_STARTED_EPOCH))" \
  '{
    schemaVersion: 1,
    passed: (
      $evaluation[0].passed
      and $factsBeforeSha256 == $factsAfterSha256
      and $migrationCountBefore == $migrationCountAfter
      and $serviceCount == 8
    ),
    gitCommit: $gitCommit,
    upgradeType: "既有数据卷受控重部署",
    databaseBackupSha256: $databaseBackupSha256,
    factsBeforeSha256: $factsBeforeSha256,
    factsAfterSha256: $factsAfterSha256,
    factsMatch: ($factsBeforeSha256 == $factsAfterSha256),
    migrationCountBefore: $migrationCountBefore,
    migrationCountAfter: $migrationCountAfter,
    serviceCount: $serviceCount,
    applicationEntryCount: 5,
    evaluation: $evaluation[0],
    images: $images[0],
    durationSeconds: $durationSeconds,
    sourceEnvironmentHealthy: true,
    rollbackBackupCreated: true,
    rollbackBackupDeletedAfterSuccess: true,
    secretsIncluded: false
  }' >"${REPORT_FILE}"
chmod 644 "${REPORT_FILE}"

if ! jq -e '.passed == true' "${REPORT_FILE}" >/dev/null; then
  recovery_fail "升级报告未达到通过条件"
  exit 1
fi

UPGRADE_SUCCEEDED=true
trap - EXIT
rm -rf -- "${BACKUP_DIRECTORY}"

echo "既有数据卷受控升级演练通过，脱敏报告：${REPORT_FILE}"
