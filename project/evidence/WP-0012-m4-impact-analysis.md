# WP-0012：M4 草稿测试与查询反馈影响分析

## 目的与边界

本记录在不改变运行行为的前提下，为 ADR-0036 的人工决策提供代码事实和最小实施 seam。它不接受 ADR-0036，不新增 HTTP、事件、表、路由或领域对象，也不将 M5 至 M8 激活。

分析时间：2026-07-30。分析基线：`5e2137cf6`。

## 代码图谱结论

| 关注点 | 已核实事实 | 对设计的影响 |
| --- | --- | --- |
| 草稿读取 | `AssistantRepository.findVersion` 可按租户读取任意 `AssistantVersion`；`AssistantService.publishVersion` 已用它加载待发布草稿。 | 可以在 assistant-delivery 增加受限的草稿测试读取门面，不需要修改 `AssistantVersion` 或把草稿伪装成新聚合。 |
| 普通查询入口 | `QueryRunService.create` 固定调用 `PublishedAssistantVersionLookup`，只在成功取得已发布快照后创建、占用配额和完成 `QueryRun`。 | 草稿测试不能向该入口添加“允许草稿”开关；这会直接违反 ADR-0016。 |
| 可信执行 | `QueryRunService` 的私有 `execute`／`executeGoverned` 负责嵌入、候选、证据选择、模型调用、引用和拒答。 | 存在第二个真实调用方后，应抽出包内应用层执行模块，输入为不可变执行快照，输出为已验证结果；它不接收或持久化聚合根。 |
| 查询事实与事件 | `QueryRun`、`QueryRunEvent`、`PostgresQueryRunRepository` 与事件服务共同形成公开查询、恢复、计量和审计事实。 | `AssistantTestRun` 必须独立持久化，不复用 `QueryRun` 表、事件序列、公开 SSE 或运营汇总。 |
| 诊断与审计 | `OperationsQueryService.audit` 已通过 `QueryOperationsFactSource` 读取脱敏事实，并强制 `audit:read`。 | M4 诊断应由 trusted-query 提供最小脱敏事实，再由 evaluation-operations 以既有 `audit:read` 门面呈现；不能向查询站点扩散内部信息。 |
| 浏览器边界 | query-web 的 `QueryRequestScope` 已按会话修订取消旧请求；查询客户端只操作普通已发布查询。 | 草稿测试属于企业控制台助手任务，不能在 query-web 复用普通提问路由；反馈仅面向已完成普通查询。 |

代码图谱探索覆盖：`AssistantVersion`、`AssistantRepository.findVersion`、`AssistantService`、`PublishedAssistantVersionLookupService`、`QueryRun`、`QueryRunService`、`QueryRunEventService`、`OperationsQueryService`、query-web 的 `QueryClient` 与 `QueryRequestScope`。以 `QueryRun` 为根的影响分析覆盖 323 个符号，包含查询服务、PostgreSQL 仓储、事件、运营投影、自动配置、HTTP 和前端客户端。

## 接受 ADR 后的最小 seam

1. assistant-delivery 只增加草稿版本的受限读取门面和 `AssistantTestRun` 聚合。门面必须按 `assistant:write`、租户和草稿状态失败关闭，返回框架无关、不可变的执行快照。
2. trusted-query 增加包内的可信答案执行模块，复用既有嵌入、候选、证据再鉴权、引用和拒答规则。普通 `QueryRunService` 与草稿测试门面共同调用该模块；只有前者创建 `QueryRun`、占用普通查询配额并写入公开事件。
3. `QueryFeedback` 只关联已完成普通 `QueryRun`，不关联 `AssistantTestRun`；它由 trusted-query 拥有并使用独立仓储与幂等键。
4. 诊断保持只读：trusted-query 暴露最小诊断事实，evaluation-operations 在现有 `audit:read` 边界内映射。最终用户查询站点不获得该接口。

这四个 seam 都有现有调用方和单向上下文关系；未引入“通用测试引擎”、共享持久化实体或跨上下文表读取。

## ADR-0036 接受前必须明确的决定

| 决定 | 可选方向 | 未决定的风险 |
| --- | --- | --- |
| 草稿测试的资源限制 | 复用普通查询配额，或为助手测试建立独立且受限的配额事实。 | 不明确会造成真实模型调用无上限，或把测试错误计入最终用户用量。 |
| 测试问题留存 | 仅留摘要与终态，或以受控保留策略保存题目以支持复现。 | 不明确会在“可复现”与最小留存之间留下不可审计的空白。 |
| 反馈内容 | 只允许正负反馈和受控原因枚举，或允许经专门安全规则处理的说明文本。 | 自由文本会引入敏感信息处理、删除和保留义务；当前没有相应 ADR 或服务。 |

上述决定均会改变资源计量、数据留存或公共契约，不能由实现人员自行选择。ADR-0036 必须在人工接受时补全它们；在此之前不得开始红绿实现。

## 接受后的首批红灯

- 传入 `DRAFT` 助手版本到普通创建查询入口时仍失败关闭。
- 无 `assistant:write`、跨租户或非草稿版本不能启动 `AssistantTestRun`。
- 草稿测试与普通查询使用同一可信证据验证；未知、越权或缺失引用均拒答。
- `QueryFeedback` 不接受未完成、跨租户或非请求成员的查询；其字段不含问题、回答、提示词、证据正文或令牌。
- 无 `audit:read` 的主体无法读取诊断；诊断不返回内部资源标识、正文、策略细节或密钥。
- 事件重连只恢复已验证终态，不发布暂定模型正文。

## 结论

当前代码与 ADR-0036 的主边界一致，但不具备安全的草稿测试、反馈或诊断实现入口。实施应等待 ADR-0036 补全并接受；届时从“草稿不可经普通查询访问”的纯领域反例开始，按 WP-0012 的 TDD 顺序推进。
