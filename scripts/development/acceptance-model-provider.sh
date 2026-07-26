#!/usr/bin/env bash
set -euo pipefail

readonly API_ROOT="http://127.0.0.1:13000/api/v1"
readonly DEMO_PASSWORD="Demo@2026!"
readonly CREDENTIAL_REFERENCE="secret://demo/provider"

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
      "${API_ROOT}/auth/sessions" |
    jq -er ".tokenValue"
}

create_connection() {
  local token="$1"
  jq -n \
    --arg credential_reference "${CREDENTIAL_REFERENCE}" \
    '{
      providerKind: "DETERMINISTIC",
      displayName: "验收确定性模型",
      endpoint: "memory://deterministic",
      region: "本地",
      authenticationType: "API_KEY",
      credentialReference: $credential_reference
    }' |
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: acceptance-model-provider-v1" \
      -H "Content-Type: application/json" \
      --data-binary @- \
      "${API_ROOT}/model-providers"
}

probe_connection() {
  local token="$1"
  local connection_id="$2"
  local expected_version="$3"
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
      "${API_ROOT}/model-providers/${connection_id}/probe"
}

verify_list_is_redacted() {
  local token="$1"
  local response
  response="$(
    curl --fail-with-body --silent --show-error \
      -H "Authorization: Bearer ${token}" \
      "${API_ROOT}/model-providers"
  )"
  if rg -q "credentialReference|${CREDENTIAL_REFERENCE}" <<<"${response}"; then
    echo "供应商连接列表泄露了凭据引用" >&2
    return 1
  fi
}

token="$(login)"
created="$(create_connection "${token}")"
connection_id="$(jq -er ".id" <<<"${created}")"
expected_version="$(jq -er ".version" <<<"${created}")"
probe="$(
  probe_connection "${token}" "${connection_id}" "${expected_version}"
)"

jq -e \
  '.reachable == true
    and .providerConnection.status == "VERIFIED"
    and (.capabilities | sort) == ["CHAT", "EMBEDDING"]' \
  >/dev/null <<<"${probe}"
verify_list_is_redacted "${token}"

echo "供应商连接容器验收通过：已创建、探测、列表脱敏"
