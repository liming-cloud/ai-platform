#!/usr/bin/env bash
set -euo pipefail

readonly QUERY_WEB_ROOT="http://127.0.0.1:13001"
readonly QUERY_API_ROOT="${QUERY_WEB_ROOT}/api/v1"
readonly DEMO_PASSWORD="Demo@2026!"

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
      "${QUERY_API_ROOT}/auth/sessions" |
    jq -er ".tokenValue"
}

list_assistants() {
  local token="$1"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    "${QUERY_API_ROOT}/query-assistants?limit=20"
}

create_query() {
  local token="$1"
  local assistant_version_id="$2"
  local question="$3"
  local request_hash
  request_hash="$(
    printf '%s\0%s' "${assistant_version_id}" "${question}" |
      shasum -a 256 |
      awk '{print $1}'
  )"
  jq -n \
    --arg assistant_version_id "${assistant_version_id}" \
    --arg question "${question}" \
    '{
      assistantVersionId: $assistant_version_id,
      question: $question
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: acceptance-query-web-${request_hash}" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${QUERY_API_ROOT}/query-runs"
}

query_events() {
  local token="$1"
  local query_run_id="$2"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    -H "Accept: text/event-stream" \
    "${QUERY_API_ROOT}/query-runs/${query_run_id}/events"
}

page="$(
  curl --fail-with-body --silent --show-error "${QUERY_WEB_ROOT}/"
)"
grep -q '<div id="root"></div>' <<<"${page}"

unauthenticated_status="$(
  curl --silent --output /dev/null --write-out "%{http_code}" \
    "${QUERY_API_ROOT}/query-assistants?limit=20"
)"
[[ "${unauthenticated_status}" == "401" ]]

store_token="$(login "demo-store")"
campus_token="$(login "demo-campus")"
store_catalog="$(list_assistants "${store_token}")"
campus_catalog="$(list_assistants "${campus_token}")"

readonly ASSISTANT_FIELDS='[
  "assistantId",
  "assistantVersionId",
  "displayName",
  "publishedAt",
  "versionNumber"
]'
jq -e \
  --argjson expected "${ASSISTANT_FIELDS}" \
  '.items | length > 0
    and all(.[]; (keys | sort) == $expected)' \
  >/dev/null <<<"${store_catalog}"
jq -e \
  --argjson expected "${ASSISTANT_FIELDS}" \
  '.items | length > 0
    and all(.[]; (keys | sort) == $expected)' \
  >/dev/null <<<"${campus_catalog}"

store_assistant_id="$(jq -er ".items[0].assistantId" <<<"${store_catalog}")"
store_version_id="$(
  jq -er ".items[0].assistantVersionId" <<<"${store_catalog}"
)"
campus_version_id="$(
  jq -er ".items[0].assistantVersionId" <<<"${campus_catalog}"
)"
jq -e \
  --arg store_assistant_id "${store_assistant_id}" \
  'all(.items[]; .assistantId != $store_assistant_id)' \
  >/dev/null <<<"${campus_catalog}"
[[ "${store_version_id}" != "${campus_version_id}" ]]

query_run="$(
  create_query \
    "${store_token}" \
    "${store_version_id}" \
    "青柚气泡水在哪里，价格是多少"
)"
jq -e \
  '.status == "ANSWERED" and (.citations | length > 0)' \
  >/dev/null <<<"${query_run}"

query_run_id="$(jq -er ".id" <<<"${query_run}")"
events="$(query_events "${store_token}" "${query_run_id}" | tr -d '\r')"
[[ "$(awk -F: '/^id:/{print $2}' <<<"${events}")" == $'1\n2' ]]
grep -q '^event:QUERY_STARTED$' <<<"${events}"
grep -q '^event:QUERY_ANSWERED$' <<<"${events}"
grep -q '"terminal":true' <<<"${events}"

cross_tenant_status="$(
  curl --silent --output /dev/null --write-out "%{http_code}" \
    -H "Authorization: Bearer ${campus_token}" \
    -H "Accept: text/event-stream" \
    "${QUERY_API_ROOT}/query-runs/${query_run_id}/events"
)"
[[ "${cross_tenant_status}" == "404" ]]

echo "查询站点同源验收通过：登录、最小助手目录、问答事件流和租户隔离均通过"
