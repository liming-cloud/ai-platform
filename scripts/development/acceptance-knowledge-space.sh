#!/usr/bin/env bash
set -euo pipefail

readonly REPOSITORY_ROOT="$(
  cd "$(dirname "${BASH_SOURCE[0]}")/../.." >/dev/null 2>&1
  pwd
)"
readonly COMPOSE_FILE="${REPOSITORY_ROOT}/deploy/compose/compose.yml"
readonly ENV_FILE="${REPOSITORY_ROOT}/deploy/compose/.env.example"
readonly API_ROOT="http://127.0.0.1:13000/api/v1"
readonly BUCKET="foundation-v1-private"
readonly DEMO_PASSWORD="Demo@2026!"

compose() {
  docker compose \
    --env-file "${ENV_FILE}" \
    -f "${COMPOSE_FILE}" \
    "$@"
}

login() {
  local tenant_code="$1"
  local response
  if ! response="$(
    jq -n \
      --arg tenantCode "${tenant_code}" \
      --arg loginName "admin" \
      --arg password "${DEMO_PASSWORD}" \
      '{tenantCode: $tenantCode, loginName: $loginName, password: $password}' |
      curl --fail-with-body --silent --show-error \
        -H "Content-Type: application/json" \
        --data-binary @- \
        "${API_ROOT}/auth/sessions"
  )"; then
    return 1
  fi
  jq -er '.tokenValue' <<<"${response}"
}

create_space() {
  local token="$1"
  local scenario="$2"
  local display_name="$3"
  local response
  if ! response="$(
    jq -n \
      --arg name "${display_name}" \
      --arg description "M1 ${scenario} 合成知识验收" \
      '{name: $name, description: $description}' |
      curl --fail-with-body --silent --show-error \
        -H "Authorization: Bearer ${token}" \
        -H "Idempotency-Key: acceptance-${scenario}-space-v1" \
        -H "Content-Type: application/json" \
        --data-binary @- \
        "${API_ROOT}/knowledge-spaces"
  )"; then
    return 1
  fi
  jq -er '.id' <<<"${response}"
}

create_source() {
  local token="$1"
  local scenario="$2"
  local space_id="$3"
  local display_name="$4"
  local response
  if ! response="$(
    jq -n \
      --arg knowledgeSpaceId "${space_id}" \
      --arg name "${display_name}" \
      '{knowledgeSpaceId: $knowledgeSpaceId, name: $name}' |
      curl --fail-with-body --silent --show-error \
        -H "Authorization: Bearer ${token}" \
        -H "Idempotency-Key: acceptance-${scenario}-source-v1" \
        -H "Content-Type: application/json" \
        --data-binary @- \
        "${API_ROOT}/data-sources"
  )"; then
    return 1
  fi
  jq -er '.id' <<<"${response}"
}

upload_content() {
  local token="$1"
  local scenario="$2"
  local space_id="$3"
  local source_id="$4"
  local fixture="$5"
  local response
  if ! response="$(
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: acceptance-${scenario}-upload-v1" \
      -H "Content-Type: text/plain; charset=UTF-8" \
      --data-binary "@${fixture}" \
      "${API_ROOT}/data-sources/${source_id}/content?knowledgeSpaceId=${space_id}&expectedVersion=0"
  )"; then
    return 1
  fi
  jq -e \
    '.status == "CONTENT_READY"
      and .version == 1
      and .contentVersion == 1
      and .publishedContentVersion == 0' \
    >/dev/null <<<"${response}"
}

publish_source() {
  local token="$1"
  local scenario="$2"
  local space_id="$3"
  local source_id="$4"
  local response
  if ! response="$(
    jq -n \
      --arg knowledgeSpaceId "${space_id}" \
      '{knowledgeSpaceId: $knowledgeSpaceId, expectedVersion: 1}' |
      curl --fail-with-body --silent --show-error \
        -H "Authorization: Bearer ${token}" \
        -H "Idempotency-Key: acceptance-${scenario}-publish-v1" \
        -H "Content-Type: application/json" \
        --data-binary @- \
        "${API_ROOT}/data-sources/${source_id}/publish"
  )"; then
    return 1
  fi
  jq -e \
    '.status == "PUBLISHED"
      and .version == 2
      and .contentVersion == 1
      and .publishedContentVersion == 1' \
    >/dev/null <<<"${response}"
}

verify_persisted_content() {
  local source_id="$1"
  local fixture="$2"
  if [[ ! "${source_id}" =~ ^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$ ]]; then
    echo "数据源标识格式不正确" >&2
    return 1
  fi
  local row
  row="$(
    compose exec -T postgres \
      psql \
      -U foundation_app \
      -d foundation_v1 \
      -At \
      -c "
        SELECT
          content.object_key,
          content.sha256,
          content.byte_size,
          source.status,
          source.version,
          source.content_version,
          source.published_content_version
        FROM knowledge_data.text_data_source AS source
        JOIN knowledge_data.text_data_source_content AS content
          ON content.tenant_id = source.tenant_id
          AND content.knowledge_space_id = source.knowledge_space_id
          AND content.data_source_id = source.id
          AND content.version = source.content_version
        WHERE source.id = '${source_id}'::uuid;
      "
  )"

  local object_key
  local stored_sha256
  local stored_bytes
  local status
  local version
  local content_version
  local published_content_version
  IFS='|' read -r \
    object_key \
    stored_sha256 \
    stored_bytes \
    status \
    version \
    content_version \
    published_content_version <<<"${row}"

  local expected_sha256
  local expected_bytes
  expected_sha256="$(shasum -a 256 "${fixture}" | awk '{print $1}')"
  expected_bytes="$(wc -c <"${fixture}" | tr -d ' ')"

  [[ "${object_key}" == foundation-v1/tenants/*/knowledge-data/knowledge-spaces/* ]]
  [[ "${object_key}" == *"/${expected_sha256}/content.txt" ]]
  [[ "${stored_sha256}" == "${expected_sha256}" ]]
  [[ "${stored_bytes}" == "${expected_bytes}" ]]
  [[ "${status}" == "PUBLISHED" ]]
  [[ "${version}" == "2" ]]
  [[ "${content_version}" == "1" ]]
  [[ "${published_content_version}" == "1" ]]

  local object_sha256
  object_sha256="$(
    compose exec -T minio \
      sh -eu -c '
        mc alias set \
          acceptance \
          http://127.0.0.1:9000 \
          "$MINIO_ROOT_USER" \
          "$MINIO_ROOT_PASSWORD" \
          >/dev/null
        mc cat "acceptance/$1"
      ' sh "${BUCKET}/${object_key}" |
      shasum -a 256 |
      awk '{print $1}'
  )"
  [[ "${object_sha256}" == "${expected_sha256}" ]]
}

accept_scenario() {
  local scenario="$1"
  local tenant_code="$2"
  local space_name="$3"
  local source_name="$4"
  local fixture="${REPOSITORY_ROOT}/verification/fixtures/${scenario}/knowledge.txt"

  local token
  local space_id
  local source_id
  token="$(login "${tenant_code}")"
  space_id="$(create_space "${token}" "${scenario}" "${space_name}")"
  source_id="$(
    create_source "${token}" "${scenario}" "${space_id}" "${source_name}"
  )"
  upload_content \
    "${token}" \
    "${scenario}" \
    "${space_id}" \
    "${source_id}" \
    "${fixture}"
  publish_source "${token}" "${scenario}" "${space_id}" "${source_id}"
  verify_persisted_content "${source_id}" "${fixture}"

  curl --fail-with-body --silent --show-error \
    -X DELETE \
    -H "Authorization: Bearer ${token}" \
    "${API_ROOT}/auth/sessions/current" \
    >/dev/null
  echo "${scenario} 场景知识发布验收通过"
}

accept_scenario \
  "store" \
  "demo-store" \
  "门店商品知识" \
  "门店商品合成文本"
accept_scenario \
  "campus" \
  "demo-campus" \
  "校园公共知识" \
  "校园公共信息合成文本"

readonly PUBLISHED_COUNT="$(
  compose exec -T postgres \
    psql \
    -U foundation_app \
    -d foundation_v1 \
    -At \
    -c "
      SELECT count(*)
      FROM knowledge_data.text_data_source
      WHERE status = 'PUBLISHED'
        AND name IN ('门店商品合成文本', '校园公共信息合成文本');
    "
)"
[[ "${PUBLISHED_COUNT}" == "2" ]]

echo "两租户、两知识空间、两对象和两条发布事实验收通过"
