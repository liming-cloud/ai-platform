# WP-0012：查询反馈持久化与受控接口证据

## 范围

本证据绑定提交 `7cb00b563`。本切片完成 ADR-0036 定义的 `QueryFeedback` 真实持久化、同一保留边界、自动配置和最小 HTTP 入口；不实施草稿可信回答执行，ADR-0037 仍待接受。

## 设计与安全边界

- `QueryFeedback` 仍是 trusted-query 拥有的独立不可变聚合，接口只接受 `outcome` 和 `reason` 枚举。
- `TrustedRequestContext` 继续由令牌解析器注入。`POST /api/v1/query-runs/{queryRunId}/feedback` 不接受租户、成员、权限、渠道或令牌字段。
- `trusted_query.query_feedback` 只保存标识、受控判断和时间；不保存问题、回答、提示词、引用正文、令牌或诊断。
- `(tenant_id, query_run_id, requested_by)` 同时作为唯一约束和到 `query_run` 的三列外键，确保反馈只能属于该运行的原始请求成员；外键使用 `ON DELETE CASCADE`，删除查询运行时同步删除反馈。
- Mapper 使用 `ON CONFLICT DO NOTHING` 原子区分首次写入和重复提交，登记为 `ATOMIC_STATE_TRANSITION`，不是债务豁免。

## TDD 红绿记录

红灯：先在 `PostgresQueryRunRepositoryTest` 注入尚不存在的 `QueryFeedbackMapper` 与 `PostgresQueryFeedbackRepository`，执行：

```bash
./mvnw -q -pl backend/contexts/trusted-query -am test \
  -Dtest=PostgresQueryRunRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：失败，编译器明确报告两个持久化类型不存在。

红灯：随后先增加 `QueryFeedbackControllerTest` 和自动配置断言，执行：

```bash
./mvnw -q -pl backend/bootstrap/query-bootstrap -am test \
  -Dtest=QueryFeedbackControllerTest,TrustedQueryAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：失败，编译器明确报告 `QueryFeedbackController` 不存在。

绿灯：实现迁移、Mapper、仓储、自动配置与接口后，上述两条命令均通过。控制器回归确认返回 `204`，并原样传递令牌解析的可信上下文；真实 PostgreSQL 回归确认首次写入、重复无新增和查询运行删除后的级联清理。

## 全量验证

```bash
make architecture-guard
./mvnw -q -pl backend/contexts/trusted-query -am clean test
```

结果：两条命令均通过。第二条命令使用 `clean` 清除了本地已删除的旧迁移资源；Flyway 在新库实际执行 `V202607300026__create_query_feedback_table.sql`，版本到达 `202607300026`。日志中的 Spring 条件装配与 Testcontainers 警告均为既有测试预期，不含失败。

## 代码图谱与影响分析

- `codegraph_explore` 显示 `QueryRun` 已被配额、事件和运营投影广泛消费，反馈不应并入该聚合。
- `codegraph_impact` 显示 `TrustedQueryPersistenceAutoConfiguration` 的受影响范围仅为持久化 Bean 与既有查询事实投影；因此只增加 `QueryFeedbackRepository` Bean，不改变查询运行持久化协议。
- 对 HTTP seam 的探索确认 `QueryRunController` 已以 `TrustedRequestContext` 参数接收解析后的登录态，反馈入口沿用同一模式，没有新建身份参数或浏览器会话机制。

## 未完成项

- 草稿 `AssistantTestRun` 的可信回答执行仍受 ADR-0037 阻塞。
- 受权诊断投影仍未进入本切片。
