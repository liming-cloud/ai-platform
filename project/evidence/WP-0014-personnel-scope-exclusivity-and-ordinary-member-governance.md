# WP-0014 人员范围互斥与租户普通成员治理证据

## 范围与结论

- 实现提交：`790db7b17`。
- ADR-0073 方案 A 的后端安全纵切已落地：活动平台管理员绑定与启用租户成员资格双向互斥；租户管理员
  只能建立、启用、停用和授权本租户普通成员，不能治理租户管理员。
- 所有会扩大人员范围的命令都先锁定共同 `SubjectIdentity`，再复核另一范围的当前事实；页面候选过滤
  只提供一致体验，不能替代命令侧授权。
- 冲突分别稳定返回安全的 `409` 和 `403`，HTTP 响应不含主体、成员、租户内部标识或底层异常。
- 新增 Flyway 只读预检在升级前拒绝历史冲突，只报告冲突主体数量，不自动猜测应删除哪一侧身份。
- 本切片没有实现平台侧租户管理员目录、分配、替换、停用 UI，也没有执行 Chrome 角色任务；这些是
  紧邻下一纵切，不能用本证据声称 `HITL-0008-M6-REJECTION` 已关闭。

## 领域与分层边界

`PersonnelScopeEligibility` 拥有平台／租户活动范围互斥规则，`TenantMemberGovernance` 拥有“租户只治理
普通成员”的目标和角色规则。应用服务只负责可信上下文授权、共同主体锁、另一范围事实读取、领域规则调用、
提交和会话撤销；基础设施层负责 MyBatis Mapper、PostgreSQL 行锁和安全投影。

实现保持以下依赖方向：

```text
interfaces -> application -> domain
infrastructure -> application/domain
```

因此用户、角色与权限不应拆成相互复制事实的独立后端：它们继续由 organization-access 限界上下文拥有，
但接口、应用编排、领域规则和基础设施实现必须分层。SQL 只存在于受登记的 Mapper XML 或迁移；本切片没有
把 SQL 写回 Java。架构门禁仍显示 15 个历史 Java 内联 SQL 债务点，只可递减，本切片未扩大清理范围。

## 代码图谱与影响分析

编码前使用代码图谱探索并追踪下列关键符号：

- `SubjectIdentity` 及既有主体行锁；
- `PlatformRoleBindingApplicationService` 与 `PlatformRoleBindingStore`；
- `TenantMemberEstablishmentApplicationService`、`TenantMemberStatusApplicationService`；
- `TenantProvisioningService`；
- `TenantRoleBindingApplicationService`、`TenantRoleRevocationApplicationService`；
- `TenantMemberCandidateDirectory` 与持久化 Mapper。

影响分析确认平台角色绑定、成员建立／启用和新租户初始管理员建立会并发触碰同一主体，但既有
`SubjectIdentity` 行锁可以作为唯一排序点。该结论使实现采用窄领域服务加存储端口扩展，没有新建 IAM
后端、跨上下文复制角色事实或把规则放进控制器。影响面限定在 organization-access、控制面 HTTP 回归、
演示种子和复杂 SQL 登记。

## 红绿 TDD

### 领域与应用红灯

先增加平台／租户双向互斥和普通成员治理反例。首次编译红灯缺少
`PersonnelScopeEligibility`、`TenantMemberGovernance` 及存储端口事实；实现最小领域规则与应用编排后转绿。

### 候选一致性红灯

真实 PostgreSQL 候选测试首次期望 3 个候选、实际返回 4 个，证明活动平台管理员仍可被页面选中。
`TenantMemberCandidateMapper.xml` 增加活动平台管理员排除谓词后转绿。

### 迁移红灯

`PersonnelScopeExclusivityMigrationTest` 首次期望冲突数据使迁移失败，但迁移正常通过。新增只读预检后：

- 冲突数据以 SQLSTATE `23514` 失败；
- 错误只含“冲突主体数量：1”；
- 登录名、租户编码和内部标识均不泄露；
- 无冲突数据可正常升级。

### HTTP 红灯

真实随机端口 HTTP 首次有 2 项失败：期望 `403`／`409`，实际为 `500`。引入
`OrganizationAccessRuleViolation` 和两个公开安全错误后转绿：

- 平台管理员不能被建立或启用为租户成员；
- 启用租户成员不能再绑定平台管理员角色；
- 新租户不能选择活动平台管理员作为初始管理员；
- 租户管理员不能分配／撤销租户管理员角色，也不能停用租户管理员；
- 响应不包含厂商错误、Java 异常或任何业务内部标识。

### 架构门禁红灯

首次 `make architecture-guard` 精确拒绝 5 个未登记的 Mapper XML 语句。逐条登记行锁或安全投影的业务
原因并修正候选语句说明后转绿；没有修改债务基线或增加 Java 内联 SQL 例外。

## 真实 PostgreSQL、并发与 HTTP

定向模块命令：

```bash
./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -Dstyle.color=never -q \
  -f backend/pom.xml -pl contexts/organization-access -am test
```

结果：organization-access 共 191 项，失败 0、错误 0、跳过 0。测试使用 Testcontainers PostgreSQL
18.4，覆盖领域、应用、Mapper、存储和自动配置。

`PersonnelScopeExclusivityConcurrencyTest` 使用真实
`PlatformRoleBindingApplicationService.bind` 与 `TenantMemberStatusApplicationService.enable` 并发竞争同一
主体。结果严格只有一条命令成功，数据库最终事实满足异或关系，证明共同主体锁不是仅靠候选过滤的乐观假设。

控制面真实 HTTP 命令：

```bash
./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -f backend/pom.xml \
  -pl bootstrap/control-plane-bootstrap -am \
  -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：`AuthenticationApiTest` 21 项，失败 0、错误 0、跳过 0；使用真实 PostgreSQL、Redis、Tomcat
随机端口和浏览器会话契约。

## 演示事实修正

演示种子不再让活动平台管理员同时成为租户成员：

- `platform-admin` 与 `platform-operator` 只保留平台管理员身份；
- `tenant-admin-candidate` 作为平台建立新租户时可选择的独立账户；
- `tenant-user` 作为租户普通成员和角色治理目标。

这不是生产数据自动修复。版本迁移发现历史冲突时失败关闭，由平台运营先显式停用一侧身份后重试。

## 全量验证

```bash
make architecture-guard
make validate
git diff --check
```

结果全部通过：

- 后端架构与前端产品门禁 28 项通过；复杂 SQL 均已登记，Java 内联 SQL 债务保持 15 个只可递减点；
- 全量 Maven／Testcontainers 回归通过；
- 前端 typecheck、lint、37 个测试文件共 180 项和 Vite 生产构建通过；
- 演示夹具 9 项、真实模型 5 项、恢复 8 项、容量韧性 4 项、交付 7 项通过。

## 六维质量判断

- 正确性：领域、应用、迁移、并发、持久化和 HTTP 共同证明双向互斥与普通成员治理，安全状态码稳定。
- 复杂度：只增加两个无状态领域规则和必要存储事实，没有新后端、补偿流程或推测性角色模型。
- 冗余：互斥和目标治理各只有一个领域事实来源；候选、应用和迁移分别承担体验、命令安全和升级预检，
  不复制授权决策。
- 性能：另一范围复核均在共同主体锁内执行有界 `EXISTS`／`COUNT`；没有加载全量成员或账户到 JVM。
- 架构：依赖方向、Mapper XML 所有权、Java 内联 SQL 递减门禁和安全异常边界全部通过自动门禁。
- 测试质量：红灯能分别证伪候选遗漏、迁移缺口、HTTP 500 和 XML 未登记；绿灯包含真实数据库并发而非
  只有 mock 单元测试。

## 工程助手插件证据

- 当前运行环境：Codex Desktop 本地仓库会话。
- 能力来源：已安装的 `engineering-assistant` 插件限定技能与代码图谱 MCP。
- 路由并显式加载：`engineering-assistant:tdd`。
- 使用的控制面资产：该技能 `SKILL.md` 与 `references/security-seams.md`，据此先固定公共安全 seam、记录
  红灯、最小实现并以真实基础设施回归。
- 本切片未生成独立 `StageRunResult` JSON 或等价阶段产物，阶段结果校验不适用；上述命令、测试报告、
  实现提交和六维判断构成可复现证据。

## 后续边界

下一纵切只实现平台拥有的租户管理员生命周期：平台侧目录、候选、分配／替换／停用、最后管理员与会话撤销、
统一 SPA，以及 Compose 和 Chrome 的平台管理员／租户管理员／普通用户角色任务。它必须继续完成
1024×768、390×844、真实 200% 缩放、键盘和 `prefers-reduced-motion`，完成前不申请 M6 人工复验。
