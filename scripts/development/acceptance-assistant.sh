#!/usr/bin/env bash
set -euo pipefail

readonly API_ROOT="http://127.0.0.1:13000/api/v1"
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
      "${API_ROOT}/auth/sessions" |
    jq -er ".tokenValue"
}

find_published_chat_deployment() {
  local token="$1"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    "${API_ROOT}/model-deployments?limit=20" |
    jq -cer \
      '.items[]
        | select(.modelType == "CHAT" and .status == "PUBLISHED")' |
    head -n 1
}

find_knowledge_space() {
  local token="$1"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    "${API_ROOT}/knowledge-spaces?limit=20" |
    jq -cer '.items[] | select(.name == "门店商品知识")'
}

find_active_index() {
  local token="$1"
  local space_id="$2"
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${token}" \
    "${API_ROOT}/knowledge-spaces/${space_id}/index-versions?limit=20" |
    jq -cer '.items[] | select(.status == "ACTIVE")'
}

create_assistant() {
  local token="$1"
  jq -n '{displayName: "门店可信导购助手"}' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: acceptance-store-assistant-v1" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${API_ROOT}/assistants"
}

create_version() {
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
      systemInstruction: "只依据获得授权的编号证据回答，并在结论后标注引用。",
      maxOutputTokens: 512,
      retrievalLimit: 5,
      minimumScore: 0.2,
      citationPolicy: "REQUIRED",
      refusalPolicy: "INSUFFICIENT_EVIDENCE"
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: acceptance-store-assistant-version-v2" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${API_ROOT}/assistants/${assistant_id}/versions"
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
      -H "Idempotency-Key: acceptance-store-assistant-publish-v2" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${API_ROOT}/assistant-versions/${version_id}/publish"
}

store_token="$(login "demo-store")"
campus_token="$(login "demo-campus")"
deployment="$(find_published_chat_deployment "${store_token}")"
space="$(find_knowledge_space "${store_token}")"
space_id="$(jq -er ".id" <<<"${space}")"
index="$(find_active_index "${store_token}" "${space_id}")"
assistant="$(create_assistant "${store_token}")"
assistant_id="$(jq -er ".id" <<<"${assistant}")"
version="$(
  create_version \
    "${store_token}" \
    "${assistant_id}" \
    "$(jq -er ".id" <<<"${deployment}")" \
    "${space_id}"
)"

if [[ "$(jq -er ".status" <<<"${version}")" == "DRAFT" ]]; then
  publish_expected_version="$(jq -er ".version" <<<"${version}")"
  version="$(
    publish_version \
      "${store_token}" \
      "$(jq -er ".id" <<<"${version}")" \
      "${publish_expected_version}"
  )"
  replayed_version="$(
    publish_version \
      "${store_token}" \
      "$(jq -er ".id" <<<"${version}")" \
      "${publish_expected_version}"
  )"
  jq -e \
    --arg id "$(jq -er ".id" <<<"${version}")" \
    '.id == $id and .status == "PUBLISHED" and .version == 1' \
    >/dev/null <<<"${replayed_version}"
fi

jq -e \
  --arg deployment_id "$(jq -er ".id" <<<"${deployment}")" \
  --argjson deployment_revision "$(jq -er ".version" <<<"${deployment}")" \
  --arg space_id "${space_id}" \
  --arg index_id "$(jq -er ".id" <<<"${index}")" \
  --argjson index_revision "$(jq -er ".version" <<<"${index}")" \
  '.status == "PUBLISHED"
    and .version == 1
    and .chatDeploymentId == $deployment_id
    and .chatDeploymentRevision == $deployment_revision
    and .knowledgeSpaceId == $space_id
    and .indexVersionId == $index_id
    and .indexVersionRevision == $index_revision
    and .citationPolicy == "REQUIRED"
    and .refusalPolicy == "INSUFFICIENT_EVIDENCE"' \
  >/dev/null <<<"${version}"

campus_assistants="$(
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${campus_token}" \
    "${API_ROOT}/assistants?limit=20"
)"
jq -e --arg assistant_id "${assistant_id}" \
  'all(.items[]; .id != $assistant_id)' \
  >/dev/null <<<"${campus_assistants}"

echo "助手不可变版本容器验收通过：已冻结模型和索引修订、发布并验证租户隔离"
