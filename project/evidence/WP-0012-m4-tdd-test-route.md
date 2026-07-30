# WP-0012：M4 草稿测试 TDD 路线

## 目的与边界

本记录把 ADR-0036 接受后的首个红绿切片固定为可执行路径。它不执行测试、不创建生产代码，也不代表 ADR-0036 已接受。

分析时间：2026-07-30。代码基线：`367cc3363`。

## 已验证的测试与装配事实

| 区域 | 现有 seam | 首次变更的意义 |
| --- | --- | --- |
| assistant-delivery | `AssistantRepository.findVersion` 可按租户读取版本；`PublishedAssistantVersionLookupService.find` 明确过滤 `PUBLISHED`。 | 新增只读取 `DRAFT` 的独立门面，不修改普通查询的已发布读取门面。 |
| trusted-query | `QueryRunServiceTest` 已以 `PublishedAssistantVersionLookup`、内存仓储、模型和检索替身验证查询编排。 | 先保留此测试为普通查询回归，后续通过独立草稿执行入口添加新测试，不向 `QueryRunService.create` 加开关。 |
| 自动配置 | `TrustedQueryAutoConfigurationTest` 已验证核心服务、Web 入口和持久化条件装配。 | 新服务需要各自的自动配置反例；查询 Web 不能因草稿测试新增入口。 |
| 真实持久化 | `PostgresAssistantRepositoryTest` 已创建 DRAFT／PUBLISHED 版本并验证租户隔离；`PostgresQueryRunRepositoryTest` 覆盖查询事实。 | `AssistantTestRun` 和其独立配额必须另建真实 PostgreSQL 反例，不能复用 `query_run` 表。 |

以上结论由代码图谱的 `PublishedAssistantVersionLookupService.find`、`QueryRunServiceTest`、`TrustedQueryAutoConfigurationTest`、`PostgresAssistantRepositoryTest` 和 `QueryRun` 影响分析获得。`QueryRun` 的影响面为 323 个符号，故首切片明确不修改它。

## 红绿切片顺序

### 切片一：草稿版本受限读取门面

先新增 `DraftAssistantVersionLookupServiceTest`。红灯应证明：同租户 `DRAFT` 可投影为不可变测试执行快照；`PUBLISHED`、跨租户、非法标识均失败关闭。测试初始因门面与快照不存在而失败。

最小实现只在 assistant-delivery 增加受限公开门面与不可变快照，复用既有 `AssistantRepository.findVersion`。不得修改 `PublishedAssistantVersionLookupService`、`QueryRunService`、HTTP 路由或数据库。

绿灯后运行：

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test
make architecture-guard
```

### 切片二：独立 `AssistantTestRun` 与测试配额

先以纯领域测试建立 `AssistantTestRun` 状态、冻结依赖、题目摘要、终态和独立配额快照的反例。再以真实 PostgreSQL 证明测试运行和普通 `QueryRun` 的数据、配额及运营汇总相互隔离。

绿灯后至少运行：

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test
./mvnw -q -pl backend/contexts/trusted-query -am test
make architecture-guard
```

### 切片三：可信执行复用与安全回归

先使草稿执行入口调用与普通查询同一可信证据、引用和拒答规则，再以应用测试证明它不会创建 `QueryRun`、公开事件或普通用量。模型、检索和受控配置使用现有 `QueryRunServiceTest` 风格的替身，真实 PostgreSQL 只验证本地事实和原子提交。

### 切片四：反馈、诊断与浏览器

先以领域和 HTTP 反例固定“每个查询运行与请求成员仅一条枚举反馈”、无自由文本和跨租户失败关闭；诊断只经 `audit:read` 映射。随后补企业控制台草稿测试任务、问答工作台反馈任务和原有会话修订／SSE 恢复回归。

## 最终验证层级

每个切片先运行对应模块测试和 `make architecture-guard`。M4 自动验收前，还必须新增明确命名的草稿测试、反馈与诊断验收命令，并运行：

```bash
make project-verify
make validate
make acceptance-assistant
make acceptance-trusted-query
make acceptance-query-web
```

浏览器验收必须分别覆盖助手负责人、最终用户和具有 `audit:read` 的审计人员；不能以单一管理会话代替三种权限反例。

## 六维预检

| 维度 | 当前结论 |
| --- | --- |
| 正确性 | 首切片不改变已发布查询；随后以独立聚合确保草稿不进入普通运行。 |
| 复杂度 | 只引入已有第二调用方所需的草稿读取门面和可信执行 seam，拒绝通用测试引擎。 |
| 冗余 | 普通查询与草稿测试共享可信执行规则，但不共享查询事实、配额或事件。 |
| 性能 | 草稿测试仍在事务外完成模型与检索调用；独立配额限制并发与每日消耗。 |
| 架构 | 维持 assistant-delivery 到 trusted-query 的公开门面方向，基础设施不补写领域规则。 |
| 测试质量 | 每个新行为先有预期红灯，模块、真实 PostgreSQL、自动配置、HTTP 和浏览器各有独立反例。 |

## 进入条件

只有 ADR-0036 被人工接受并提交后，才能执行本路线中的首个红灯。届时必须在同一工作包中保存实际红灯、绿灯命令和结果，不能以本计划替代运行证据。
