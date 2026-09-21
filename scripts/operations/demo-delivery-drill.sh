#!/usr/bin/env bash
set -euo pipefail
umask 077

readonly SCRIPT_DIRECTORY="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# shellcheck source=recovery-common.sh
source "${SCRIPT_DIRECTORY}/recovery-common.sh"

if [[ "$#" -gt 1 ]]; then
  echo "用法：demo-delivery-drill.sh [脱敏报告绝对路径]" >&2
  exit 2
fi

readonly REPORT_FILE="${1:-/private/tmp/ai-platform-operations/WP-0007-delivery-demo.json}"
readonly DELIVERY_NAME="trusted-rag-foundation-v1-recovery-delivery-$(
  date -u +%Y%m%d%H%M%S
)-$$"
readonly WORK_DIRECTORY="$(
  mktemp -d "${TMPDIR:-/tmp}/trusted-rag-foundation-v1-delivery.XXXXXX"
)"
readonly EVALUATION_REPORT="${WORK_DIRECTORY}/evaluation.json"
readonly FINAL_FACTS_REPORT="${WORK_DIRECTORY}/final-facts.json"
readonly IMAGES_REPORT="${WORK_DIRECTORY}/images.json"
readonly DEMO_PASSWORD="Demo@2026!"
readonly DELIVERY_STARTED_EPOCH="$(recovery_epoch_seconds)"
DELIVERY_CREATED=false

cleanup() {
  local exit_code=$?
  if [[ "${DELIVERY_CREATED}" == "true" ]]; then
    "${SCRIPT_DIRECTORY}/demo-recovery-cleanup.sh" "${DELIVERY_NAME}" \
      >/dev/null 2>&1 || true
  fi
  if [[ -d "${WORK_DIRECTORY}" ]] &&
    [[ "${WORK_DIRECTORY}" == "${TMPDIR:-/tmp}/trusted-rag-foundation-v1-delivery."* ]]; then
    rm -rf -- "${WORK_DIRECTORY}"
  fi
  exit "${exit_code}"
}

trap cleanup EXIT

if [[ "${REPORT_FILE}" != /* ]]; then
  recovery_fail "交付报告路径必须是绝对路径"
  exit 1
fi

require_recovery_command docker
require_recovery_command curl
require_recovery_command jq
require_recovery_name "${DELIVERY_NAME}"
load_recovery_environment "${RECOVERY_DEFAULT_ENV_FILE}"
export_recovery_environment "${DELIVERY_NAME}"
require_absent_recovery_resources

recovery_compose up --detach --build
DELIVERY_CREATED=true

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

readonly INITIAL_BUSINESS_FACT_COUNT="$(
  recovery_compose exec -T postgres \
    psql \
    --username "${FOUNDATION_POSTGRES_USER}" \
    --dbname "${FOUNDATION_POSTGRES_DATABASE}" \
    --no-psqlrc \
    --tuples-only \
    --no-align \
    --command "
      SELECT sum(fact_count)
      FROM (
        SELECT count(*) AS fact_count
        FROM knowledge_data.knowledge_space
        UNION ALL SELECT count(*) FROM knowledge_data.text_data_source
        UNION ALL SELECT count(*) FROM knowledge_data.index_version
        UNION ALL SELECT count(*) FROM model_runtime.provider_connection
        UNION ALL SELECT count(*) FROM model_runtime.model_deployment
        UNION ALL SELECT count(*) FROM assistant_delivery.assistant
        UNION ALL SELECT count(*) FROM trusted_query.query_run
      ) facts;
    " |
    tr -d '[:space:]'
)"
if [[ "${INITIAL_BUSINESS_FACT_COUNT}" != "0" ]]; then
  recovery_fail "交付演练环境不是空业务状态"
  exit 1
fi

export FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT="http://127.0.0.1:${FOUNDATION_CONSOLE_WEB_PORT}/api/v1"
export FOUNDATION_ACCEPTANCE_QUERY_API_ROOT="http://127.0.0.1:${FOUNDATION_QUERY_RUNTIME_PORT}/api/v1"
export FOUNDATION_ACCEPTANCE_QUERY_HEALTH_ROOT="http://127.0.0.1:${FOUNDATION_QUERY_RUNTIME_PORT}"
export FOUNDATION_ACCEPTANCE_QUERY_WEB_ROOT="http://127.0.0.1:${FOUNDATION_QUERY_WEB_PORT}"
export FOUNDATION_ACCEPTANCE_DEMO_PASSWORD="${DEMO_PASSWORD}"

readonly ACCEPTANCE_STARTED_EPOCH="$(recovery_epoch_seconds)"
make -C "${RECOVERY_REPOSITORY_ROOT}" acceptance-query-web
EVALUATION_REPORT_PATH="${EVALUATION_REPORT}" \
  bash "${RECOVERY_REPOSITORY_ROOT}/scripts/development/evaluation-demo.sh"
bash "${RECOVERY_REPOSITORY_ROOT}/scripts/development/acceptance-query-quota.sh"
bash "${RECOVERY_REPOSITORY_ROOT}/scripts/development/acceptance-query-operations.sh"

wait_for_http_endpoint \
  "http://127.0.0.1:${FOUNDATION_QUERY_RUNTIME_PORT}/actuator/health/readiness"

verify_logout() {
  local tenant_code="$1"
  local session
  local token
  local logout_status
  local revoked_status
  session="$(
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
        "${FOUNDATION_ACCEPTANCE_QUERY_WEB_ROOT}/api/v1/auth/sessions"
  )"
  token="$(jq -er '.tokenValue' <<<"${session}")"
  logout_status="$(
    curl --silent --show-error \
      --output /dev/null \
      --write-out "%{http_code}" \
      --request DELETE \
      -H "Authorization: Bearer ${token}" \
      "${FOUNDATION_ACCEPTANCE_QUERY_WEB_ROOT}/api/v1/auth/sessions/current"
  )"
  [[ "${logout_status}" == "204" ]]
  revoked_status="$(
    curl --silent --show-error \
      --output /dev/null \
      --write-out "%{http_code}" \
      -H "Authorization: Bearer ${token}" \
      "${FOUNDATION_ACCEPTANCE_QUERY_WEB_ROOT}/api/v1/query-assistants"
  )"
  [[ "${revoked_status}" == "401" ]]
}

verify_logout "demo-store"
verify_logout "demo-campus"
readonly ACCEPTANCE_COMPLETED_EPOCH="$(recovery_epoch_seconds)"

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
      'knowledgeSpaceCount', (
        SELECT count(*) FROM knowledge_data.knowledge_space
      ),
      'knowledgeObjectCount', (
        SELECT count(*) FROM knowledge_data.text_data_source_content
      ),
      'activeIndexCount', (
        SELECT count(*)
        FROM knowledge_data.index_version
        WHERE status = 'ACTIVE'
      ),
      'publishedModelDeploymentCount', (
        SELECT count(*)
        FROM model_runtime.model_deployment
        WHERE status = 'PUBLISHED'
      ),
      'publishedAssistantVersionCount', (
        SELECT count(*)
        FROM assistant_delivery.assistant_version
        WHERE status = 'PUBLISHED'
      ),
      'queryRunCount', (SELECT count(*) FROM trusted_query.query_run),
      'citationCount', (SELECT count(*) FROM trusted_query.query_citation)
    );
  " >"${FINAL_FACTS_REPORT}"

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

readonly RUNNING_SERVICE_COUNT="$(
  recovery_compose ps --status running --services |
    wc -l |
    tr -d ' '
)"
if [[ "${RUNNING_SERVICE_COUNT}" != "8" ]]; then
  recovery_fail "最终验收前未运行全部八个服务"
  exit 1
fi

"${SCRIPT_DIRECTORY}/demo-recovery-cleanup.sh" "${DELIVERY_NAME}"
DELIVERY_CREATED=false

for resource in \
  "${DELIVERY_NAME}-postgres" \
  "${DELIVERY_NAME}-redis" \
  "${DELIVERY_NAME}-minio"; do
  if docker volume inspect "${resource}" >/dev/null 2>&1; then
    recovery_fail "交付演练卷未被清理：${resource}"
    exit 1
  fi
done
if docker network inspect "${DELIVERY_NAME}" >/dev/null 2>&1; then
  recovery_fail "交付演练网络未被清理"
  exit 1
fi

wait_for_http_endpoint "http://127.0.0.1:18080/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18081/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:18082/actuator/health/readiness"
wait_for_http_endpoint "http://127.0.0.1:13000/healthz"
wait_for_http_endpoint "http://127.0.0.1:13001/healthz"

readonly DELIVERY_COMPLETED_EPOCH="$(recovery_epoch_seconds)"
readonly GIT_COMMIT="$(
  git -C "${RECOVERY_REPOSITORY_ROOT}" rev-parse HEAD
)"

mkdir -p "$(dirname "${REPORT_FILE}")"
jq -n \
  --slurpfile evaluation "${EVALUATION_REPORT}" \
  --slurpfile facts "${FINAL_FACTS_REPORT}" \
  --slurpfile images "${IMAGES_REPORT}" \
  --arg gitCommit "${GIT_COMMIT}" \
  --argjson initialBusinessFactCount "${INITIAL_BUSINESS_FACT_COUNT}" \
  --argjson serviceCount "${RUNNING_SERVICE_COUNT}" \
  --argjson deploymentDurationSeconds "$((ACCEPTANCE_STARTED_EPOCH - DELIVERY_STARTED_EPOCH))" \
  --argjson acceptanceDurationSeconds "$((ACCEPTANCE_COMPLETED_EPOCH - ACCEPTANCE_STARTED_EPOCH))" \
  --argjson totalDurationSeconds "$((DELIVERY_COMPLETED_EPOCH - DELIVERY_STARTED_EPOCH))" \
  '{
    schemaVersion: 1,
    passed: (
      $evaluation[0].passed
      and $facts[0].tenantCount == 2
      and $facts[0].knowledgeSpaceCount >= 2
      and $facts[0].knowledgeObjectCount >= 2
      and $facts[0].activeIndexCount >= 2
      and $facts[0].publishedModelDeploymentCount >= 4
      and $facts[0].publishedAssistantVersionCount >= 2
      and $facts[0].queryRunCount > 0
      and $facts[0].citationCount > 0
    ),
    gitCommit: $gitCommit,
    initialBusinessFactCount: $initialBusinessFactCount,
    serviceCount: $serviceCount,
    applicationEntryCount: 5,
    scenarios: ["门店", "校园"],
    tasks: {
      knowledgePublication: true,
      modelProbeAndPublication: true,
      assistantPublication: true,
      citedQueryAndRefusal: true,
      queryEventResume: true,
      quotaRejection: true,
      usageAndAudit: true,
      tenantIsolation: true,
      logoutAndRevocation: true
    },
    evaluation: $evaluation[0],
    facts: $facts[0],
    demoLimits: {
      dailyQueryLimit: 100,
      concurrentQueryLimit: 4,
      model: "本地确定性模型",
      data: "门店与校园合成数据",
      publicProductionUse: false
    },
    durations: {
      deploymentSeconds: $deploymentDurationSeconds,
      acceptanceSeconds: $acceptanceDurationSeconds,
      totalSeconds: $totalDurationSeconds
    },
    images: $images[0],
    sourceEnvironmentHealthy: true,
    deliveryResourcesRemoved: true,
    secretsIncluded: false,
    humanMilestoneApprovalRequired: true
  }' >"${REPORT_FILE}"
chmod 644 "${REPORT_FILE}"

if ! jq -e '.passed == true' "${REPORT_FILE}" >/dev/null; then
  recovery_fail "最终交付报告未达到通过条件"
  exit 1
fi

trap - EXIT
rm -rf -- "${WORK_DIRECTORY}"

echo "空环境部署与双场景演示验收通过，脱敏报告：${REPORT_FILE}"
