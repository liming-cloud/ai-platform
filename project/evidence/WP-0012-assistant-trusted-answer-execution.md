# WP-0012：草稿助手与普通查询的统一可信回答执行

## 范围与绑定提交

本证据绑定实现提交 `d22c3e65a`。它完成 ADR-0037 的统一可信回答执行门面和
ADR-0036 的草稿助手测试应用、自动配置与 HTTP 入口；不创建 ADR-0038 的诊断事实表、
读取接口或任何诊断持久化协议。

`AssistantTrustedAnswerExecutor` 是唯一的执行路径：在冻结目标内执行嵌入、已授权索引候选
检索、确定性证据选择、对话与最小引用生成。它不持久化问题、回答或候选正文，不写事件、
配额或运行根。`QueryRunService` 保留普通查询的幂等、配额和终态事件编排；
`AssistantTestRunService` 保留草稿受限读取、独立配额和 `AssistantTestRun` 最小终态提交。

## 安全与领域不变量

- 草稿测试仅加载同租户、`DRAFT` 且拥有 `assistant:write` 的冻结快照；开始执行前还要求
  `knowledge:read`。它不放宽普通 `QueryRun` 的 `PUBLISHED` 版本规则。
- 草稿运行以 SHA-256 题目摘要开始，当前调用返回的回答和引用不写入运行历史；仓储只保存
  已有的最小终态事实。
- 控制器只接收助手版本标识和问题。HTTP 回归使用真实
  `SaTokenTrustedContextArgumentResolver`，证明 `Authorization: Bearer` 恢复的租户和权限
  覆盖请求体伪造的 `tenantId` 与 `permissions`。
- 旧查询侧 `QueryPromptBuilder`、`QueryEvidenceSelector` 及其测试已迁入共享执行器包，避免
  普通查询与草稿测试形成两套提示词或证据选择规则。
- 自动配置只有在模型解析、受控模型配置运行时、候选检索器和草稿测试仓储均可用时才装配
  执行器和草稿测试服务；缺少依赖时不会创建半可用入口。

## 验证

```bash
./mvnw -q -pl backend/contexts/assistant-delivery,backend/contexts/trusted-query -am test \
  -Dtest=TrustedAnswerEvidenceSelectorTest,QueryRunServiceTest,AssistantTestRunServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false

./mvnw -q -pl backend/contexts/assistant-delivery,backend/bootstrap/query-bootstrap -am test \
  -Dtest=AssistantDeliveryAutoConfigurationTest,AssistantTestRunHttpTest \
  -Dsurefire.failIfNoSpecifiedTests=false

./mvnw -q -pl backend/bootstrap/query-bootstrap -am test
make architecture-guard
```

以上命令均以退出码 `0` 通过。前两条分别覆盖共享证据选择、普通查询委派、草稿终态、自动
配置与 Bearer 上下文 HTTP 路由；完整反应堆命令包含已有的真实 PostgreSQL
`PostgresAssistantTestRunRepositoryTest`。日志中的条件装配、MyBatis 扫描与 Testcontainers
提示均为既有测试预期，不包含失败。

## 代码图谱与影响

实施前以 `codegraph_explore` 检查 `AssistantTestRunService`、
`AssistantTrustedAnswerExecutor`、`AssistantTestRunController`、自动配置和
`TrustedRequestContext` 解析路径，并对 `AssistantTestRunService` 执行深度 3 影响分析。
结论是新增服务只影响核心自动配置、控制器和三组局部测试。

随后对 `QueryPromptBuilder` 与 `QueryEvidenceSelector` 执行影响分析：后者只被自身测试
使用，前者没有生产调用方。因此将规则和反例测试迁入共享执行器，而不改变查询事件、仓储、
配额或诊断边界。

## 后续

ADR-0038 的诊断投影仍待处理“检索前失败没有精确候选数”的已识别语义矛盾；在该矛盾获得
明确决策前，不得把未知候选数伪造为零，也不得实现诊断事实持久化。
