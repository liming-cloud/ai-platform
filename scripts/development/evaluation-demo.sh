#!/usr/bin/env bash
set -euo pipefail

readonly REPOSITORY_ROOT="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." >/dev/null 2>&1
  pwd
)"
readonly COMPOSE_FILE="${REPOSITORY_ROOT}/deploy/compose/compose.yml"
readonly ENV_FILE="${REPOSITORY_ROOT}/deploy/compose/.env.example"
readonly CONTROL_API_ROOT="${FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT:-http://127.0.0.1:13000/api/v1}"
readonly QUERY_API_ROOT="${FOUNDATION_ACCEPTANCE_QUERY_API_ROOT:-http://127.0.0.1:18081/api/v1}"
readonly DEMO_PASSWORD="${FOUNDATION_ACCEPTANCE_DEMO_PASSWORD:-Demo@2026!}"
readonly STORE_TENANT="10000000-0000-0000-0000-000000000001"
readonly CAMPUS_TENANT="10000000-0000-0000-0000-000000000002"
readonly REPORT_PATH="${EVALUATION_REPORT_PATH:-/tmp/trusted-rag-demo-evaluation.json}"

bundle_path="$(mktemp)"
case_results_path="$(mktemp)"
cross_results_path="$(mktemp)"
refusal_results_path="$(mktemp)"
trap 'rm -f "${bundle_path}" "${case_results_path}" "${cross_results_path}" "${refusal_results_path}"' EXIT

compose() {
  docker compose \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_FILE}" \
    "$@"
}

login() {
  local tenant_code="$1"
  jq -n \
    --arg tenant_code "${tenant_code}" \
    --arg password "${DEMO_PASSWORD}" \
    '{
      tenantCode: $tenant_code,
      loginName: "admin",
      password: $password
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${CONTROL_API_ROOT}/auth/sessions" |
    jq -er '.tokenValue'
}

latest_standard_metadata() {
  local tenant_id="$1"
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -F '|' \
    -c "
      SELECT
        version.id,
        version.assistant_id,
        version.chat_deployment_id,
        version.knowledge_space_id,
        connection.provider_kind
      FROM assistant_delivery.assistant_version version
      JOIN model_runtime.model_deployment deployment
        ON deployment.tenant_id = version.tenant_id
       AND deployment.id = version.chat_deployment_id
      JOIN model_runtime.provider_connection connection
        ON connection.tenant_id = deployment.tenant_id
       AND connection.id = deployment.provider_connection_id
      WHERE version.tenant_id = '${tenant_id}'
        AND version.status = 'PUBLISHED'
        AND version.minimum_score = 0
      ORDER BY version.published_at DESC, version.id DESC
      LIMIT 1;
    "
}

query() {
  local token="$1"
  local idempotency_key="$2"
  local assistant_version_id="$3"
  local question="$4"
  jq -n \
    --arg assistant_version_id "${assistant_version_id}" \
    --arg question "${question}" \
    '{
      assistantVersionId: $assistant_version_id,
      question: $question
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: ${idempotency_key}" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${QUERY_API_ROOT}/query-runs"
}

create_version() {
  local token="$1"
  local assistant_id="$2"
  local deployment_id="$3"
  local space_id="$4"
  local minimum_score="$5"
  local idempotency_key="$6"
  jq -n \
    --arg deployment_id "${deployment_id}" \
    --arg space_id "${space_id}" \
    --argjson minimum_score "${minimum_score}" \
    '{
      chatDeploymentId: $deployment_id,
      knowledgeSpaceId: $space_id,
      systemInstruction: "只依据获得授权的编号证据回答，不得补充证据外信息。",
      maxOutputTokens: 256,
      retrievalLimit: 5,
      minimumScore: $minimum_score,
      citationPolicy: "REQUIRED",
      refusalPolicy: "INSUFFICIENT_EVIDENCE"
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: ${idempotency_key}" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${CONTROL_API_ROOT}/assistants/${assistant_id}/versions"
}

publish_version() {
  local token="$1"
  local version_id="$2"
  local expected_version="$3"
  local idempotency_key="$4"
  jq -n \
    --argjson expected_version "${expected_version}" \
    '{expectedVersion: $expected_version}' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: ${idempotency_key}" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${CONTROL_API_ROOT}/assistant-versions/${version_id}/publish"
}

ensure_published() {
  local token="$1"
  local version="$2"
  local publish_key="$3"
  if [[ "$(jq -er '.status' <<<"${version}")" == "DRAFT" ]]; then
    version="$(
      publish_version \
        "${token}" \
        "$(jq -er '.id' <<<"${version}")" \
        "$(jq -er '.version' <<<"${version}")" \
        "${publish_key}"
    )"
  fi
  jq -e '.status == "PUBLISHED"' >/dev/null <<<"${version}"
  printf '%s' "${version}"
}

request_key() {
  local purpose="$1"
  local version_id="$2"
  local question="$3"
  local request_hash
  request_hash="$(
    printf '%s\0%s' "${version_id}" "${question}" |
      shasum -a 256 |
      awk '{print $1}'
  )"
  printf 'evaluation-v3-%s-%s' "${purpose}" "${request_hash}"
}

append_standard_cases() {
  local token="$1"
  local scenario="$2"
  local version_id="$3"
  local cases_file="${REPOSITORY_ROOT}/verification/fixtures/${scenario}/search-cases.json"
  local case_value
  local identifier
  local question
  local response
  while IFS= read -r case_value; do
    identifier="$(jq -er '.id' <<<"${case_value}")"
    question="$(jq -er '.query' <<<"${case_value}")"
    response="$(
      query \
        "${token}" \
        "$(request_key "${identifier}" "${version_id}" "${question}")" \
        "${version_id}" \
        "${question}"
    )"
    jq -cn \
      --arg id "${identifier}" \
      --arg scenario "${scenario}" \
      --argjson response "${response}" \
      '{id: $id, scenario: $scenario, response: $response}' \
      >>"${case_results_path}"
  done < <(jq -c '.cases[]' "${cases_file}")
}

append_cross_tenant_case() {
  local token="$1"
  local identifier="$2"
  local version_id="$3"
  local question="$4"
  local forbidden_file="$5"
  local response
  response="$(
    query \
      "${token}" \
      "$(request_key "${identifier}" "${version_id}" "${question}")" \
      "${version_id}" \
      "${question}"
  )"
  jq -cn \
    --arg id "${identifier}" \
    --slurpfile cases "${forbidden_file}" \
    --argjson response "${response}" \
    '{
      id: $id,
      forbiddenRecordIds: [
        $cases[0].cases[].expectedRecordIds[]
      ] | unique,
      response: $response
    }' \
    >>"${cross_results_path}"
}

restore_standard_version() {
  local token="$1"
  local scenario="$2"
  local assistant_id="$3"
  local deployment_id="$4"
  local space_id="$5"
  local standard
  standard="$(
    create_version \
      "${token}" \
      "${assistant_id}" \
      "${deployment_id}" \
      "${space_id}" \
      "0" \
      "evaluation-v3-${scenario}-standard-version"
  )"
  ensure_published \
    "${token}" \
    "${standard}" \
    "evaluation-v3-${scenario}-standard-publish" \
    >/dev/null
}

append_refusal_case() {
  local token="$1"
  local scenario="$2"
  local assistant_id="$3"
  local deployment_id="$4"
  local space_id="$5"
  local question="$6"
  local strict
  local strict_id
  local response
  strict="$(
    create_version \
      "${token}" \
      "${assistant_id}" \
      "${deployment_id}" \
      "${space_id}" \
      "1.0" \
      "evaluation-v3-${scenario}-strict-version"
  )"
  strict="$(
    ensure_published \
      "${token}" \
      "${strict}" \
      "evaluation-v3-${scenario}-strict-publish"
  )"
  strict_id="$(jq -er '.id' <<<"${strict}")"
  if ! response="$(
    query \
      "${token}" \
      "$(request_key "${scenario}-refusal" "${strict_id}" "${question}")" \
      "${strict_id}" \
      "${question}"
  )"; then
    restore_standard_version \
      "${token}" \
      "${scenario}" \
      "${assistant_id}" \
      "${deployment_id}" \
      "${space_id}"
    return 1
  fi
  restore_standard_version \
    "${token}" \
    "${scenario}" \
    "${assistant_id}" \
    "${deployment_id}" \
    "${space_id}"
  jq -cn \
    --arg id "${scenario}-refusal-001" \
    --argjson response "${response}" \
    '{id: $id, response: $response}' \
    >>"${refusal_results_path}"
}

store_token="$(login "demo-store")"
campus_token="$(login "demo-campus")"
IFS='|' read -r \
  store_version_id \
  store_assistant_id \
  store_deployment_id \
  store_space_id \
  store_provider_kind \
  <<<"$(latest_standard_metadata "${STORE_TENANT}")"
IFS='|' read -r \
  campus_version_id \
  campus_assistant_id \
  campus_deployment_id \
  campus_space_id \
  campus_provider_kind \
  <<<"$(latest_standard_metadata "${CAMPUS_TENANT}")"

[[ -n "${store_version_id}" && "${store_provider_kind}" == "DETERMINISTIC" ]]
[[ -n "${campus_version_id}" && "${campus_provider_kind}" == "DETERMINISTIC" ]]

append_standard_cases \
  "${store_token}" \
  "store" \
  "${store_version_id}"
append_standard_cases \
  "${campus_token}" \
  "campus" \
  "${campus_version_id}"

append_cross_tenant_case \
  "${store_token}" \
  "store-asks-campus-001" \
  "${store_version_id}" \
  "中心图书馆周末几点开门？" \
  "${REPOSITORY_ROOT}/verification/fixtures/campus/search-cases.json"
append_cross_tenant_case \
  "${campus_token}" \
  "campus-asks-store-001" \
  "${campus_version_id}" \
  "青柚气泡水在哪里，多少钱？" \
  "${REPOSITORY_ROOT}/verification/fixtures/store/search-cases.json"

append_refusal_case \
  "${store_token}" \
  "store" \
  "${store_assistant_id}" \
  "${store_deployment_id}" \
  "${store_space_id}" \
  "火星基地第九仓库的量子雨伞库存是多少？"
append_refusal_case \
  "${campus_token}" \
  "campus" \
  "${campus_assistant_id}" \
  "${campus_deployment_id}" \
  "${campus_space_id}" \
  "海王星分校的飞船驾驶证在哪里补办？"

jq -n \
  --slurpfile cases "${case_results_path}" \
  --slurpfile cross_tenant_cases "${cross_results_path}" \
  --slurpfile refusal_cases "${refusal_results_path}" \
  '{
    schemaVersion: 1,
    runtime: {providerKind: "DETERMINISTIC"},
    cases: $cases,
    crossTenantCases: $cross_tenant_cases,
    refusalCases: $refusal_cases
  }' \
  >"${bundle_path}"

python3 \
  "${REPOSITORY_ROOT}/scripts/quality/demo_evaluation.py" \
  "${bundle_path}" \
  --root "${REPOSITORY_ROOT}" \
  --output "${REPORT_PATH}"

jq -e \
  '{
    passed,
    caseCount,
    crossTenantCaseCount,
    refusalCaseCount,
    metrics
  }' \
  "${REPORT_PATH}"
echo "演示离线评测通过，脱敏报告：${REPORT_PATH}"
