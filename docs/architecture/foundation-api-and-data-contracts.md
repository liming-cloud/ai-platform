# 可信 RAG 基座接口与数据契约

## 1. 通用约定

- 公共接口前缀为 `/api/v1`。
- 接口只暴露平台契约，不暴露 Spring AI、Sa-Token、MyBatis-Plus、数据库或厂商类。
- 时间使用带时区的 ISO-8601 UTC 字符串。
- 标识为不透明字符串，不包含租户、表名或序列含义。
- 金额使用整数最小货币单位和币种，禁止浮点数。
- Token、数量和版本使用整数。
- 写操作接收 `Idempotency-Key`。
- 更新和发布使用 `If-Match` 或等价修订号。
- 列表使用稳定排序、页码分页和总数契约。
- 每个响应返回或传播 `traceId`。

## 2. 标识契约

- 领域资源标识由平台雪花生成器产生，数据库使用 64 位整数。
- HTTP 和事件契约将资源标识序列化为十进制字符串，避免浏览器整数精度损失。
- 标识不编码租户、资源类型或授权信息，服务端必须同时验证租户和资源范围。
- 追踪标识是诊断标识，不属于领域资源标识，也不能作为授权依据。

## 3. 错误信封

```json
{
  "code": "MODEL_RUNTIME.RESOURCE.0001",
  "message": "没有满足要求的已授权模型部署",
  "traceId": "trc_01...",
  "details": {
    "requiredCapabilities": ["STRUCTURED_OUTPUT"],
    "rejectedCandidates": [
      {
        "deploymentId": "mdl_01...",
        "reason": "DATA_RESIDENCY_MISMATCH"
      }
    ]
  }
}
```

规则：

- `code` 是稳定、可测试的机器码。
- `message` 可本地化，不包含密钥、内部地址、SQL、堆栈或未授权资源信息。
- `details` 只包含调用方有权了解的结构化信息。
- 身份不存在、资源不存在和资源无权访问按策略返回不可区分结果。

## 4. 页码分页

请求：

```text
GET /api/v1/knowledge-spaces?pageNumber=1&pageSize=20
```

响应：

```json
{
  "items": [],
  "pageNumber": 1,
  "pageSize": 20,
  "totalElements": 0,
  "totalPages": 0,
  "hasPrevious": false,
  "hasNext": false
}
```

页码从 1 开始，默认每页 20 条，单页最多 100 条，默认最大偏移量为 100000。
排序由接口固定，前端不得传递 SQL 字段或排序片段。超出最大偏移量时返回稳定的
`COMMON.REQUEST` 错误。SSE 事件重连仍使用单调事件序号和 `Last-Event-ID`，不属于分页。

## 5. 主要资源接口

### 5.1 组织、权限与凭据

- `POST/GET /api/v1/tenants`
- `POST/GET /api/v1/members`
- `POST/GET /api/v1/access-policies`
- `POST /api/v1/access-policies/{id}/publish`
- `POST/GET /api/v1/device-credentials`
- `POST /api/v1/device-credentials/{id}/revoke`

### 5.2 数据与知识

- `POST/GET /api/v1/data-sources`
- `POST /api/v1/data-sources/{id}/probe`
- `POST /api/v1/data-sources/{id}/sync-runs`
- `GET /api/v1/data-sources/{id}/sync-runs`
- `POST/GET /api/v1/dataset-schemas`
- `POST /api/v1/dataset-schemas/{id}/publish`
- `POST/GET /api/v1/knowledge-spaces`
- `POST /api/v1/knowledge-spaces/{id}/index-versions`
- `POST /api/v1/index-versions/{id}/publish`

### 5.3 模型治理

- `POST/GET /api/v1/model-providers`
- `POST /api/v1/model-providers/{id}/probe`
- `POST/GET /api/v1/model-deployments`
- `POST /api/v1/model-deployments/{id}/probe`
- `POST /api/v1/model-deployments/{id}/publish`
- `GET /api/v1/model-capabilities`
- `POST/GET /api/v1/model-routing-policies`
- `POST /api/v1/model-routing-policies/{id}/simulate`
- `GET /api/v1/model-usage`
- `GET /api/v1/model-health`

### 5.4 助手、查询和评测

- `POST/GET /api/v1/assistants`
- `POST /api/v1/assistants/{id}/versions`
- `POST /api/v1/assistant-versions/{id}/evaluate`
- `POST /api/v1/assistant-versions/{id}/publish`
- `POST /api/v1/query-runs`
- `GET /api/v1/query-runs/{id}`
- `GET /api/v1/query-runs/{id}/events`
- `POST /api/v1/query-runs/{id}/cancel`
- `POST /api/v1/query-runs/{id}/feedback`
- `POST/GET /api/v1/evaluation-runs`
- `GET /api/v1/audit-records`
- `GET /api/v1/usage`

## 6. 查询契约

### 6.1 创建查询

```json
{
  "assistantVersionId": "asv_01...",
  "channelId": "chn_01...",
  "conversationId": "cnv_01...",
  "input": {
    "text": "商品 A 在哪里，价格是多少？",
    "attachments": []
  },
  "requestOverrides": {
    "responseLanguage": "zh-CN"
  }
}
```

请求覆盖只能使用助手版本明确允许的字段和范围，不能改变租户、知识范围、模型厂商、权限、驻留或安全上限。

接受响应：

```json
{
  "queryRunId": "qry_01...",
  "status": "ACCEPTED",
  "eventsUrl": "/api/v1/query-runs/qry_01.../events",
  "traceId": "trc_01..."
}
```

### 6.2 查询结果

```json
{
  "id": "qry_01...",
  "status": "COMPLETED",
  "answer": {
    "text": "商品 A 售价 39 元，位于 4 号通道 B 货架。",
    "citations": [
      {
        "citationId": "cit_01...",
        "claimRange": {"start": 0, "end": 28},
        "evidenceIds": ["evd_01...", "evd_02..."],
        "verificationStatus": "VERIFIED"
      }
    ]
  },
  "evidence": [
    {
      "id": "evd_01...",
      "sourceId": "src_01...",
      "sourceVersion": "7",
      "locator": {"type": "DATASET_ROW", "value": "sku:A"},
      "title": "门店商品数据",
      "freshnessAt": "2026-07-26T09:56:00Z",
      "authorizationDecisionId": "poldec_01..."
    }
  ],
  "execution": {
    "policyVersion": "12",
    "indexVersions": ["idx_01..."],
    "modelDeploymentId": "mdl_01...",
    "fallbackReason": null,
    "latencyMs": 2800
  },
  "usage": {
    "inputTokens": 820,
    "outputTokens": 96,
    "currency": "CNY",
    "costMinor": 12
  },
  "traceId": "trc_01..."
}
```

普通渠道响应不返回内部提示词、完整检索候选、被拒绝资源、明文策略或供应商错误。诊断接口按独立权限返回受控视图。

### 6.3 流式事件

`GET /api/v1/query-runs/{id}/events` 使用 SSE。每个事件包含递增序号：

```text
id: 7
event: answer.delta
data: {"queryRunId":"qry_01...","sequence":7,"delta":"位于 4 号通道"}
```

事件类型：

- `query.accepted`
- `query.planning`
- `retrieval.started`
- `retrieval.completed`
- `generation.started`
- `answer.delta`
- `citation.verified`
- `query.completed`
- `query.refused`
- `query.denied`
- `query.failed`
- `query.cancelled`

终止事件只允许出现一次。重连使用 `Last-Event-ID`，服务端从可用保留窗口继续；窗口外返回稳定错误码并允许读取最终快照。

## 7. 供应商连接与模型部署

### 7.1 创建供应商连接

```json
{
  "providerKind": "OPENAI_COMPATIBLE",
  "displayName": "企业模型网关",
  "endpoint": "https://models.example.cn/v1",
  "region": "CN",
  "authentication": {
    "type": "BEARER_TOKEN",
    "credentialReference": "vault://tenant-a/model-gateway"
  },
  "networkPolicyId": "netpol_cn_public_v1"
}
```

响应不得包含密钥值。`endpoint` 在保存、探测和运行时 DNS 解析后重复执行出站策略。

探测结果：

```json
{
  "providerId": "prv_01...",
  "revision": 3,
  "status": "READY",
  "checks": [
    {"type": "DNS", "status": "PASSED", "latencyMs": 12},
    {"type": "TLS", "status": "PASSED", "latencyMs": 74},
    {"type": "AUTHENTICATION", "status": "PASSED", "latencyMs": 182},
    {"type": "MINIMAL_INVOCATION", "status": "PASSED", "latencyMs": 340}
  ],
  "probedAt": "2026-07-26T10:00:00Z"
}
```

### 7.2 模型类型

- `CHAT`
- `EMBEDDING`
- `IMAGE_GENERATION`
- `MODERATION`
- `TRANSCRIPTION`
- `TEXT_TO_SPEECH`
- `RERANK`

推理属于 Chat 能力，多模态理解属于 Chat 输入能力，工具与 MCP 属于调用能力，不重复定义为模型类型。

### 7.3 能力描述

```json
{
  "streaming": true,
  "toolCalling": true,
  "structuredOutput": true,
  "vision": false,
  "audioInput": false,
  "jsonSchema": true,
  "chatMemoryCompatible": true,
  "maximumContext": 128000,
  "maximumOutput": 8192,
  "embeddingDimension": null,
  "batchEmbedding": false,
  "moderationCategories": [],
  "usageMetadata": true,
  "reasoningOptions": ["EFFORT"]
}
```

未探测或未声明的能力按不支持处理。

### 7.4 参数

便携参数：

```json
{
  "temperature": 0.2,
  "topP": 0.9,
  "topK": null,
  "maxTokens": 1024,
  "stopSequences": [],
  "frequencyPenalty": 0,
  "presencePenalty": 0
}
```

厂商参数：

```json
{
  "schema": "openai-chat-options/v1",
  "values": {
    "reasoning_effort": "medium"
  }
}
```

厂商参数经过结构、白名单、范围和权限校验，保存后形成不可变修订。

## 8. 路由模拟

请求：

```json
{
  "tenantId": "ten_01...",
  "useCase": "STORE_GUIDANCE",
  "requiredModelType": "CHAT",
  "requiredCapabilities": ["STREAMING", "STRUCTURED_OUTPUT"],
  "dataResidency": ["CN"],
  "estimatedInputTokens": 1600,
  "estimatedOutputTokens": 300
}
```

响应：

```json
{
  "selectedDeploymentId": "mdl_01...",
  "fallbackDeploymentIds": ["mdl_02..."],
  "estimatedLatencyRangeMs": {"min": 900, "max": 2400},
  "estimatedCost": {"currency": "CNY", "minMinor": 8, "maxMinor": 20},
  "rejectedCandidates": [
    {
      "deploymentId": "mdl_03...",
      "reason": "DATA_RESIDENCY_MISMATCH"
    }
  ],
  "policyRevision": 5
}
```

模拟只解释当前策略，不预留容量，不保证未来健康状态。

## 9. 受控结构化查询语言

模型只能生成平台查询语言，不能生成可执行 SQL：

```json
{
  "datasetSchemaVersionId": "dss_01...",
  "select": ["productName", "price", "aisle", "shelf"],
  "filter": {
    "and": [
      {"field": "storeId", "operator": "EQ", "value": "store-001"},
      {"field": "productName", "operator": "MATCH", "value": "商品 A"}
    ]
  },
  "orderBy": [
    {"field": "sales30d", "direction": "DESC"}
  ],
  "limit": 10
}
```

编译规则：

- 字段、运算符、排序和聚合必须在已发布结构白名单内。
- 租户、资源和行权限由服务端强制注入，调用方不能覆盖。
- 字段遮蔽在数据库返回后和证据生成前再次执行。
- 查询复杂度、扫描量、返回数和执行时间有硬上限。
- 参数化编译，不拼接标识或值。
- 每次执行记录结构版本、策略版本、规范化查询摘要和行数，不记录敏感值。

支持的首批操作：

- 比较：`EQ`、`NE`、`GT`、`GTE`、`LT`、`LTE`
- 集合：`IN`
- 文本：`MATCH`、`PREFIX`
- 空值：`IS_NULL`、`IS_NOT_NULL`
- 逻辑：`AND`、`OR`
- 排序和限制

首周不支持任意关联、子查询、脚本、用户表达式或数据库函数。

## 10. 统一证据元数据

非结构化和结构化证据统一包含：

```json
{
  "tenantId": "ten_01...",
  "knowledgeSpaceId": "ksp_01...",
  "sourceId": "src_01...",
  "sourceVersion": "7",
  "indexVersionId": "idx_01...",
  "contentType": "DATASET_ROW",
  "locator": {},
  "title": "门店商品数据",
  "language": "zh-CN",
  "effectiveFrom": "2026-07-01T00:00:00Z",
  "effectiveTo": null,
  "freshnessAt": "2026-07-26T09:56:00Z",
  "classification": "INTERNAL",
  "ownerOrganizationId": "org_01...",
  "authorizationTags": ["store:001"],
  "checksum": "sha256:..."
}
```

召回、重排、引用、清除和审计都使用同一元数据标准，适配器不得私自增加影响权限或语义但平台不理解的关键字段。

## 11. 领域事件契约

事件信封：

```json
{
  "eventId": "evt_01...",
  "eventType": "ModelDeploymentPublished",
  "schemaVersion": 1,
  "tenantId": "ten_01...",
  "aggregateId": "mdl_01...",
  "aggregateVersion": 4,
  "occurredAt": "2026-07-26T10:00:00Z",
  "traceId": "trc_01...",
  "payload": {}
}
```

- 事件结构只向后兼容演进；破坏性变化使用新事件类型或主版本。
- 消费者按 `eventId` 幂等，按聚合版本拒绝倒序覆盖。
- 事件不携带密钥、提示词全文、文档正文或个人画像明细。
- 领域事件不是审计记录；审计上下文单独保存并建立关联。

## 12. 契约安全与验证

- OpenAPI 文档由契约生成并做破坏性变化检查。
- 请求结构拒绝未知高风险字段，限制嵌套深度、数组长度和正文大小。
- 文件上传使用内容嗅探、病毒检测、隔离和异步解析。
- 所有资源接口执行租户和资源授权，不能依赖前端隐藏。
- 接口契约测试覆盖错误码、幂等、乐观锁、页码分页、SSE 重连、撤权和脱敏。
- 公共接口或事件发生破坏性变化前必须有关联 ADR 和工作包。
