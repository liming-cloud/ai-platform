#!/usr/bin/env bash
set -euo pipefail

readonly REPOSITORY_ROOT="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." >/dev/null 2>&1
  pwd
)"
readonly COMPOSE_FILE="${REPOSITORY_ROOT}/deploy/compose/compose.yml"
readonly ENV_FILE="${REPOSITORY_ROOT}/deploy/compose/.env.example"
readonly CONTROL_API_ROOT="http://127.0.0.1:13000/api/v1"
readonly QUERY_API_ROOT="http://127.0.0.1:18081/api/v1"
readonly DEMO_PASSWORD="Demo@2026!"
readonly STORE_TENANT="10000000-0000-0000-0000-000000000001"
readonly CAMPUS_TENANT="10000000-0000-0000-0000-000000000002"

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

latest_standard_version() {
  local tenant_id="$1"
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -c "
      SELECT id
      FROM assistant_delivery.assistant_version
      WHERE tenant_id = '${tenant_id}'
        AND status = 'PUBLISHED'
        AND minimum_score = 0
      ORDER BY published_at DESC, id DESC
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

query_events() {
  local token="$1"
  local query_run_id="$2"
  local last_event_id="${3:-}"
  local -a headers=(
    -H "Authorization: Bearer ${token}"
    -H "Accept: text/event-stream"
  )
  if [[ -n "${last_event_id}" ]]; then
    headers+=(-H "Last-Event-ID: ${last_event_id}")
  fi
  curl --fail-with-body --silent --show-error \
    "${headers[@]}" \
    "${QUERY_API_ROOT}/query-runs/${query_run_id}/events"
}

create_strict_version() {
  local token="$1"
  local assistant_id="$2"
  local deployment_id="$3"
  local space_id="$4"
  jq -n \
    --arg deployment_id "${deployment_id}" \
    --arg space_id "${space_id}" \
    '{
      chatDeploymentId: $deployment_id,
      knowledgeSpaceId: $space_id,
      systemInstruction: "只依据获得授权的编号证据回答，不得补充证据外信息。",
      maxOutputTokens: 256,
      retrievalLimit: 5,
      minimumScore: 1.0,
      citationPolicy: "REQUIRED",
      refusalPolicy: "INSUFFICIENT_EVIDENCE"
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: acceptance-strict-assistant-version-v3" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${CONTROL_API_ROOT}/assistants/${assistant_id}/versions"
}

publish_version() {
  local token="$1"
  local version_id="$2"
  local expected_version="$3"
  jq -n \
    --argjson expected_version "${expected_version}" \
    '{expectedVersion: $expected_version}' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: acceptance-strict-assistant-publish-v3" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${CONTROL_API_ROOT}/assistant-versions/${version_id}/publish"
}

store_token="$(login "demo-store")"
campus_token="$(login "demo-campus")"
store_version_id="$(latest_standard_version "${STORE_TENANT}")"
campus_version_id="$(latest_standard_version "${CAMPUS_TENANT}")"
[[ -n "${store_version_id}" ]]
[[ -n "${campus_version_id}" ]]

store_answer="$(
  query \
    "${store_token}" \
    "acceptance-store-query-v3" \
    "${store_version_id}" \
    "青柚气泡水在哪里，价格是多少"
)"
campus_answer="$(
  query \
    "${campus_token}" \
    "acceptance-campus-query-v3" \
    "${campus_version_id}" \
    "中心图书馆周末开放到几点"
)"

jq -e \
  '.status == "ANSWERED"
    and (.answer | contains("一层 A 区 03 号货架第二层"))
    and (.citations | length > 0)
    and any(.citations[]; .quote | contains("青柚气泡水"))' \
  >/dev/null <<<"${store_answer}"
jq -e \
  '.status == "ANSWERED"
    and (.answer | contains("周六至周日: 09:00-18:00"))
    and (.citations | length > 0)
    and any(.citations[]; .quote | contains("图书馆"))' \
  >/dev/null <<<"${campus_answer}"

store_replayed="$(
  query \
    "${store_token}" \
    "acceptance-store-query-v3" \
    "${store_version_id}" \
    "青柚气泡水在哪里，价格是多少"
)"
jq -e \
  --arg id "$(jq -er '.id' <<<"${store_answer}")" \
  '.id == $id and .status == "ANSWERED"' \
  >/dev/null <<<"${store_replayed}"

store_run_id="$(jq -er '.id' <<<"${store_answer}")"
store_events="$(query_events "${store_token}" "${store_run_id}" | tr -d '\r')"
store_resumed_events="$(
  query_events "${store_token}" "${store_run_id}" "1" | tr -d '\r'
)"
store_completed_events="$(
  query_events "${store_token}" "${store_run_id}" "2" | tr -d '\r'
)"
[[ "$(awk -F: '/^id:/{print $2}' <<<"${store_events}")" == $'1\n2' ]]
[[ "$(awk -F: '/^event:/{print $2}' <<<"${store_events}")" \
  == $'QUERY_STARTED\nQUERY_ANSWERED' ]]
grep -q '"sequence":1' <<<"${store_events}"
grep -q '"sequence":2' <<<"${store_events}"
[[ "$(awk -F: '/^id:/{print $2}' <<<"${store_resumed_events}")" == "2" ]]
! grep -q '^id:1$' <<<"${store_resumed_events}"
[[ -z "${store_completed_events}" ]]

IFS='|' read -r assistant_id deployment_id space_id <<<"$(
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -F '|' \
    -c "
      SELECT assistant_id, chat_deployment_id, knowledge_space_id
      FROM assistant_delivery.assistant_version
      WHERE tenant_id = '${STORE_TENANT}'
        AND id = '${store_version_id}';
    "
)"
strict_version="$(
  create_strict_version \
    "${store_token}" \
    "${assistant_id}" \
    "${deployment_id}" \
    "${space_id}"
)"
if [[ "$(jq -er '.status' <<<"${strict_version}")" == "DRAFT" ]]; then
  strict_version="$(
    publish_version \
      "${store_token}" \
      "$(jq -er '.id' <<<"${strict_version}")" \
      "$(jq -er '.version' <<<"${strict_version}")"
  )"
fi
refused="$(
  query \
    "${store_token}" \
    "acceptance-refused-query-v3" \
    "$(jq -er '.id' <<<"${strict_version}")" \
    "火星基地第九仓库的量子雨伞库存是多少"
)"
jq -e \
  '.status == "REFUSED"
    and .refusalReason == "INSUFFICIENT_EVIDENCE"
    and .answer == null
    and (.citations | length == 0)' \
  >/dev/null <<<"${refused}"

cross_status="$(
  curl --silent --output /dev/null --write-out "%{http_code}" \
    -H "Authorization: Bearer ${campus_token}" \
    "${QUERY_API_ROOT}/query-runs/${store_run_id}"
)"
[[ "${cross_status}" == "404" ]]
cross_event_status="$(
  curl --silent --output /dev/null --write-out "%{http_code}" \
    -H "Authorization: Bearer ${campus_token}" \
    -H "Accept: text/event-stream" \
    "${QUERY_API_ROOT}/query-runs/${store_run_id}/events"
)"
[[ "${cross_event_status}" == "404" ]]

readonly STORE_ANSWER_KEY_HASH="$(
  printf '%s' 'acceptance-store-query-v3' | shasum -a 256 | awk '{print $1}'
)"
readonly STORE_REFUSED_KEY_HASH="$(
  printf '%s' 'acceptance-refused-query-v3' | shasum -a 256 | awk '{print $1}'
)"
readonly STORE_RUN_COUNT="$(
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -c "
      SELECT count(*)
      FROM trusted_query.query_run_request
      WHERE tenant_id = '${STORE_TENANT}'
        AND idempotency_key_hash IN (
          '${STORE_ANSWER_KEY_HASH}',
          '${STORE_REFUSED_KEY_HASH}'
        );
    "
)"
[[ "${STORE_RUN_COUNT}" == "2" ]]
readonly STORE_EVENT_COUNT="$(
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -c "
      SELECT count(*)
      FROM trusted_query.query_run_event
      WHERE tenant_id = '${STORE_TENANT}'
        AND query_run_id = '${store_run_id}';
    "
)"
[[ "${STORE_EVENT_COUNT}" == "2" ]]

echo "可信查询容器验收通过：带引用回答、拒答、幂等计量、单调事件重连和租户隔离均通过"
