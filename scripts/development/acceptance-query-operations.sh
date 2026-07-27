#!/usr/bin/env bash
set -euo pipefail

readonly REPOSITORY_ROOT="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." >/dev/null 2>&1
  pwd
)"
readonly COMPOSE_FILE="${REPOSITORY_ROOT}/deploy/compose/compose.yml"
readonly ENV_FILE="${REPOSITORY_ROOT}/deploy/compose/.env.example"
readonly CONTROL_API_ROOT="${FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT:-http://127.0.0.1:13000/api/v1}"
readonly DEMO_PASSWORD="${FOUNDATION_ACCEPTANCE_DEMO_PASSWORD:-Demo@2026!}"
readonly STORE_TENANT="100000000000000001"
readonly CAMPUS_TENANT="100000000000000002"

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
      "${CONTROL_API_ROOT}/auth/sessions"
}

authorized_get() {
  local token="$1"
  local path="$2"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    "${CONTROL_API_ROOT}/${path}"
}

database_usage() {
  local tenant_id="$1"
  local from="$2"
  local to="$3"
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -F '|' \
    -c "
      SELECT
        count(*),
        count(*) FILTER (WHERE status = 'ANSWERED'),
        count(*) FILTER (WHERE status = 'REFUSED'),
        count(*) FILTER (WHERE status = 'FAILED'),
        count(*) FILTER (WHERE status = 'RUNNING'),
        coalesce(sum(input_tokens), 0),
        coalesce(sum(output_tokens), 0),
        coalesce(sum(total_tokens), 0)
      FROM trusted_query.query_run
      WHERE tenant_id = '${tenant_id}'
        AND created_at >= '${from}'::timestamptz
        AND created_at < '${to}'::timestamptz;
    "
}

assert_usage_matches_database() {
  local tenant_id="$1"
  local usage="$2"
  local from
  local to
  local measured
  local expected
  from="$(jq -er '.from' <<<"${usage}")"
  to="$(jq -er '.to' <<<"${usage}")"
  measured="$(database_usage "${tenant_id}" "${from}" "${to}")"
  IFS='|' read -r \
    accepted answered refused failed running input output total \
    <<<"${measured}"
  expected="$(
    jq -n \
      --argjson accepted "${accepted}" \
      --argjson answered "${answered}" \
      --argjson refused "${refused}" \
      --argjson failed "${failed}" \
      --argjson running "${running}" \
      --argjson input "${input}" \
      --argjson output "${output}" \
      --argjson total "${total}" \
      '{
        acceptedQueries: $accepted,
        answeredQueries: $answered,
        refusedQueries: $refused,
        failedQueries: $failed,
        runningQueries: $running,
        inputTokens: $input,
        outputTokens: $output,
        totalTokens: $total
      }'
  )"
  jq -e \
    --argjson expected "${expected}" \
    '{
      acceptedQueries,
      answeredQueries,
      refusedQueries,
      failedQueries,
      runningQueries,
      inputTokens,
      outputTokens,
      totalTokens
    } == $expected
    and .dailyQuota.limit == 100
    and .concurrentQuota.limit == 4
    and (.generatedAt | type == "string")' \
    >/dev/null <<<"${usage}"
}

store_session="$(login "demo-store")"
campus_session="$(login "demo-campus")"
store_token="$(jq -er '.tokenValue' <<<"${store_session}")"
campus_token="$(jq -er '.tokenValue' <<<"${campus_session}")"

readonly OPERATIONS_PERMISSION_COUNT="$(
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -c "
      SELECT count(*)
      FROM organization_access.membership_permission
      WHERE tenant_id IN ('${STORE_TENANT}', '${CAMPUS_TENANT}')
        AND permission_code IN ('usage:read', 'audit:read');
    "
)"
[[ "${OPERATIONS_PERMISSION_COUNT}" == "4" ]]

store_usage="$(authorized_get "${store_token}" "usage")"
campus_usage="$(authorized_get "${campus_token}" "usage")"
assert_usage_matches_database "${STORE_TENANT}" "${store_usage}"
assert_usage_matches_database "${CAMPUS_TENANT}" "${campus_usage}"

store_audit="$(authorized_get "${store_token}" "audit-records?limit=100")"
campus_audit="$(authorized_get "${campus_token}" "audit-records?limit=100")"

jq -e \
  'all(
    .items[];
    ((keys - [
      "assistantVersionId",
      "completedAt",
      "createdAt",
      "inputTokens",
      "outputTokens",
      "queryRunId",
      "questionDigest",
      "refusalReason",
      "requestedBy",
      "status",
      "totalTokens"
    ]) | length) == 0
    and (.questionDigest | test("^[0-9a-f]{64}$"))
  )' \
  >/dev/null <<<"${store_audit}"
jq -e \
  'all(
    .items[];
    ((keys - [
      "assistantVersionId",
      "completedAt",
      "createdAt",
      "inputTokens",
      "outputTokens",
      "queryRunId",
      "questionDigest",
      "refusalReason",
      "requestedBy",
      "status",
      "totalTokens"
    ]) | length) == 0
  )' \
  >/dev/null <<<"${campus_audit}"

store_run_id="$(jq -er '.items[0].queryRunId' <<<"${store_audit}")"
jq -e \
  --arg store_run_id "${store_run_id}" \
  'all(.items[]; .queryRunId != $store_run_id)' \
  >/dev/null <<<"${campus_audit}"

first_page="$(authorized_get "${store_token}" "audit-records?limit=1")"
if [[ "$(jq -r '.nextCursor // empty' <<<"${first_page}")" != "" ]]; then
  next_cursor="$(jq -er '.nextCursor | @uri' <<<"${first_page}")"
  second_page="$(
    authorized_get "${store_token}" "audit-records?limit=1&cursor=${next_cursor}"
  )"
  jq -e \
    --arg first_id "$(jq -er '.items[0].queryRunId' <<<"${first_page}")" \
    '.items | length == 1 and .[0].queryRunId != $first_id' \
    >/dev/null <<<"${second_page}"
fi

echo "查询运营验收通过：权限、用量实数、脱敏审计、游标分页和租户隔离均通过"
