# WP-0012：草稿助手版本受限读取门面

## 目标

实现 ADR-0036 的第一个纵向切片：助手负责人只能在同一租户、具有 `assistant:write` 权限且版本为 `DRAFT` 时取得草稿测试执行快照。该门面不改变普通查询的 `PUBLISHED` 版本边界。

## 红绿记录

### 红灯

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test \
  -Dtest=DraftAssistantVersionLookupServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

在实现前失败：`DraftAssistantVersionLookupServiceTest` 引用的
`DraftAssistantVersionLookupService` 不存在，Maven `testCompile` 以“找不到符号”退出。

### 绿灯

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test
make architecture-guard
```

两条命令均通过。日志中的 Spring、SLF4J、MyBatis 与 Testcontainers 警告为既有测试装配输出，不改变测试结果。

## 实现与不变量

- 新增 `DraftAssistantVersionLookup`、`DraftAssistantVersionSnapshot` 与其服务实现；服务只从 `AssistantRepository.findVersion` 读取同租户版本。
- 缺少 `assistant:write` 时抛出既有 `AssistantPermissionDeniedException`，且不读取仓储。
- 仅 `DRAFT` 且使用受控 Chat／Embedding 配置的版本可投影；`PUBLISHED`、跨租户和非法标识关闭失败。
- 自动配置以 `@ConditionalOnMissingBean` 注册门面，并补充开启／关闭装配回归。
- 未修改 `PublishedAssistantVersionLookupService`、`QueryRunService`、查询事件、配额、HTTP 路由、SQL 或数据库迁移；因此本切片不声称新增真实 PostgreSQL 或浏览器行为。

## 代码图谱与影响

实施前代码图谱确认 `PublishedAssistantVersionLookupService.find` 已以状态过滤保护普通查询；`QueryRun` 影响面为 323 个符号。切片只在 assistant-delivery 添加独立门面，避免扩大该影响面。

## 后续

下一切片建立独立 `AssistantTestRun` 聚合和测试配额，并在此基础上复用可信证据、引用和拒答执行规则。
