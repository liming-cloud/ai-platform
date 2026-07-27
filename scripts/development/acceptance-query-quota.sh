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
readonly QUERY_HEALTH_ROOT="${FOUNDATION_ACCEPTANCE_QUERY_HEALTH_ROOT:-http://127.0.0.1:18081}"
readonly DEMO_PASSWORD="${FOUNDATION_ACCEPTANCE_DEMO_PASSWORD:-Demo@2026!}"
readonly STORE_TENANT="100000000000000001"
readonly RESPONSE_FILE="$(mktemp)"

compose() {
  docker compose \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_FILE}" \
    "$@"
}

restore_runtime() {
  rm -f "${RESPONSE_FILE}"
  FOUNDATION_QUERY_QUOTA_DAILY_LIMIT=100 \
    compose up --detach --force-recreate query-runtime >/dev/null
  wait_for_query_runtime
}
trap restore_runtime EXIT

wait_for_query_runtime() {
  local attempt
  for attempt in $(seq 1 30); do
    if curl --fail --silent \
      "${QUERY_HEALTH_ROOT}/actuator/health" >/dev/null 2>&1; then
      return
    fi
    sleep 1
  done
  echo "查询运行时未在预期时间内恢复健康" >&2
  return 1
}

login() {
  jq -n \
    --arg password "${DEMO_PASSWORD}" \
    '{
      tenantCode: "demo-store",
      loginName: "admin",
      password: $password
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${CONTROL_API_ROOT}/auth/sessions" |
    jq -er ".tokenValue"
}

latest_standard_version() {
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -c "
      SELECT id
      FROM assistant_delivery.assistant_version
      WHERE tenant_id = '${STORE_TENANT}'
        AND status = 'PUBLISHED'
        AND minimum_score = 0
      ORDER BY published_at DESC, id DESC
      LIMIT 1;
    "
}

today_count() {
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -c "
      SELECT count(*)
      FROM trusted_query.query_run
      WHERE tenant_id = '${STORE_TENANT}'
        AND created_at >= date_trunc('day', now() AT TIME ZONE 'UTC')
            AT TIME ZONE 'UTC'
        AND created_at < (
          date_trunc('day', now() AT TIME ZONE 'UTC') + INTERVAL '1 day'
        ) AT TIME ZONE 'UTC';
    "
}

query_body() {
  local assistant_version_id="$1"
  local question="$2"
  jq -n \
    --arg assistant_version_id "${assistant_version_id}" \
    --arg question "${question}" \
    '{
      assistantVersionId: $assistant_version_id,
      question: $question
    }'
}

create_query() {
  local token="$1"
  local key="$2"
  local body="$3"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    -H "Idempotency-Key: ${key}" \
    -H "Content-Type: application/json" \
    --data-binary "${body}" \
    "${QUERY_API_ROOT}/query-runs"
}

baseline="$(today_count)"
readonly DAILY_LIMIT="$((baseline + 1))"
FOUNDATION_QUERY_QUOTA_DAILY_LIMIT="${DAILY_LIMIT}" \
  compose up --detach --force-recreate query-runtime >/dev/null
wait_for_query_runtime

token="$(login)"
assistant_version_id="$(latest_standard_version)"
[[ -n "${assistant_version_id}" ]]
nonce="$(date +%s)-$$"
first_body="$(
  query_body \
    "${assistant_version_id}" \
    "青柚气泡水在哪里，价格是多少，配额验收编号 ${nonce}"
)"
first="$(
  create_query \
    "${token}" \
    "acceptance-quota-${nonce}-first" \
    "${first_body}"
)"
first_id="$(jq -er ".id" <<<"${first}")"

second_body="$(
  query_body \
    "${assistant_version_id}" \
    "青柚气泡水销量如何，配额验收编号 ${nonce}"
)"
status="$(
  curl --silent --show-error \
    --output "${RESPONSE_FILE}" \
    --write-out "%{http_code}" \
    -H "Authorization: Bearer ${token}" \
    -H "Idempotency-Key: acceptance-quota-${nonce}-second" \
    -H "Content-Type: application/json" \
    --data-binary "${second_body}" \
    "${QUERY_API_ROOT}/query-runs"
)"

[[ "${status}" == "429" ]]
jq -e \
  --argjson limit "${DAILY_LIMIT}" \
  '.code == "TRUSTED_QUERY.QUOTA.0001"
    and .details.limitType == "DAILY"
    and .details.limit == $limit
    and (.details.retryAt | type == "string")' \
  >/dev/null <"${RESPONSE_FILE}"

replayed="$(
  create_query \
    "${token}" \
    "acceptance-quota-${nonce}-first" \
    "${first_body}"
)"
jq -e \
  --arg id "${first_id}" \
  '.id == $id' \
  >/dev/null <<<"${replayed}"

[[ "$(today_count)" == "${DAILY_LIMIT}" ]]

echo "查询配额验收通过：原子每日限额、稳定超额错误和幂等重放均通过"
