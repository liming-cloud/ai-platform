# WP-0012：独立草稿测试运行持久化与原子配额

## 目标与边界

本切片把 `AssistantTestRun` 的独立持久化事实和独立测试配额落在
assistant-delivery。它只新增运行根、最小引用和配额快照的 PostgreSQL 映射；不调用模型、检索或 HTTP，不创建事件流，不修改 `QueryRun`、普通查询配额或运营汇总。

`assistant_delivery.assistant_test_run` 只保存 SHA-256 题目摘要、冻结依赖、配额快照、终态、用量和最小引用标识。迁移与真实 PostgreSQL 用例均证明表中没有 `question` 或 `answer` 列。

## 红绿记录

### 红灯

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test \
  -Dtest=PostgresAssistantTestRunRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

实现前以退出码非零失败：测试引用的 `AssistantTestRunRepository`、
`PostgresAssistantTestRunRepository`、映射器、迁移和配额异常类型均不存在，Maven `testCompile` 报“找不到符号”。

### 绿灯

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test \
  -Dtest=PostgresAssistantTestRunRepositoryTest,AssistantDeliveryAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -q -pl backend/contexts/assistant-delivery -am test
make architecture-guard
make project-verify
```

以上命令均以退出码 0 通过。真实 PostgreSQL 用例通过 Flyway 应用
`V202607300025`；Spring、MyBatis 与 Testcontainers 的既有装配警告不改变结果。

## 持久化与安全不变量

- `claim` 在每个租户的 PostgreSQL advisory lock 短事务中依次计数日配额、有效运行租约并插入运行起点；日配额和并发配额失败均返回独立类型，不触碰 `QueryRun`。
- `complete` 仅接受已进入终态的根，以租户、运行标识、`RUNNING` 状态和乐观版本原子更新；最小引用在同一事务写入。
- `find` 始终带租户条件；真实用例证明其他租户以相同运行标识读取为空。
- 迁移只建立 assistant-delivery 内的引用外键；不建立跨上下文外键，也不写普通查询事件或运营事实。
- 自动配置仅注册独立 `AssistantTestRunRepository`，且可由已有条件装配关闭。

## 代码图谱与影响

实施前以代码图谱探索 `AssistantTestRun`、`QueryRun`、`QueryRunEvent` 与
`AssistantDeliveryPersistenceAutoConfiguration`，并对后者执行深度 2 影响分析。结果显示普通查询有独立仓储、事件和 41 个调用方；新增自动配置影响面只包含自身及其仓储 Bean。因此实现复用 PostgreSQL 原子锁的技术模式，但不复用 `QueryRun` 类型、表、配额、事件或运营路径。

## 六维判断

| 维度 | 结论与证据 |
| --- | --- |
| 正确性 | 真实 PostgreSQL 覆盖原子占额、并发／日限额、终态恢复、引用写入与跨租户读取拒绝。 |
| 复杂度 | 只有一个仓储端口和一个 PostgreSQL 实现；没有新增通用运行框架、事件或配额配置 API。 |
| 冗余 | 与普通查询的锁模式同构，但使用独立根、异常、表和快照，避免事实混写。 |
| 性能 | 配额计数与锁只覆盖短数据库事务；模型和检索仍不在事务内，运行中租约有专用部分索引。 |
| 架构 | `make architecture-guard` 通过；端口仅传递根和 `DomainPolicy`，基础设施只做映射、锁与原子提交。 |
| 测试质量 | 已保存真实红灯；同一公共仓储 seam 的 PostgreSQL、自动配置、模块回归和项目验证均转绿。 |

## 后续

下一切片在应用层从 `TrustedRequestContext` 取得发起成员和权限，加载受限草稿快照，在事务外执行既有可信检索与引用核验，再调用本仓储提交终态。M4 仍未完成，不能进入 M5。
