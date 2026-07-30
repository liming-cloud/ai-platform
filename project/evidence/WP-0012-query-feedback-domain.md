# WP-0012：QueryFeedback 领域基础

## 目标与边界

本切片落实 ADR-0036 的最小反馈事实。`QueryFeedback` 是 trusted-query
拥有的独立、不可变 `AggregateRoot`，只关联租户、查询运行标识、请求成员、正负结果、受控原因和创建时间。

它不持有问题、回答、提示词、证据正文、令牌、模型参数或任意自由文本；没有修改和撤回 API。本切片不创建仓储、数据库表、HTTP、事件或前端入口。

## 红绿记录

### 红灯

```bash
./mvnw -q -pl backend/contexts/trusted-query -am test \
  -Dtest=QueryFeedbackTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

实现前以退出码非零失败：`QueryFeedback`、其标识、结果和原因类型均不存在，Maven `testCompile` 报“找不到符号”。

### 绿灯

```bash
./mvnw -q -pl backend/contexts/trusted-query -am test \
  -Dtest=QueryFeedbackTest \
  -Dsurefire.failIfNoSpecifiedTests=false
./mvnw -q -pl backend/contexts/trusted-query -am test
make architecture-guard
```

以上命令均以退出码 0 通过。全模块回归中的 Spring、Testcontainers 与预期失败场景日志为既有测试装配输出，不改变结果。

## 领域不变量

- 标识、关联 `QueryRunId`、请求成员和创建时间均不可为空且标识为正数。
- `HELPFUL` 只能表达正向反馈；`INCORRECT`、`INSUFFICIENT`、`IRRELEVANT` 只能表达负向反馈。原因与结果不匹配时立即拒绝。
- 聚合只有 `record` 工厂，没有更新、撤回或正文入口；结构性测试确认该聚合没有 `String` 字段。
- “查询已完成”“同一查询运行与请求成员至多一条”以及随查询保留边界删除，留给后续应用编排和 PostgreSQL 唯一约束切片验证，不能在纯聚合中伪造当前 `QueryRun` 状态。

## 代码图谱与影响

代码图谱显示 `QueryRun` 有 41 个调用方，`QueryRunRepository` 影响 101 个符号并负责运行、事件和查询持久化。故本切片没有向 `QueryRun` 增添反馈字段或事件，也没有改动该仓储；反馈只以运行标识关联，避免复制历史正文或扩大普通查询事件的恢复语义。

## 六维判断

| 维度 | 结论与证据 |
| --- | --- |
| 正确性 | `QueryFeedbackTest` 覆盖正向事实、负向原因、结果与原因不匹配、非法标识和正文存储归零。 |
| 复杂度 | 仅增加一个根、两个标识和两个受控枚举；没有通用评价模型、自由文本或状态机。 |
| 冗余 | 不扩展 `QueryRun` 或其事件；反馈以标识关联，保留两条生命周期的清晰边界。 |
| 性能 | 领域层无 I/O；后续唯一写入将由数据库索引保证，当前不引入缓存或扫描。 |
| 架构 | `make architecture-guard` 通过；新增可变对象实现 `AggregateRoot`，未引入非根 `Entity`、框架依赖或跨上下文领域引用。 |
| 测试质量 | 保存了真实缺类型红灯；同一领域 seam 及 trusted-query 全模块回归均已转绿。 |

## 后续

下一切片增加受控应用命令、完成态验证、独立 PostgreSQL 唯一约束和随 `QueryRun` 删除的保留语义。M4 仍未完成，不能进入 M5。
