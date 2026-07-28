#!/usr/bin/env bash
set -Eeuo pipefail

# WP-0010：M2 真实模型验收只使用合成知识，输出不得包含 API Key、正文、回答或令牌。
readonly CONTROL_API_ROOT="${FOUNDATION_ACCEPTANCE_CONTROL_API_ROOT:-http://127.0.0.1:13000/api/v1}"
readonly QUERY_API_ROOT="${FOUNDATION_ACCEPTANCE_QUERY_API_ROOT:-http://127.0.0.1:18081/api/v1}"
readonly DEMO_PASSWORD="${FOUNDATION_ACCEPTANCE_DEMO_PASSWORD:-Demo@2026!}"
readonly PLATFORM_TENANT_CODE="${FOUNDATION_ACCEPTANCE_PLATFORM_TENANT_CODE:-demo-store}"
readonly PLATFORM_LOGIN_NAME="${FOUNDATION_ACCEPTANCE_PLATFORM_LOGIN_NAME:-platform-admin}"
readonly TENANT_CODE="${FOUNDATION_ACCEPTANCE_TENANT_CODE:-demo-store}"
readonly TENANT_LOGIN_NAME="${FOUNDATION_ACCEPTANCE_TENANT_LOGIN_NAME:-admin}"
readonly API_KEY_FILE="${FOUNDATION_M2_API_KEY_FILE:?必须指定仅本地可读的 FOUNDATION_M2_API_KEY_FILE}"
readonly MODEL_ENDPOINT="${FOUNDATION_M2_MODEL_ENDPOINT:-https://ark.cn-beijing.volces.com/api/v3}"
readonly CHAT_MODEL_CODE="${FOUNDATION_M2_CHAT_MODEL_CODE:-doubao-seed-2-1-turbo-260628}"
readonly EMBEDDING_MODEL_CODE="${FOUNDATION_M2_EMBEDDING_MODEL_CODE:-doubao-embedding-vision-251215}"
readonly CHAT_CONTEXT_WINDOW="${FOUNDATION_M2_CHAT_CONTEXT_WINDOW:-262144}"
readonly CHAT_OUTPUT_LIMIT="${FOUNDATION_M2_CHAT_OUTPUT_LIMIT:-262144}"
readonly EMBEDDING_DIMENSION="${FOUNDATION_M2_EMBEDDING_DIMENSION:-2048}"
readonly HTTP_TIMEOUT_SECONDS="${FOUNDATION_M2_HTTP_TIMEOUT_SECONDS:-30}"
readonly INDEX_TIMEOUT_SECONDS="${FOUNDATION_M2_INDEX_TIMEOUT_SECONDS:-180}"
readonly RUN_ID="m2-$(date -u +%Y%m%d%H%M%S)"
readonly KNOWLEDGE_TEXT="M2 合成验收知识：星河研发园一号楼访客中心的咖啡吧在工作日 09:00 至 18:00 提供服务。"
readonly ACCEPTANCE_QUESTION="星河研发园一号楼的咖啡吧工作日服务到几点？"

api_key=""
token=""
platform_token=""
query_response=""
stage="初始化"

cleanup() {
  unset api_key token platform_token query_response
}
trap cleanup EXIT

report_failure() {
  local status=$?
  echo "真实模型验收失败阶段：${stage}" >&2
  exit "${status}"
}
trap report_failure ERR

require_command() {
  command -v "$1" >/dev/null 2>&1 || {
    echo "缺少必要命令：$1" >&2
    exit 1
  }
}

require_nonempty_value() {
  local name="$1"
  local value="$2"
  [[ -n "${value}" ]] || {
    echo "必须指定 ${name}" >&2
    exit 1
  }
}

require_positive_integer() {
  local name="$1"
  local value="$2"
  [[ "${value}" =~ ^[1-9][0-9]*$ ]] || {
    echo "${name} 必须是正整数" >&2
    exit 1
  }
}

request() {
  local method="$1"
  local path="$2"
  local token_value="$3"
  local idempotency_key="${4:-}"
  local payload="${5:-}"
  local -a headers=(
    -H "Accept: application/json"
    -H "Content-Type: application/json"
  )
  if [[ -n "${token_value}" ]]; then
    headers+=(-H "Authorization: Bearer ${token_value}")
  fi
  if [[ -n "${idempotency_key}" ]]; then
    headers+=(-H "Idempotency-Key: ${idempotency_key}")
  fi
  if [[ -n "${payload}" ]]; then
    curl --fail-with-body --silent --show-error --max-time "${HTTP_TIMEOUT_SECONDS}" \
      -X "${method}" "${headers[@]}" --data-binary @- \
      "${CONTROL_API_ROOT}${path}" <<<"${payload}"
  else
    curl --fail-with-body --silent --show-error --max-time "${HTTP_TIMEOUT_SECONDS}" \
      -X "${method}" "${headers[@]}" "${CONTROL_API_ROOT}${path}"
  fi
}

login() {
  local tenant_code="$1"
  local login_name="$2"
  local payload
  payload="$(jq -n --arg tenantCode "${tenant_code}" --arg loginName "${login_name}" --arg password "${DEMO_PASSWORD}" \
    '{tenantCode: $tenantCode, loginName: $loginName, password: $password}')"
  request POST "/auth/sessions" "" "" "${payload}" | jq -er '.tokenValue'
}

create_catalog_entry() {
  local model_type="$1"
  local model_code="$2"
  local context_window="$3"
  local output_limit="$4"
  local embedding_dimension="$5"
  local payload entry published existing
  existing="$(request GET "/controlled-model-catalog?pageNumber=1&pageSize=100" "${platform_token}" | \
    jq -er --arg endpoint "${MODEL_ENDPOINT}" --arg model_code "${model_code}" --arg model_type "${model_type}" \
      'first(.items[] | select(.providerKind == "OPENAI_COMPATIBLE" and .endpoint == $endpoint
        and .modelCode == $model_code and .modelType == $model_type and .status == "PUBLISHED")) | .id' || true)"
  if [[ -n "${existing}" ]]; then
    printf '%s\n' "${existing}"
    return 0
  fi
  payload="$(jq -n \
    --arg endpoint "${MODEL_ENDPOINT}" \
    --arg modelCode "${model_code}" \
    --arg modelType "${model_type}" \
    --argjson contextWindow "${context_window}" \
    --argjson outputLimit "${output_limit}" \
    --argjson embeddingDimension "${embedding_dimension}" \
    '{providerKind: "OPENAI_COMPATIBLE", endpoint: $endpoint, modelCode: $modelCode,
      modelType: $modelType, contextWindow: $contextWindow, outputLimit: $outputLimit,
      embeddingDimension: $embeddingDimension}')"
  entry="$(request POST "/controlled-model-catalog" "${platform_token}" "" "${payload}")"
  published="$(request POST "/controlled-model-catalog/$(jq -er '.id' <<<"${entry}")/publish" "${platform_token}")"
  jq -er --arg model_type "${model_type}" \
    'select(.status == "PUBLISHED" and .modelType == $model_type) | .id' \
    <<<"${published}"
}

create_active_personal_configuration() {
  local catalog_entry_id="$1"
  local model_type="$2"
  local payload created activated existing
  existing="$(request GET "/model-configurations?pageNumber=1&pageSize=100" "${token}" | \
    jq -er --arg catalog_entry_id "${catalog_entry_id}" --arg model_type "${model_type}" \
      'first(.items[] | select(.scope == "PERSONAL" and .catalogEntryId == $catalog_entry_id
        and .modelType == $model_type and .status == "ACTIVE" and .credentialConfigured == true)) | .id' || true)"
  if [[ -n "${existing}" ]]; then
    printf '%s\n' "${existing}"
    return 0
  fi
  payload="$(jq -n \
    --arg displayName "M2 ${model_type} 真实验收 ${RUN_ID}" \
    --arg catalogEntryId "${catalog_entry_id}" \
    --arg apiKey "${api_key}" \
    '{scope: "PERSONAL", displayName: $displayName, catalogEntryId: $catalogEntryId, apiKey: $apiKey}')"
  created="$(request POST "/model-configurations" "${token}" "" "${payload}")"
  unset payload
  activated="$(request POST "/model-configurations/$(jq -er '.id' <<<"${created}")/activate" "${token}")"
  jq -er \
    'select(.status == "ACTIVE" and .credentialConfigured == true) | .id' \
    <<<"${activated}"
}

create_knowledge_space() {
  local payload response
  payload="$(jq -n --arg name "M2 真实模型验收 ${RUN_ID}" \
    '{name: $name, description: "仅含合成文本的 M2 真实模型研发验收"}')"
  response="$(request POST "/knowledge-spaces" "${token}" "${RUN_ID}-space" "${payload}")"
  jq -er '.id' <<<"${response}"
}

publish_synthetic_source() {
  local knowledge_space_id="$1"
  local payload source_id uploaded
  payload="$(jq -n --arg knowledgeSpaceId "${knowledge_space_id}" --arg name "M2 合成文本 ${RUN_ID}" \
    '{knowledgeSpaceId: $knowledgeSpaceId, name: $name}')"
  source_id="$(request POST "/data-sources" "${token}" "${RUN_ID}-source" "${payload}" | jq -er '.id')"
  uploaded="$(curl --fail-with-body --silent --show-error --max-time "${HTTP_TIMEOUT_SECONDS}" \
    -H "Authorization: Bearer ${token}" \
    -H "Idempotency-Key: ${RUN_ID}-content" \
    -H "Content-Type: text/plain; charset=UTF-8" \
    --data-binary "${KNOWLEDGE_TEXT}" \
    "${CONTROL_API_ROOT}/data-sources/${source_id}/content?knowledgeSpaceId=${knowledge_space_id}&expectedVersion=0")"
  jq -e '.status == "CONTENT_READY" and .version == 1' >/dev/null <<<"${uploaded}"
  request POST "/data-sources/${source_id}/publish" "${token}" "${RUN_ID}-source-publish" \
    "$(jq -n --arg knowledgeSpaceId "${knowledge_space_id}" '{knowledgeSpaceId: $knowledgeSpaceId, expectedVersion: 1}')" |
    jq -e '.status == "PUBLISHED" and .version == 2' >/dev/null
}

wait_for_ready_index() {
  local knowledge_space_id="$1"
  local index_id="$2"
  local deadline=$((SECONDS + INDEX_TIMEOUT_SECONDS))
  local page status
  while (( SECONDS < deadline )); do
    page="$(request GET "/knowledge-spaces/${knowledge_space_id}/index-versions" "${token}")"
    status="$(jq -er --arg index_id "${index_id}" '.items[] | select(.id == $index_id) | .status' <<<"${page}")"
    case "${status}" in
      READY) return 0 ;;
      FAILED) echo "真实嵌入索引构建失败" >&2; return 1 ;;
    esac
    sleep 2
  done
  echo "等待真实嵌入索引超时" >&2
  return 1
}

create_active_index() {
  local knowledge_space_id="$1"
  local embedding_configuration_id="$2"
  local created current published
  created="$(request POST "/knowledge-spaces/${knowledge_space_id}/index-versions" "${token}" "${RUN_ID}-index" \
    "$(jq -n --arg embeddingModelConfigurationId "${embedding_configuration_id}" \
      '{embeddingModelConfigurationId: $embeddingModelConfigurationId}')")"
  local index_id
  index_id="$(jq -er '.id' <<<"${created}")"
  wait_for_ready_index "${knowledge_space_id}" "${index_id}"
  current="$(request GET "/knowledge-spaces/${knowledge_space_id}/index-versions" "${token}" | \
    jq -er --arg index_id "${index_id}" '.items[] | select(.id == $index_id)')"
  published="$(request POST "/knowledge-spaces/${knowledge_space_id}/index-versions/${index_id}/publish" "${token}" "${RUN_ID}-index-publish" \
    "$(jq -n --argjson expectedVersion "$(jq -er '.version' <<<"${current}")" '{expectedVersion: $expectedVersion}')")"
  jq -er 'select(.status == "ACTIVE") | .id' <<<"${published}"
}

create_published_assistant_version() {
  local knowledge_space_id="$1"
  local chat_configuration_id="$2"
  local embedding_configuration_id="$3"
  local assistant version published
  assistant="$(request POST "/assistants" "${token}" "${RUN_ID}-assistant" \
    "$(jq -n --arg displayName "M2 真实模型助手 ${RUN_ID}" '{displayName: $displayName}')")"
  version="$(request POST "/assistants/$(jq -er '.id' <<<"${assistant}")/versions" "${token}" "${RUN_ID}-assistant-version" \
    "$(jq -n \
      --arg chatModelConfigurationId "${chat_configuration_id}" \
      --arg embeddingModelConfigurationId "${embedding_configuration_id}" \
      --arg knowledgeSpaceId "${knowledge_space_id}" \
      '{chatModelConfigurationId: $chatModelConfigurationId,
        embeddingModelConfigurationId: $embeddingModelConfigurationId,
        knowledgeSpaceId: $knowledgeSpaceId,
        systemInstruction: "只依据引用的合成知识回答。",
        maxOutputTokens: 128, retrievalLimit: 3, minimumScore: 0,
        citationPolicy: "REQUIRED", refusalPolicy: "INSUFFICIENT_EVIDENCE"}')")"
  published="$(request POST "/assistant-versions/$(jq -er '.id' <<<"${version}")/publish" "${token}" "${RUN_ID}-assistant-publish" \
    "$(jq -n --argjson expectedVersion "$(jq -er '.version' <<<"${version}")" '{expectedVersion: $expectedVersion}')")"
  jq -er 'select(.status == "PUBLISHED") | .id' <<<"${published}"
}

query_real_model() {
  local assistant_version_id="$1"
  query_response="$(jq -n \
    --arg assistantVersionId "${assistant_version_id}" \
    --arg question "${ACCEPTANCE_QUESTION}" \
    '{assistantVersionId: $assistantVersionId, question: $question}' |
    curl --fail-with-body --silent --show-error --max-time "${HTTP_TIMEOUT_SECONDS}" \
      -H "Authorization: Bearer ${token}" \
      -H "Idempotency-Key: ${RUN_ID}-query" \
      -H "Content-Type: application/json" \
      --data-binary @- "${QUERY_API_ROOT}/query-runs")"
  jq -e '.status == "ANSWERED" and (.answer | type == "string") and (.citations | length > 0)' \
    >/dev/null <<<"${query_response}"
}

require_command curl
require_command jq
require_command shasum
require_command stat
require_command grep
[[ -f "${API_KEY_FILE}" && -r "${API_KEY_FILE}" ]] || {
  echo "API Key 文件不可读：${API_KEY_FILE}" >&2
  exit 1
}
api_key_file_mode="$(stat -f '%Lp' "${API_KEY_FILE}" 2>/dev/null || stat -c '%a' "${API_KEY_FILE}")"
if (( (8#${api_key_file_mode} & 8#077) != 0 )); then
  echo "API Key 文件权限过宽，禁止组或其他用户读取：${API_KEY_FILE}" >&2
  exit 1
fi
api_key_candidates=()
while IFS= read -r candidate; do
  api_key_candidates+=("${candidate}")
done < <(LC_ALL=C grep -Eo '[[:alnum:]][[:alnum:]._-]{19,}' "${API_KEY_FILE}" || true)
if (( ${#api_key_candidates[@]} != 1 )); then
  echo "API Key 文件必须且只能包含一个长格式密钥值" >&2
  exit 1
fi
api_key="${api_key_candidates[0]}"
unset api_key_candidates
require_nonempty_value FOUNDATION_M2_CHAT_MODEL_CODE "${CHAT_MODEL_CODE}"
require_nonempty_value FOUNDATION_M2_EMBEDDING_MODEL_CODE "${EMBEDDING_MODEL_CODE}"
require_positive_integer FOUNDATION_M2_CHAT_CONTEXT_WINDOW "${CHAT_CONTEXT_WINDOW}"
require_positive_integer FOUNDATION_M2_CHAT_OUTPUT_LIMIT "${CHAT_OUTPUT_LIMIT}"
require_positive_integer FOUNDATION_M2_EMBEDDING_DIMENSION "${EMBEDDING_DIMENSION}"

stage="登录平台管理员"
platform_token="$(login "${PLATFORM_TENANT_CODE}" "${PLATFORM_LOGIN_NAME}")"
stage="登录租户管理员"
token="$(login "${TENANT_CODE}" "${TENANT_LOGIN_NAME}")"
stage="发布或复用对话模型目录"
chat_catalog_id="$(create_catalog_entry CHAT "${CHAT_MODEL_CODE}" "${CHAT_CONTEXT_WINDOW}" "${CHAT_OUTPUT_LIMIT}" null)"
stage="发布或复用嵌入模型目录"
embedding_catalog_id="$(create_catalog_entry EMBEDDING "${EMBEDDING_MODEL_CODE}" 8192 null "${EMBEDDING_DIMENSION}")"
stage="创建并启用对话模型配置"
chat_configuration_id="$(create_active_personal_configuration "${chat_catalog_id}" CHAT)"
stage="创建并启用嵌入模型配置"
embedding_configuration_id="$(create_active_personal_configuration "${embedding_catalog_id}" EMBEDDING)"
stage="创建合成知识空间"
knowledge_space_id="$(create_knowledge_space)"
stage="发布合成知识"
publish_synthetic_source "${knowledge_space_id}"
stage="构建真实嵌入索引"
index_version_id="$(create_active_index "${knowledge_space_id}" "${embedding_configuration_id}")"
stage="发布受控助手版本"
assistant_version_id="$(create_published_assistant_version \
  "${knowledge_space_id}" "${chat_configuration_id}" "${embedding_configuration_id}")"
stage="执行带引用查询"
query_real_model "${assistant_version_id}"

jq -n \
  --arg runId "${RUN_ID}" \
  --arg chatModel "${CHAT_MODEL_CODE}" \
  --arg embeddingModel "${EMBEDDING_MODEL_CODE}" \
  --arg endpointDigest "$(printf '%s' "${MODEL_ENDPOINT}" | shasum -a 256 | awk '{print $1}')" \
  --arg assistantVersionDigest "$(printf '%s' "${assistant_version_id}" | shasum -a 256 | awk '{print $1}')" \
  --arg indexVersionDigest "$(printf '%s' "${index_version_id}" | shasum -a 256 | awk '{print $1}')" \
  --arg answerDigest "$(jq -r '.answer' <<<"${query_response}" | shasum -a 256 | awk '{print $1}')" \
  --argjson citationCount "$(jq -er '.citations | length' <<<"${query_response}")" \
  --argjson elapsedSeconds "${SECONDS}" \
  '{runId: $runId, chatModel: $chatModel, embeddingModel: $embeddingModel,
    endpointDigest: $endpointDigest, assistantVersionDigest: $assistantVersionDigest,
    indexVersionDigest: $indexVersionDigest, answerDigest: $answerDigest,
    citationCount: $citationCount, elapsedSeconds: $elapsedSeconds, status: "PASSED"}'
