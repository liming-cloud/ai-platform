# WP-0012：查询诊断事实原子投影证据

## 范围

本证据绑定提交 `8fcd66e61`。它落实 ADR-0038 与 ADR-0039：查询终态在同一事务中固化最小诊断事实，并只向持有 `audit:read` 的令牌派生身份提供受控读取。它不把 M4 标记为完成，也不激活 M5。

## 设计与安全边界

- `QueryDiagnosticFact` 只保存候选是否已收集、候选数量、检索授权、Chat／Embedding 修订和引用校验；不保存候选正文、相似度、对象位置、模型标识、供应商错误、问题或回答。
- `QueryRunTerminalCommitter` 是应用层原子提交端口。`QueryRunRepository` 不再暴露可绕过诊断事实的终态提交方法。
- `PostgresQueryRunRepository` 在一个事务内条件更新 `QueryRun`、写入引用和终态事件，并插入 `query_diagnostic_fact`。事实插入失败时全部回滚；删除查询运行时外键级联删除事实。
- `GET /api/v1/query-runs/{queryRunId}/diagnostic` 仅从 `Authorization: Bearer` 解析 `TrustedRequestContext`。应用服务在任何存储读取前验证 `audit:read`，仅使用当前租户查询；不存在、跨租户和缺失事实统一返回不可得结果。
- XML SQL 的登记是复杂 SQL 审计清单，不是 DDD 违规的债务豁免；领域聚合和应用层依赖仍由架构门禁直接检查。

## TDD 红绿记录

红灯一：先增加 `QueryDiagnosticFactTest` 的候选收集状态反例，执行：

```bash
./mvnw -q -pl backend/contexts/trusted-query -am test \
  -Dtest=QueryDiagnosticFactTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：测试编译失败，明确报告 `CandidateCollection` 尚不存在。

红灯二：先增加 `QueryDiagnosticServiceTest`，执行：

```bash
./mvnw -q -pl backend/contexts/trusted-query -am test \
  -Dtest=QueryDiagnosticServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：测试编译失败，明确报告诊断查询、读取端口、服务和拒绝异常尚不存在。

绿灯：实现不可变事实、终态提交端口、PostgreSQL 映射、受权服务和 HTTP 控制器后，定向领域、应用、真实 PostgreSQL、自动配置和 Bearer HTTP 回归全部通过。负向回归还覆盖候选已经收集后终态映射失败仍写入 `COLLECTED` 失败事实。

## 可复现验证

```bash
./mvnw -q -pl backend/contexts/assistant-delivery,backend/contexts/trusted-query,backend/bootstrap/query-bootstrap -am test \
  -Dtest=AssistantTestRunServiceTest,QueryRunServiceTest,QueryDiagnosticFactTest,QueryDiagnosticServiceTest,PostgresQueryRunRepositoryTest,TrustedQueryAutoConfigurationTest,QueryDiagnosticHttpTest \
  -Dsurefire.failIfNoSpecifiedTests=false

make validate
```

结果：两条命令均通过。`make validate` 在持久终端会话中以退出码 `0` 完成；包含项目治理、架构门禁、全反应堆 Maven、前端检查、演示、真实模型边界、恢复、容量和交付演练。Testcontainers、预期失败关闭和 Spring 条件装配日志不代表测试失败。

## 代码图谱与影响分析

- `codegraph_explore` 确认 `QueryRunService` 是生产终态写入路径，`PostgresQueryRunRepository` 已在一个 `TransactionTemplate` 中写入引用与终态事件，适合收口为一个原子提交端口。
- `codegraph_impact QueryRunRepository` 显示移除旧 `complete` 的影响限于查询服务、自动配置和测试替身；据此移除了可绕过事实写入的端口，而没有扩大到其他上下文。
- HTTP 探索确认 `SaTokenTrustedContextArgumentResolver.resolveArgument` 已从 Bearer 会话恢复可信上下文，因此诊断控制器复用该 seam，并在 HTTP 回归中验证伪造租户参数不能改变查询租户。

## 未完成项

- M4 仍等待工作包级人工评审；本证据不构成 M4 退出批准。
- 当前切片是受权 API 投影，不新增面向普通用户的诊断页面；浏览器角色验收按 M4 整包人工评审统一执行。
