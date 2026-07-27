#!/usr/bin/env bash
set -euo pipefail

readonly API_ROOT="${FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT:-http://127.0.0.1:13000/api/v1}"
readonly DEMO_PASSWORD="${FOUNDATION_ACCEPTANCE_DEMO_PASSWORD:-Demo@2026!}"
readonly CREDENTIAL_REFERENCE="secret://demo/deployment-provider"

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

create_provider() {
  local token="$1"
  jq -n \
    --arg credential_reference "${CREDENTIAL_REFERENCE}" \
    '{
      providerKind: "DETERMINISTIC",
      displayName: "模型部署验收供应商",
      endpoint: "memory://deterministic",
      region: "本地",
      authenticationType: "API_KEY",
      credentialReference: $credential_reference
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: acceptance-model-deployment-provider-v1" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${API_ROOT}/model-providers"
}

ensure_provider_verified() {
  local token="$1"
  local provider="$2"
  local status
  status="$(jq -er ".status" <<<"${provider}")"
  if [[ "${status}" == "VERIFIED" ]]; then
    printf "%s" "${provider}"
    return
  fi

  local provider_id
  local expected_version
  provider_id="$(jq -er ".id" <<<"${provider}")"
  expected_version="$(jq -er ".version" <<<"${provider}")"
  jq -n \
    --argjson expected_version "${expected_version}" \
    '{
      expectedVersion: $expected_version,
      modelCode: "deterministic-chat",
      timeoutMillis: 2000
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${API_ROOT}/model-providers/${provider_id}/probe" |
    jq -er ".providerConnection"
}

create_deployment() {
  local token="$1"
  local provider_id="$2"
  local model_type="$3"
  local idempotency_key="acceptance-model-deployment-embedding-v2"
  if [[ "${model_type}" == "CHAT" ]]; then
    idempotency_key="acceptance-model-deployment-v1"
  fi
  jq -n \
    --arg provider_id "${provider_id}" \
    --arg model_type "${model_type}" \
    'if $model_type == "CHAT" then {
       providerConnectionId: $provider_id,
       modelCode: "deterministic-chat",
       displayName: "验收确定性对话部署",
       modelType: "CHAT",
       capabilities: ["CHAT"],
       contextWindow: 8192,
       outputLimit: 1024
     } else {
       providerConnectionId: $provider_id,
       modelCode: "deterministic-embedding",
       displayName: "验收确定性嵌入部署",
       modelType: "EMBEDDING",
       capabilities: ["EMBEDDING"],
       contextWindow: 8192,
       embeddingDimension: 128
     } end' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: ${idempotency_key}" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${API_ROOT}/model-deployments"
}

probe_deployment() {
  local token="$1"
  local deployment_id="$2"
  local expected_version="$3"
  jq -n \
    --argjson expected_version "${expected_version}" \
    '{expectedVersion: $expected_version, timeoutMillis: 2000}' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${API_ROOT}/model-deployments/${deployment_id}/probe"
}

publish_deployment() {
  local token="$1"
  local deployment_id="$2"
  local expected_version="$3"
  jq -n \
    --argjson expected_version "${expected_version}" \
    '{expectedVersion: $expected_version}' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${API_ROOT}/model-deployments/${deployment_id}/publish"
}

ensure_published_deployment() {
  local token="$1"
  local model_type="$2"
  local provider
  local provider_id
  local provider_revision
  local deployment
  local deployment_id
  local probe

  provider="$(ensure_provider_verified "${token}" "$(create_provider "${token}")")"
  provider_id="$(jq -er ".id" <<<"${provider}")"
  provider_revision="$(jq -er ".version" <<<"${provider}")"
  deployment="$(create_deployment "${token}" "${provider_id}" "${model_type}")"
  deployment_id="$(jq -er ".id" <<<"${deployment}")"

  if [[ "$(jq -er ".status" <<<"${deployment}")" == "DRAFT" ]]; then
    probe="$(
      probe_deployment \
        "${token}" \
        "${deployment_id}" \
        "$(jq -er ".version" <<<"${deployment}")"
    )"
    jq -e \
      '.reachable == true
        and .modelDeployment.status == "VERIFIED"
        and (.capabilities | sort) == ["CHAT", "EMBEDDING"]' \
      >/dev/null <<<"${probe}"
    deployment="$(jq -er ".modelDeployment" <<<"${probe}")"
  fi

  if [[ "$(jq -er ".status" <<<"${deployment}")" == "VERIFIED" ]]; then
    deployment="$(
      publish_deployment \
        "${token}" \
        "${deployment_id}" \
        "$(jq -er ".version" <<<"${deployment}")"
    )"
  fi

  jq -e \
    --arg provider_id "${provider_id}" \
    --argjson provider_revision "${provider_revision}" \
    --arg model_type "${model_type}" \
    '.status == "PUBLISHED"
      and .providerConnectionId == $provider_id
      and .providerConnectionRevision == $provider_revision
      and .modelType == $model_type
      and (
        ($model_type == "CHAT" and .outputLimit == 1024)
        or ($model_type == "EMBEDDING" and .embeddingDimension == 128)
      )' \
    >/dev/null <<<"${deployment}"
  printf "%s" "${deployment}"
}

store_token="$(login "demo-store")"
campus_token="$(login "demo-campus")"
store_chat="$(ensure_published_deployment "${store_token}" "CHAT")"
store_embedding="$(ensure_published_deployment "${store_token}" "EMBEDDING")"
campus_chat="$(ensure_published_deployment "${campus_token}" "CHAT")"
campus_embedding="$(ensure_published_deployment "${campus_token}" "EMBEDDING")"
store_chat_id="$(jq -er ".id" <<<"${store_chat}")"
store_embedding_id="$(jq -er ".id" <<<"${store_embedding}")"
campus_chat_id="$(jq -er ".id" <<<"${campus_chat}")"
campus_embedding_id="$(jq -er ".id" <<<"${campus_embedding}")"

store_list="$(
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${store_token}" \
    "${API_ROOT}/model-deployments"
)"
campus_list="$(
  curl --fail-with-body --silent --show-error \
    -H "Authorization: Bearer ${campus_token}" \
    "${API_ROOT}/model-deployments"
)"
jq -e \
  --arg chat_id "${store_chat_id}" \
  --arg embedding_id "${store_embedding_id}" \
  'any(.items[]; .id == $chat_id and .status == "PUBLISHED")
    and any(.items[]; .id == $embedding_id and .status == "PUBLISHED")' \
  >/dev/null <<<"${store_list}"
jq -e \
  --arg chat_id "${store_chat_id}" \
  --arg embedding_id "${store_embedding_id}" \
  'all(.items[]; .id != $chat_id and .id != $embedding_id)' \
  >/dev/null <<<"${campus_list}"
jq -e \
  --arg chat_id "${campus_chat_id}" \
  --arg embedding_id "${campus_embedding_id}" \
  'any(.items[]; .id == $chat_id and .status == "PUBLISHED")
    and any(.items[]; .id == $embedding_id and .status == "PUBLISHED")' \
  >/dev/null <<<"${campus_list}"
jq -e \
  --arg chat_id "${campus_chat_id}" \
  --arg embedding_id "${campus_embedding_id}" \
  'all(.items[]; .id != $chat_id and .id != $embedding_id)' \
  >/dev/null <<<"${store_list}"

echo "模型部署容器验收通过：门店与校园的对话、嵌入部署均已冻结修订、探测、发布并验证租户隔离"
