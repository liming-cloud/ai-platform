# WP-0012：独立 AssistantTestRun 领域基础

## 目标与边界

本切片落实 ADR-0036 的独立草稿测试运行和独立配额的纯领域边界。它只建立不可变运行事实；不创建表、仓储、HTTP、事件、模型调用或浏览器入口，普通 `QueryRun` 的事实、配额、事件和运营汇总保持不变。

## 红绿记录

### 红灯

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test \
  -Dtest=AssistantTestRunTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

实现前以退出码非零失败：`AssistantTestRunTest` 引用的
`AssistantTestRun`、独立配额策略／快照、冻结目标与最小引用标识均不存在，Maven `testCompile` 报“找不到符号”。

### 绿灯

```bash
./mvnw -q -pl backend/contexts/assistant-delivery -am test \
  -Dtest=AssistantTestRunTest \
  -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -q -pl backend/contexts/assistant-delivery -am test
./mvnw -q -pl backend/contexts/trusted-query -am test
make architecture-guard
make project-verify
```

以上命令均以退出码 0 通过。模块回归会启动既有 PostgreSQL/Testcontainers 用例；Spring、SLF4J、MyBatis 与 Testcontainers 警告为既有测试装配输出，不改变结果。

## 领域不变量

- `AssistantTestRun` 是 assistant-delivery 的独立 `AggregateRoot`，以独立标识、状态和版本管理草稿测试；不引用 `QueryRun` 类型或仓储。
- 运行开始即冻结草稿版本、知识空间、索引修订、受控 Chat／Embedding 配置及其影响可信执行的参数；只接受 SHA-256 题目摘要。
- `AssistantTestQuotaPolicy` 是可注入 `DomainPolicy`，`AssistantTestQuotaSnapshot` 固定 UTC 日周期、日序号、并发上限和运行租约；终态必须先绑定这一独立快照。
- 回答终态只保存模型用量和连续的最小引用标识；聚合 API 不接收或保存题目、回答、证据正文、评分或普通查询事件。
- 拒答与失败使用独立终态，不能再次占用配额或回写普通查询事实。

## 代码图谱与影响

实施前探索 `AssistantVersion`、`Assistant`、`AggregateRoot`、`DomainPolicy`、`QueryQuotaPolicy` 与 `QueryQuotaSnapshot`。结论是：`AssistantVersion` 已冻结受控模型与知识索引事实；`QueryRun` 有 323 个符号影响面且其配额／事件属于 trusted-query。因此选择同构但不同类型的测试配额快照，并在 assistant-delivery 新增聚合，未修改或复用普通查询路径。

## 六维判断

| 维度 | 结论与证据 |
| --- | --- |
| 正确性 | `AssistantTestRunTest` 覆盖冻结摘要、无配额终态、连续最小引用和题目正文拒绝；定向绿灯通过。 |
| 复杂度 | 仅增加 ADR-0036 所需的根、值对象与一项策略；没有通用测试引擎、共享运行基类或新应用层抽象。 |
| 冗余 | 与 `QueryRun` 同样表达配额快照和终态，但使用独立类型，避免测试事实进入普通查询表、事件和汇总。 |
| 性能 | 领域层不执行 I/O；后续原子配额占用可用固定日／并发上限限制模型调用，模型和检索仍留在事务外。 |
| 架构 | `make architecture-guard` 通过，新增运行根实现 `AggregateRoot`，策略实现 `DomainPolicy`，未引入非根 `Entity` 或反向上下文依赖。 |
| 测试质量 | 真实红灯已记录，同一公共领域 seam 转绿；assistant-delivery 与 trusted-query 反应堆回归、治理门禁均通过。 |

## 后续

下一切片为该根补独立 PostgreSQL 仓储、原子测试配额占用和应用编排；之后才接入可信执行模块。M4 尚未完成，不能进入 M5。
