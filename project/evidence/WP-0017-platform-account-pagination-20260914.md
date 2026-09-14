# WP-0017：平台账户目录框架分页

## 切片边界

依据 ADR-0022 与 ADR-0079，在平台管理闭环增加业务能力前，先移除该目录的手工分页。保持现有 `PageQuery/PageResult`、账户资格、排序、角色聚合及密码摘要不可见的语义，不提前改变平台开户或租户管理员创建契约。本切片不代表 WP-0017 已完成。

允许修改：organization-access infrastructure 内的 `PlatformAccountMapper`、`PostgresPlatformAccountStore`、对应 XML、同目录分页契约与真实 PostgreSQL 测试；同步删除复杂 SQL 允许清单中已不存在的 count 语句，并登记当前工作包证据。不修改领域、应用公共接口、账户状态事务、生产前端或原型。

代码图谱已探索 `PlatformAccountMapper`、`PostgresPlatformAccountStore`、`PlatformAccountEstablishmentApplicationService` 与 `TenantProvisioningService`。Mapper 深度 1 影响分析包含 24 个符号，主要是仓储、配置测试和接口映射；因此保持 Mapper 的实现变动局限于基础设施，不同时改变账户角色语义。

## 验证设计

- 先写 Mapper 使用 MyBatis-Plus `IPage` 的架构契约反例，观察当前手工分页失败。
- 真实 PostgreSQL 容器验证空目录、跨页、越界页、稳定排序、聚合总数及现有账户治理事务回归，不能用 mock 的返回值证明 SQL。
- 复用仓库现有 `Page` 和 `PageResult.of` 模式；运行目标模块测试、架构门禁和项目治理。
- 完成代码评审后提交，证据索引绑定真实提交；不把局部测试当作整个工作包验收。

## 执行结果

工具链为 Maven 3.9.16、JDK 26.0.2、Docker 29.2.1、PostgreSQL 18.4，容器实际应用 13 个迁移。

红灯：`./mvnw -q -pl backend/contexts/organization-access -am -Dtest=PlatformAccountPagingContractTest -Dsurefire.failIfNoSpecifiedTests=false test` 因 Mapper 无 `list(IPage)` 失败。实际 PostgreSQL 测试另复现空目录第 2 页抛出“分页派生状态不一致”；13 项中 12 通过、1 错误。期间新增历史角色夹具曾误插已有活动角色，唯一约束正确拒绝；改成已撤销的历史绑定后才确认产品红灯，不把夹具错误计为产品缺陷。

绿灯：`./mvnw -q -pl backend/contexts/organization-access -am -Dtest=PlatformAccountPagingContractTest,PostgresPlatformAccountStoreTest -Dsurefire.failIfNoSpecifiedTests=false test` 返回 0，1 项 Mapper 契约和 13 项真实数据库测试通过，无跳过。覆盖空目录保留页码、非空越界页、同时间稳定排序、历史绑定聚合、密码摘要不可见及现有写入/回滚。

`make architecture-guard` 的 28 项回归和门禁通过；`make project-verify` 的 35 项测试、26 包校验通过。架构脚本仍输出 3 处手工分页，因为其正则仅识别含 Page/After 的语句 ID，原 `list` 未计入；不能据此宣称全仓债务仅剩 3 处。该统计漏计交由 WP-0017 后续门禁修正，退出条件为补齐 list 命名反例、精确盘点并登记其余责任包，本切片不扩大其他列表重构。

Codex 实际加载工程助手的 engineering-assistant、wayfinder、tdd、code-review；使用 Charter、STATE、WP-0017、ADR-0022/0079、代码图谱和真实测试。`implement` 流程为 `run-8f96ddd45aa445a8`，切片选择节点已由插件接受，红绿与评审节点随后校验。局部通过不等于整个 WP-0017 的 HTTP、浏览器、Compose 和业务门禁通过。

独立固定 diff 评审通过，无必修问题：框架类型仅在 infrastructure，账户范围、锁定/更新 SQL 及公共契约未改变；生产分页插件 `overflow=false/maxLimit=100`，测试实际验证越界不回首页，`PageQuery` 同样限制最大 100。空页、越界、历史绑定、摘要保护和现有事务成功/回滚均有真实 PostgreSQL 结果。未触及新身份、异步状态终止、凭据 grammar 或跨上下文写入 ID 合同，不把这些未触及项扩张成全仓测试。

六维判断：正确性由同一空页红绿与 14 项回归支持；复杂度减少手工 offset、页数及布尔状态计算；冗余移除独立 count 映射；性能保持数据库侧 count/分页和有界记录映射，无全量 Java 分页或 N+1，本次不声称生产容量或时延 SLA 已验收；架构由 ADR-0022 和门禁确认框架未外泄；测试质量包含真实 PostgreSQL 和既有事务回滚，不以反射用例单独证明行为。六项在当前分页切片内通过。
