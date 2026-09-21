#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=recovery-common.sh
source "${SCRIPT_DIRECTORY}/recovery-common.sh"

if [[ "$#" -gt 1 ]]; then
  echo "用法：demo-capacity-resilience-drill.sh [脱敏报告绝对路径]" >&2
  exit 2
fi

readonly REPORT_FILE="${1:-/private/tmp/ai-platform-operations/WP-0007-capacity-resilience.json}"
readonly BACKUP_DIRECTORY="$(
  mktemp -d "${RECOVERY_BACKUP_PREFIX}capacity.XXXXXX"
)"
readonly RECOVERY_NAME="trusted-rag-foundation-v1-recovery-capacity-$(
  date -u +%Y%m%d%H%M%S
)-$$"
readonly CAPACITY_REPORT="${BACKUP_DIRECTORY}/capacity.json"
readonly RESILIENCE_REPORT="${BACKUP_DIRECTORY}/resilience.json"
readonly POST_RESTART_REPORT="${BACKUP_DIRECTORY}/post-restart-query.json"
readonly IMAGES_REPORT="${BACKUP_DIRECTORY}/images.json"
readonly DATASET_REPORT="${BACKUP_DIRECTORY}/dataset.json"
readonly DEMO_PASSWORD="Demo@2026!"
RECOVERY_CREATED=false

cleanup() {
  local exit_code=$?
  if [[ "${RECOVERY_CREATED}" == "true" ]]; then
    "${SCRIPT_DIRECTORY}/demo-recovery-cleanup.sh" "${RECOVERY_NAME}" \
      >/dev/null 2>&1 || true
  fi
  if [[ -d "${BACKUP_DIRECTORY}" ]] &&
    [[ "${BACKUP_DIRECTORY}" == "${RECOVERY_BACKUP_PREFIX}"capacity.* ]]; then
    rm -rf -- "${BACKUP_DIRECTORY}"
  fi
  exit "${exit_code}"
}

trap cleanup EXIT

if [[ "${REPORT_FILE}" != /* ]]; then
  recovery_fail "脱敏报告路径必须是绝对路径"
  exit 1
fi

require_recovery_command docker
require_recovery_command curl
require_recovery_command jq
require_recovery_command python3
require_recovery_name "${RECOVERY_NAME}"

"${SCRIPT_DIRECTORY}/demo-backup.sh" "${BACKUP_DIRECTORY}"
RECOVERY_CREATED=true
"${SCRIPT_DIRECTORY}/demo-restore.sh" \
  "${BACKUP_DIRECTORY}" \
  "${RECOVERY_NAME}"

load_recovery_environment "${RECOVERY_DEFAULT_ENV_FILE}"
export_recovery_environment "${RECOVERY_NAME}"

FOUNDATION_CAPACITY_DEMO_PASSWORD="${DEMO_PASSWORD}" \
  python3 "${SCRIPT_DIRECTORY}/query_capacity.py" \
    --control-base \
    "http://127.0.0.1:${FOUNDATION_CONTROL_PLANE_PORT}" \
    --query-base \
    "http://127.0.0.1:${FOUNDATION_QUERY_RUNTIME_PORT}" \
    --output "${CAPACITY_REPORT}" \
    --concurrency-per-tenant 4 \
    --warmup-per-tenant 5 \
    --samples-per-tenant 20 \
    --p95-limit-milliseconds 1000 \
    --minimum-throughput-per-second 5

"${SCRIPT_DIRECTORY}/demo-resilience-verify.sh" \
  "${RECOVERY_NAME}" \
  "${RESILIENCE_REPORT}"

FOUNDATION_CAPACITY_DEMO_PASSWORD="${DEMO_PASSWORD}" \
  python3 "${SCRIPT_DIRECTORY}/query_capacity.py" \
    --control-base \
    "http://127.0.0.1:${FOUNDATION_CONTROL_PLANE_PORT}" \
    --query-base \
    "http://127.0.0.1:${FOUNDATION_QUERY_RUNTIME_PORT}" \
    --output "${POST_RESTART_REPORT}" \
    --concurrency-per-tenant 1 \
    --warmup-per-tenant 0 \
    --samples-per-tenant 1 \
    --p95-limit-milliseconds 5000 \
    --minimum-throughput-per-second 0.01

recovery_compose exec -T postgres \
  psql \
  --username "${FOUNDATION_POSTGRES_USER}" \
  --dbname "${FOUNDATION_POSTGRES_DATABASE}" \
  --no-psqlrc \
  --tuples-only \
  --no-align \
  --command "
    SELECT json_build_object(
      'tenantCount', (SELECT count(*) FROM organization_access.tenant),
      'knowledgeObjectCount', (
        SELECT count(*) FROM knowledge_data.text_data_source_content
      ),
      'indexChunkCount', (
        SELECT count(*) FROM knowledge_data.index_chunk
      ),
      'publishedAssistantVersionCount', (
        SELECT count(*)
        FROM assistant_delivery.assistant_version
        WHERE status = 'PUBLISHED'
      )
    );
  " >"${DATASET_REPORT}"

recovery_compose images --format json |
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

"${SCRIPT_DIRECTORY}/demo-recovery-cleanup.sh" "${RECOVERY_NAME}"
RECOVERY_CREATED=false

for resource in \
  "${RECOVERY_NAME}-postgres" \
  "${RECOVERY_NAME}-redis" \
  "${RECOVERY_NAME}-minio"; do
  if docker volume inspect "${resource}" >/dev/null 2>&1; then
    recovery_fail "容量演练恢复卷未被清理：${resource}"
    exit 1
  fi
done
if docker network inspect "${RECOVERY_NAME}" >/dev/null 2>&1; then
  recovery_fail "容量演练恢复网络未被清理"
  exit 1
fi

wait_for_http_endpoint "http://127.0.0.1:18080/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18081/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18082/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:13000/healthz"
wait_for_http_endpoint "http://127.0.0.1:13001/healthz"

readonly GIT_COMMIT="$(
  git -C "${RECOVERY_REPOSITORY_ROOT}" rev-parse HEAD
)"
readonly OPERATING_SYSTEM="$(uname -srm)"
readonly HARDWARE_MODEL="$(sysctl -n hw.model 2>/dev/null || uname -m)"
readonly MEMORY_BYTES="$(sysctl -n hw.memsize 2>/dev/null || echo 0)"
readonly PROCESSOR="$(sysctl -n machdep.cpu.brand_string 2>/dev/null || uname -m)"
readonly DOCKER_VERSION="$(docker version --format '{{.Server.Version}}')"
readonly COMPOSE_VERSION="$(docker compose version --short)"

mkdir -p "$(dirname "${REPORT_FILE}")"
jq -n \
  --slurpfile capacity "${CAPACITY_REPORT}" \
  --slurpfile resilience "${RESILIENCE_REPORT}" \
  --slurpfile postRestart "${POST_RESTART_REPORT}" \
  --slurpfile dataset "${DATASET_REPORT}" \
  --slurpfile images "${IMAGES_REPORT}" \
  --arg gitCommit "${GIT_COMMIT}" \
  --arg operatingSystem "${OPERATING_SYSTEM}" \
  --arg hardwareModel "${HARDWARE_MODEL}" \
  --arg processor "${PROCESSOR}" \
  --argjson memoryBytes "${MEMORY_BYTES}" \
  --arg dockerVersion "${DOCKER_VERSION}" \
  --arg composeVersion "${COMPOSE_VERSION}" \
  '{
    schemaVersion: 1,
    passed: (
      $capacity[0].passed
      and $resilience[0].allRecovered
      and $postRestart[0].passed
    ),
    gitCommit: $gitCommit,
    capacity: $capacity[0],
    resilience: (
      $resilience[0]
      + {postRestartQueryPassed: $postRestart[0].passed}
    ),
    dataset: $dataset[0],
    environment: {
      operatingSystem: $operatingSystem,
      hardwareModel: $hardwareModel,
      processor: $processor,
      memoryBytes: $memoryBytes,
      dockerVersion: $dockerVersion,
      composeVersion: $composeVersion
    },
    images: $images[0],
    sourceEnvironmentHealthy: true,
    recoveryResourcesRemoved: true,
    secretsIncluded: false
  }' >"${REPORT_FILE}"
chmod 644 "${REPORT_FILE}"

trap - EXIT
rm -rf -- "${BACKUP_DIRECTORY}"

echo "端到端容量与关键服务重启演练通过，脱敏报告：${REPORT_FILE}"
