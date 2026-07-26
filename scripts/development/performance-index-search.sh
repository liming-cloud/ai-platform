#!/usr/bin/env bash
set -euo pipefail

readonly CONTROL_API_ROOT="http://127.0.0.1:13000/api/v1"
readonly QUERY_API_ROOT="http://127.0.0.1:18081/api/v1"
readonly SAMPLE_COUNT=100
readonly WARMUP_COUNT=20
readonly P95_LIMIT_SECONDS="${FOUNDATION_SEARCH_P95_SECONDS:-0.200}"
readonly DURATIONS_FILE="$(mktemp)"
readonly RESPONSE_FILE="$(mktemp)"
trap 'rm -f "${DURATIONS_FILE}" "${RESPONSE_FILE}"' EXIT

token="$(
  jq -n \
    --arg tenantCode "demo-store" \
    --arg loginName "admin" \
    --arg password "Demo@2026!" \
    '{tenantCode: $tenantCode, loginName: $loginName, password: $password}' |
    curl --fail-with-body --silent --show-error \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${CONTROL_API_ROOT}/auth/sessions" |
    jq -er '.tokenValue'
)"
space_id="$(
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    "${CONTROL_API_ROOT}/knowledge-spaces?limit=20" |
    jq -er '.items[] | select(.name == "门店商品知识") | .id'
)"
request_body="$(
  jq -cn \
    --arg knowledgeSpaceId "${space_id}" \
    --arg text "青柚气泡水在哪里，价格是多少" \
    '{knowledgeSpaceId: $knowledgeSpaceId, text: $text, limit: 3}'
)"

search_once() {
  local output_file="$1"
  curl --silent --show-error \
    --output "${output_file}" \
    --write-out '%{http_code} %{time_total}\n' \
    -H "Authorization: Bearer ${token}" \
    -H "Content-Type: application/json" \
    --data-binary "${request_body}" \
    "${QUERY_API_ROOT}/query-candidates"
}

for _ in $(seq 1 "${WARMUP_COUNT}"); do
  read -r response_code _ < <(search_once /dev/null)
  [[ "${response_code}" == "200" ]]
done

for _ in $(seq 1 "${SAMPLE_COUNT}"); do
  read -r response_code duration < <(search_once "${RESPONSE_FILE}")
  [[ "${response_code}" == "200" ]]
  jq -e \
    '.candidates | length > 0
      and any(.[]; .content | contains("青柚气泡水"))' \
    >/dev/null <"${RESPONSE_FILE}"
  printf '%s\n' "${duration}" >>"${DURATIONS_FILE}"
done

sorted="$(sort -n "${DURATIONS_FILE}")"
p50="$(sed -n '50p' <<<"${sorted}")"
p95="$(sed -n '95p' <<<"${sorted}")"
maximum="$(tail -n 1 <<<"${sorted}")"

if ! awk -v actual="${p95}" -v limit="${P95_LIMIT_SECONDS}" \
  'BEGIN { exit !(actual <= limit) }'; then
  echo "候选检索 95 分位 ${p95} 秒超过门禁 ${P95_LIMIT_SECONDS} 秒" >&2
  exit 1
fi

curl --fail-with-body --silent --show-error \
  -X DELETE \
  -H "Authorization: Bearer ${token}" \
  "${CONTROL_API_ROOT}/auth/sessions/current" \
  >/dev/null

echo "候选检索性能通过：预热 ${WARMUP_COUNT} 次，采样 ${SAMPLE_COUNT} 次"
echo "中位数 ${p50} 秒，95 分位 ${p95} 秒，最大值 ${maximum} 秒"
