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
readonly CROSS_TENANT_RESPONSE="$(mktemp)"
trap 'rm -f "${CROSS_TENANT_RESPONSE}"' EXIT

compose() {
  docker compose \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_FILE}" \
    "$@"
}

login() {
  local tenant_code="$1"
  jq -n \
    --arg tenantCode "${tenant_code}" \
    --arg loginName "admin" \
    --arg password "${DEMO_PASSWORD}" \
    '{tenantCode: $tenantCode, loginName: $loginName, password: $password}' |
    curl --fail-with-body --silent --show-error \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${CONTROL_API_ROOT}/auth/sessions" |
    jq -er '.tokenValue'
}

find_space() {
  local token="$1"
  local name="$2"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    "${CONTROL_API_ROOT}/knowledge-spaces?limit=20" |
    jq -er --arg name "${name}" \
      '.items[] | select(.name == $name) | .id'
}

create_index() {
  local token="$1"
  local scenario="$2"
  local space_id="$3"
  local embedding_deployment_id="$4"
  jq -n \
    --arg embeddingDeploymentId "${embedding_deployment_id}" \
    '{embeddingDeploymentId: $embeddingDeploymentId}' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: acceptance-${scenario}-index-v2" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${CONTROL_API_ROOT}/knowledge-spaces/${space_id}/index-versions"
}

find_embedding_deployment() {
  local token="$1"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    "${CONTROL_API_ROOT}/model-deployments" |
    jq -er \
      '[.items[]
        | select(.modelType == "EMBEDDING" and .status == "PUBLISHED")
        | .id][0]'
}

wait_for_index() {
  local token="$1"
  local space_id="$2"
  local index_id="$3"
  local response
  local status
  for _ in {1..60}; do
    response="$(
      curl --fail-with-body --silent --show-error \
        -H "Authorization: Bearer ${token}" \
        "${CONTROL_API_ROOT}/knowledge-spaces/${space_id}/index-versions?limit=20"
    )"
    status="$(
      jq -er --arg id "${index_id}" \
        '.items[] | select(.id == $id) | .status' <<<"${response}"
    )"
    if [[ "${status}" == "READY" || "${status}" == "ACTIVE" ]]; then
      jq -cer --arg id "${index_id}" \
        '.items[] | select(.id == $id)' <<<"${response}"
      return 0
    fi
    if [[ "${status}" == "FAILED" ]]; then
      echo "索引构建失败: ${index_id}" >&2
      return 1
    fi
    sleep 1
  done
  echo "索引构建超时: ${index_id}" >&2
  return 1
}

publish_index() {
  local token="$1"
  local scenario="$2"
  local space_id="$3"
  local index_json="$4"
  local status
  status="$(jq -er '.status' <<<"${index_json}")"
  if [[ "${status}" == "ACTIVE" ]]; then
    printf '%s\n' "${index_json}"
    return 0
  fi

  local index_id
  local version
  index_id="$(jq -er '.id' <<<"${index_json}")"
  version="$(jq -er '.version' <<<"${index_json}")"
  jq -n --argjson expectedVersion "${version}" \
    '{expectedVersion: $expectedVersion}' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: acceptance-${scenario}-activate-index-v2" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${CONTROL_API_ROOT}/knowledge-spaces/${space_id}/index-versions/${index_id}/publish"
}

search_candidates() {
  local token="$1"
  local space_id="$2"
  local query="$3"
  jq -n \
    --arg knowledgeSpaceId "${space_id}" \
    --arg text "${query}" \
    '{knowledgeSpaceId: $knowledgeSpaceId, text: $text, limit: 3}' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${QUERY_API_ROOT}/query-candidates"
}

accept_scenario() {
  local scenario="$1"
  local tenant_code="$2"
  local space_name="$3"
  local query="$4"
  local expected_text="$5"
  local token
  local space_id
  local embedding_deployment_id
  local created
  local index_json
  local active
  local result

  token="$(login "${tenant_code}")"
  space_id="$(find_space "${token}" "${space_name}")"
  embedding_deployment_id="$(find_embedding_deployment "${token}")"
  created="$(create_index \
    "${token}" \
    "${scenario}" \
    "${space_id}" \
    "${embedding_deployment_id}")"
  index_json="$(wait_for_index \
    "${token}" \
    "${space_id}" \
    "$(jq -er '.id' <<<"${created}")")"
  active="$(publish_index \
    "${token}" \
    "${scenario}" \
    "${space_id}" \
    "${index_json}")"
  jq -e \
    --arg embedding_deployment_id "${embedding_deployment_id}" \
    '.status == "ACTIVE"
      and .embeddingDeploymentId == $embedding_deployment_id
      and .embeddingDeploymentRevision > 0
      and .embeddingDimension == 128
      and .chunkCount > 0
      and .buildDigest != null' \
    >/dev/null <<<"${active}"

  result="$(search_candidates "${token}" "${space_id}" "${query}")"
  jq -e --arg expected "${expected_text}" \
    '.candidates | length > 0
      and any(.[]; .content | contains($expected))' \
    >/dev/null <<<"${result}"
  printf '%s|%s\n' "${token}" "${space_id}"
}

store="$(
  accept_scenario \
    "store" \
    "demo-store" \
    "门店商品知识" \
    "青柚气泡水在哪里，价格是多少" \
    "一层 A 区 03 号货架第二层"
)"
campus="$(
  accept_scenario \
    "campus" \
    "demo-campus" \
    "校园公共知识" \
    "中心图书馆周末开放到几点" \
    "周六至周日: 09:00-18:00"
)"

IFS='|' read -r store_token store_space_id <<<"${store}"
IFS='|' read -r campus_token campus_space_id <<<"${campus}"

cross_tenant_status="$(
  jq -n \
    --arg knowledgeSpaceId "${store_space_id}" \
    --arg text "青柚气泡水在哪里" \
    '{knowledgeSpaceId: $knowledgeSpaceId, text: $text, limit: 3}' |
    curl --silent --show-error \
      --output "${CROSS_TENANT_RESPONSE}" \
      --write-out "%{http_code}" \
      -H "Authorization: Bearer ${campus_token}" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${QUERY_API_ROOT}/query-candidates"
)"
[[ "${cross_tenant_status}" == "404" ]]
jq -e \
  '.code == "TRUSTED_QUERY.RESOURCE.0002"
    and .message == "请求的资源不存在"
    and (.details | length == 0)' \
  >/dev/null <"${CROSS_TENANT_RESPONSE}"

readonly ACTIVE_COUNT="$(
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -c "
      SELECT count(*)
      FROM knowledge_data.index_version
      WHERE status = 'ACTIVE';
    "
)"
readonly CHUNK_COUNT="$(
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -c "
      SELECT count(*)
      FROM knowledge_data.index_chunk;
    "
)"
[[ "${ACTIVE_COUNT}" == "2" ]]
[[ "${CHUNK_COUNT}" -gt 0 ]]

curl --fail-with-body --silent --show-error \
  -X DELETE \
  -H "Authorization: Bearer ${store_token}" \
  "${CONTROL_API_ROOT}/auth/sessions/current" \
  >/dev/null
curl --fail-with-body --silent --show-error \
  -X DELETE \
  -H "Authorization: Bearer ${campus_token}" \
  "${CONTROL_API_ROOT}/auth/sessions/current" \
  >/dev/null

echo "门店与校园索引构建、发布、候选命中和跨租户隔离验收通过"
