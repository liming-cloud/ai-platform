# 04 Provider Model Gateway 详细设计

Stage: module-design-detail  
Category: provider-model-gateway  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, product engineer, senior backend engineer, senior frontend engineer, UI engineer  
Approval: agent-approved  
Inputs: `docs/45-module-and-service-design.md`, `docs/50-delivery-plan.md`  
Outputs: S4 Provider Catalog 与后续 S5 Model Gateway 实现边界  
Depends On: `docs/45-details/01-backend-code-organization.md`, `docs/45-details/02-platform-infrastructure-design.md`  
Load When: provider-model-gateway-development  
Next: S5 统一模型调用

## 目标边界

S4 只实现平台模型选项目录、租户模型供应商配置、租户模型目录配置、API Key 加密保存、OpenAI / OpenAI Compatible 连接测试和调用记录。S4 不实现知识库问答、tool runtime、完整成本报表或多策略路由。

业务模块路径固定为：

```text
backend/business/ai-platform-modules/model
```

Spring AI 适配路径固定为：

```text
backend/infrastructure/ai-platform-ai
```

Controller、application service、domain 不得直接注入 Spring AI 类型；只允许通过 `AiGatewayFacade` 等平台封装端口调用。`AiGatewayFacade` 只负责门面分发，具体厂商调用必须由 `AiProviderType` 枚举、`AiGatewayClientFactory` 和 `AiGatewayClient` 实现完成；禁止在 facade 或业务 service 中写 OpenAI 专用分支。

## 领域模型

| 对象 | 职责 | 不允许 |
| --- | --- | --- |
| PlatformProviderType | 平台维护的协议类型、默认 baseUrl、展示名和状态 | 保存租户密钥或作为租户资源 |
| PlatformModelOption | 平台维护的模型编码、能力类型和默认参数 | 保存租户密钥或替代租户启用配置 |
| Provider | 租户级供应商配置、协议类型、baseUrl、预算、状态 | 决定平台模型选项、暴露明文 key |
| Credential | API Key 密文、last4、轮换信息 | 在响应、日志、trace、cache 中出现明文 |
| Model | 厂家型号、能力类型、上下文窗口、输出 token、向量维度 | 用名称替代能力校验 |
| Settings | temperature、topP、topK、惩罚系数、reasoning effort 等默认参数 | 不按 provider capability schema 校验即透传 |
| Invocation | 连接测试和模型调用记录 | 保存 prompt/completion 明文作为默认观测 |

首期能力类型：

- `CHAT`
- `EMBEDDING`
- `RERANK`
- `IMAGE`
- `AUDIO`
- `MODERATION`

## 数据库边界

S4 必须落库：

- `platform_provider_type`
- `platform_model_option`
- `model_provider`
- `model_catalog`
- `model_invocation_record`

所有表主键统一为 `id BIGINT PRIMARY KEY`，由平台雪花 Long ID 生成。`tenant_id`、`provider_id`、`model_id`、`provider_type_id`、`model_option_id` 是 Long 型关联键；`provider_name`、`provider_type`、`model_code` 只能作为业务唯一键或配置键，不得作为主键或跨表关联字段。

`platform_provider_type` 和 `platform_model_option` 是平台全局目录表，由平台管理员维护或 bootstrap seed 初始化；它们必须从 MyBatis-Plus tenant interceptor ignore list 显式排除。`model_provider` 和 `model_catalog` 是租户资源表，必须带 `tenant_id`。

`model_provider.api_key_ciphertext` 保存加密密文，`api_key_last4` 只用于页面确认当前配置。数据库不得出现 API Key 明文列。

`deploy/.env` 只作为本地 bootstrap 输入和部署密钥来源。API Key 的长期产品事实源是数据库密文，而不是环境变量。首期兼容：

- `AI_PLATFORM_OPENAI_API_KEY`
- `OPEN_AI_APIKEY`

## 首期模型选项目录

用户确认的首期 OpenAI 配置：

- providerType：`OPENAI`
- baseUrl：官方默认，数据库保存为 `OFFICIAL_DEFAULT`
- chatModel：`o3`
- budgetUsd：`5`
- apiKey：通过配置功能保存到数据库，数据库中保持加密

`o3` 是 reasoning/chat 模型，可配置为 `CHAT`。`o3` 不可配置为 `EMBEDDING`。OpenAI embedding 模型应使用 `text-embedding-3-small` 或 `text-embedding-3-large` 等 embedding 能力模型。

硅基流动按 OpenAI Compatible 接入，平台目录使用：

- providerType：`OPENAI_COMPATIBLE`
- baseUrl：`https://api.siliconflow.cn`
- chatModel：`tencent/Hunyuan-MT-7B`
- capabilityType：`CHAT`
- 默认参数：`contextWindow=33792`、`maxOutputTokens=33792`、`temperature=0.2`、`topP=0.7`

平台管理必须提供“模型基础配置”页面和 API，用于维护 `platform_provider_type`、`platform_model_option`。平台管理员维护完成后，租户模型配置页面才可以调用 `GET /api/model-providers/options` 读取启用状态的目录项并下拉选择。

前端租户模型配置页面不得让租户自由输入 provider type、baseUrl、modelCode 或 capabilityType；必须读取平台目录后下拉选择。租户只输入租户级密钥、预算和必要覆盖参数。

## 参数校验

参数保存前必须校验：

- `contextWindow`、`maxOutputTokens`、`embeddingDimensions` 必须大于 0。
- `temperature` 范围为 0 到 2。
- `topP` 范围为 0 到 1。
- `frequencyPenalty`、`presencePenalty` 范围为 -2 到 2。
- OpenAI 首期不支持 `topK`，配置 OpenAI 模型时出现 `topK` 必须拒绝。
- `EMBEDDING` 模型必须配置 `embeddingDimensions`。
- `CHAT` 模型不得使用 `text-embedding-*` 型号。
- `EMBEDDING` 模型不得使用非 embedding 型号。

## API 合同

首期 API：

- `GET /api/admin/model-catalog`
- `POST /api/admin/model-catalog/provider-types`
- `POST /api/admin/model-catalog/model-options`
- `GET /api/model-providers`
- `GET /api/model-providers/options`
- `POST /api/model-providers`
- `POST /api/model-providers/test`
- `POST /api/model-invocations/chat`

平台目录 API 必须使用 Sa-Token 权限控制：

- `model:platform-catalog:read`：读取平台模型基础配置。
- `model:platform-catalog:write`：维护供应商类型和模型选项。

租户模型配置 API 必须使用：

- `model:tenant-config:read`
- `model:tenant-config:write`
- `model:invocation:execute`：执行正式模型调用。

所有响应必须使用统一 `ApiResponse`。响应只允许返回 `apiKeyLast4`，不得返回明文 API Key 或密文。

接口响应中的 `providerId`、`modelId`、`tenantId` 可作为字符串返回给前端，避免 JavaScript 大整数精度损失；数据库和后端领域/持久化模型仍必须使用 Long。

S5.1 正式调用响应必须返回 `traceId`、`latencyMs`、`promptTokens`、`completionTokens`、`totalTokens`、`costUsd` 或 `costMissingReason`。`model_invocation_record` 只保存 trace、token、字符数、成本和错误摘要，不保存 prompt/completion 明文。

## 测试要求

S4 至少包含：

- domain/application test：`o3` 可作为 `CHAT` 保存。
- domain/application test：`o3` 作为 `EMBEDDING` 被拒绝。
- application test：API Key 加密保存，DTO/response 不含明文。
- application test：连接测试通过 `AiGatewayFacade`，并写 `model_invocation_record`。
- application test：平台管理员保存 provider type / model option 后，租户 `GET /api/model-providers/options` 可读取新启用目录项。
- infrastructure test：`AiGatewayClientFactory` 必须按 `AiProviderType` 选择对应 client，未实现的 provider type 必须明确拒绝。
- architecture test：Flyway migration 和 PO 必须持续验证 `id BIGINT PRIMARY KEY` 与 Long 关联键。
- boot smoke：登录后可读取/维护平台模型目录、创建租户 provider、查询 provider，统一响应和权限控制可用。
- frontend test：平台管理模型基础配置保存后，租户模型配置页从 `GET /api/model-providers/options` 渲染下拉项，保存 provider 使用平台目录派生的 baseUrl、modelCode 和 capabilityType，不以 mock 数据作为完成态。
- runtime smoke：真实 OpenAI Key 下完成 provider test，预算控制在 5 美元以内。

## 文档与控制面

S4 完成后必须更新：

- `docs/45-details/00-index.md`
- `docs/50-delivery-plan.md`
- `docs/60-implementation-notes.md`
- `scripts/generate_context_pack.py`
- `scripts/harness_repo_preflight.py`
- `scripts/harness_doc_guard.py`

后续 S5 只能在 S4 证据保持绿色后推进统一模型调用、quota、retry、cost 和 trace，不得把知识库或 tool 范围合并进 S5。
