# WP-0014 模型发现阻断整改证据

## 证据边界

- 核对日期：2026-08-29。
- 当前里程碑与前沿保持 `M6 / WP-0014`；本证据只纠正模型发现阻断判断，不关闭工作包，也不激活 M7。
- 本轮已通过正确同源 HTTP 请求完成 OpenAI-compatible 权威模型发现。此前手工请求返回的
  `400 COMMON.REQUEST.0001` 不是模型发现适配器或供应商 `/models` 的失败证据。
- 实现已绑定提交 `d7cf86fdd`；当前未提交内容只剩项目控制面与证据更新。以下结论严格区分已进入
  当前 Compose 的运行时事实、已提交源码与测试事实，以及仍待续证的外部／视觉事实。

## 手工请求错误与正确请求契约

此前诊断样本同时混入两项客户端构造错误：

1. 登录请求体误用 `username`，而浏览器会话 DTO 的字段是 `loginName`；
2. 请求缺少 `BrowserSessionCsrfFilter` 强制要求的 `Sec-Fetch-Site: same-origin`，且未完整携带服务端签发的
   CSRF Cookie 与对应请求头。

这些错误发生在模型发现 Controller 之前，因此 Controller 内临时观测没有输出与该现象一致。修正后的请求链为：

1. 取得服务端 CSRF Cookie；
2. 使用 `loginName` 登录，并发送 `Sec-Fetch-Site: same-origin`；
3. 后续模型发现请求继续发送同源头、浏览器会话 Cookie、CSRF Cookie 与匹配的 CSRF 请求头；
4. 首次以修正后的连接修订 `2` 完成发现；最终实现提交镜像复验使用当前修订 `3` 与 `timeoutMillis=10000`。

两次正确请求均返回 HTTP `200`。因此不得再用上述错误手工请求的 `400` 作为当前模型发现阻断结论。

## 本地连接精确修正

本地既有连接的 API 基础地址错误包含了动作路径：

```text
https://ark.cn-beijing.volces.com/api/v3/chat/completions
```

本轮只对连接 `2093675537095458817` 的单行事实做精确修正，目标值为：

```text
https://ark.cn-beijing.volces.com/api/v3
```

修正没有改动任何凭据值，只清空旧探测字段，并把连接版本从 `1` 推进到 `2`。随后正确的浏览器 HTTP
模型发现成功，连接状态变为 `VERIFIED`，版本从 `2` 推进到 `3`。当前只读核对结果为：

- `provider_kind=OPENAI_COMPATIBLE`；
- `discovery_profile=OPENAI_MODELS_API`；
- `endpoint=https://ark.cn-beijing.volces.com/api/v3`；
- `status=VERIFIED`、`version=3`；
- `last_probe_succeeded=true`、`last_probe_code=OPENAI_COMPATIBLE_DISCOVERY_OK`。

## 权威发现与安全响应

OpenAI-compatible 适配器对修正后的 API 根地址调用权威 `/models`，单次返回 `130` 个候选。最新发现批次
全部为 `REQUIRES_ADMIN_CONFIRMATION`，没有根据模型名称猜测 `CHAT`／`EMBEDDING` 类型或能力。
这符合 ADR-0070 的权威元数据与管理员确认边界。

浏览器成功响应只包含 `succeeded` 与安全候选集合。当前 Controller 契约和 HTTP 响应均不包含：

- `discoveryId`；
- `resultCode`；
- `elapsed`；
- `discoveredAt`；
- `expiresAt`。

响应也不暴露 Endpoint、凭据引用、API Key、供应商原始响应或内部错误详情。

最终 HTTP 断言成功响应无内部字段；冲突响应也不包含 `code`、`message`、`traceId` 或 `details`。

## 旧修订冲突的红绿状态

定向红测先冻结了旧修订请求不得执行发现、不得写入发现记录的行为。当前源码随后把两处泛化的
`IllegalStateException` 收敛为 `ProviderConnectionConcurrentModificationException`：一处在请求进入外部发现前
比较 `expectedVersion`，另一处在草稿连接探测后的乐观更新失败时拒绝。
测试以 `ForbiddenDiscoverer` 证明旧修订在任何供应商 HTTP 调用前即被拒绝。

异常内部仍使用稳定分类 `MODEL_RUNTIME.CONFLICT.0001`，但该端点由
`PlatformProviderModelDiscoveryController.concurrentModification` 局部投影为 HTTP `409`，公开正文只有
`{"succeeded":false,"models":[]}`。最终 Compose 同源 HTTP 先以当前修订返回 `200`，再以旧修订返回
`409`；浏览器不接收 `code`、`message`、`traceId` 或 `details`，内部稳定分类不越过端点边界。

## Compose 与外部能力边界

`docker compose --env-file deploy/.env -f deploy/compose/compose.yml ps --format json` 的只读核对显示以下七个
服务均为 `running / healthy`：

- `postgres`；
- `redis`；
- `minio`；
- `control-plane`；
- `query-runtime`；
- `ingestion-worker`；
- `console-web`。

本轮没有续证火山方舟控制面真实 AK/SK 成功路径。历史证据仍可说明既有契约和曾完成的验证，但不能替代
当前工作区、当前镜像和当前凭据条件下的新实证，因此本文件不声明该外部路径已重新通过。

## Chrome 与五项视觉续证

Chrome 控制插件仍无法启动。阻断来自 Codex 沙箱拒绝带 symlink 组件的 writable root，而不是产品页面或
浏览器业务错误。当前前端已发生新的设计修改，旧提交绑定的 Chrome 证据不能自动覆盖本次差异；恢复插件后
需要重新完成以下五项：

1. `1024×768` 桌面视口；
2. `390×844` 窄屏视口；
3. Chrome 真实 `200%` 缩放；
4. 键盘导航、Dialog 关闭与焦点恢复；
5. macOS 实际 `prefers-reduced-motion`。

在五项新证据形成前，只能声明自动契约与既有视觉基线可参考，不能声明当前差异的登录后视觉回归已完成。

## 代码路径与核对结果

代码图谱聚焦核对了以下符号：

- `PlatformProviderModelDiscoveryController.discover`：把应用结果投影为只含 `succeeded` 与 `models` 的安全响应；
- `PlatformProviderModelDiscoveryService.discover`：在外部发现前及乐观更新失败处统一抛出稳定并发冲突异常；
- `ProviderConnectionConcurrentModificationException` 与 `ModelRuntimeErrorCode.PROVIDER_CONCURRENT`：错误码为
  `MODEL_RUNTIME.CONFLICT.0001`；
- `PlatformProviderModelDiscoveryController.concurrentModification`：把并发冲突局部投影为安全 `409`，只返回
  `succeeded=false` 与空 `models`；全局 `FoundationExceptionHandler` 不是该端点最终浏览器响应来源。

代码图谱影响分析覆盖 `PlatformProviderModelDiscoveryService` 的 17 个服务内部符号；公共调用入口仍收敛在
`PlatformProviderModelDiscoveryController`，本轮只复用既有稳定冲突异常，没有新增公共错误码或扩大持久化结构。
运行时核对还包括：

- 实现提交 `d7cf86fdd` 基于 `c300125a0f21eb07a605787a5061ea0742b05b11`，本次控制面把
  `lastVerifiedCommit` 推进到该实现提交；
- `make project-status` 与 `make project-verify` 通过；
- `make validate` 通过，前端 31 个测试文件共 149 项，后端含真实 PostgreSQL／Testcontainers；
- 指定连接当前非凭据字段和最新发现批次已通过 PostgreSQL 只读查询核对；
- 七个 Compose 服务健康状态已通过 `docker compose ps` 核对。

## 结论

模型发现主阻断已经用正确请求链闭环：同源浏览器 HTTP 返回 `200`，权威 `/models` 单次返回 `130` 个候选，
全部保持管理员确认边界，公开响应不暴露内部发现元数据；旧修订返回安全 `409`，正文只含失败状态和空候选。
剩余工作是恢复 Chrome 控制插件后重做五项视觉证据，并在可授权条件下另行续证火山方舟控制面真实 AK/SK
路径。`M6 / WP-0014` 继续保持进行中。
