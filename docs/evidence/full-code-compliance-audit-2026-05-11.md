# 全量代码规范排查报告

Stage: evidence  
Category: architecture-code-compliance  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, senior backend engineer, engineering lead  
Approval: agent-approved  
Inputs: 用户人工中断反馈、`docs/30-project-harness.md`、`docs/32-control-surface.md`、`docs/45-details/01-backend-code-organization.md`、后端源码全量扫描  
Outputs: 不符合规范清单、恢复顺序、防再发门禁  
Depends On: `docs/evidence/ddd-architecture-drift-review-2026-05-11.md`  
Load When: architecture-drift/domain-recovery/backend-compliance  
Next: 后续功能扩展必须保持 DDD 守卫和 Maven 架构测试绿色

## 结论

初始排查时后端不满足 `production-targeted` 和 `springboot-enterprise-ddd` 规范。问题不是局部代码风格，而是 Tenant/IAM 与 Model 两个 bounded context 同时出现以下结构性偏移：

- repository port 用内部 `*Record` 替代聚合、实体和值对象。
- application service 承载领域规则、构造 repository record、生成领域 ID、处理 provider 密钥和调用生命周期。
- MyBatis-Plus repository impl 变成超大业务实现类，集中多个聚合、默认 seed、绑定策略、转换方法。
- bootstrap 直接写 Mapper/PO 或构造 repository record，绕过 application/domain 入口。
- 现有 Maven 架构测试可以通过，但不能发现上述 DDD 偏移。

当前已完成恢复，gate 更新为 `pass-after-ddd-recovery`。后续可以继续 S5.2 配额、计费、重试、路由等模型网关功能，但必须先设计领域模型并保持质量门禁绿色。

Tenant Access 人工复审补充恢复已完成：冗余 `TenantAccessApplicationService`、总仓储 `TenantAccessRepository`、万能实现 `MybatisPlusTenantAccessRepository`、`infrastructure/persistence/store/**` 和启动管理员初始化逻辑均已删除；controller 直接调用具体 application service；repository 按 Tenant、User/Credential、Authorization、Role、Permission、Menu、Provisioning、Audit 拆分并直接使用 Mapper/PO + converter；管理员一次性初始化进入 Flyway V5，并通过 `system_account` 领域规则保护。

## 扫描范围与命令

扫描范围：

- `backend/business/ai-platform-modules/*/src/main/java`
- `backend/business/ai-platform-modules/*/src/test/java`
- `backend/infrastructure/*/src/main/java`
- `backend/apps/ai-platform-boot/src/main`
- `backend/quality/ai-platform-architecture-tests`
- 与后端启动默认值关联的 `frontend/console-web/src/App.vue`

命令：

```bash
python3 scripts/harness_ddd_guard.py
mvn -f backend/pom.xml -pl quality/ai-platform-architecture-tests test
```

初始 DDD guard 结果：

```text
ddd_guard=blocked failure_count=22 warning_count=3
```

恢复后 DDD guard 结果：

```text
ddd_guard=pass warning_count=0
```

恢复后架构测试结果：`BackendMavenBaselineTest` 20 项通过，已覆盖 Maven baseline、domain 框架 import、`StpUtil` 边界、repository port 内部 record、fat application service、bootstrap direct mapper/PO、application service 直接依赖平台基础设施、tenant access 冗余入口、万能仓储、禁止 business persistence store、DDD 标记接口、启动管理员初始化和生产主配置本地 secret fallback 等关键偏移。

## P0 Blockers

| 编号 | 位置 | 不符合规范 | 影响 | 恢复方向 |
| --- | --- | --- | --- | --- |
| P0-01 | `backend/business/ai-platform-modules/tenant/src/main/java/org/fujay/ai/platform/tenant/domain/repository/TenantAccessRepository.java:64` | repository port 声明 `TenantRecord/UserRecord/CredentialRecord/RoleRecord/PermissionRecord/MenuRecord/RoleAccessRecord/Bootstrap*` | Tenant/IAM 聚合被数据快照替代，领域行为无处内聚 | 删除跨层 `*Record`，按 Tenant、User/Credential、Role、Permission、Menu、RoleBinding、Audit 建立领域对象和明确 query projection |
| P0-02 | `backend/business/ai-platform-modules/model/src/main/java/org/fujay/ai/platform/model/domain/repository/ModelProviderRepository.java:39` | repository port 声明 Provider、ProviderTypeOption、ModelOption、Model、Invocation record | Provider、Model、Invocation 生命周期和密钥边界被持久化快照污染 | 拆 `ProviderConfigRepository`、`PlatformModelCatalogRepository`、`ModelInvocationRepository`，返回聚合或只读投影 |
| P0-03 | `TenantAccessApplicationService.java:26` | 直接 import repository records，482 行、17 个 helper，串联登录、租户、角色、权限、菜单、审计 | application service 替代 Tenant/IAM 领域模型 | 下沉状态、绑定、菜单树、权限规则和审计行为到领域对象/领域服务 |
| P0-04 | `ModelProviderApplicationService.java:25` | 直接 import/构造 repository records，451 行、21 个 helper，在 application 生成 ID | 平台目录、provider、model 保存规则不在领域内聚 | 引入 Platform Catalog、ProviderConfig、ModelDefinition 聚合/工厂，application 只编排 command |
| P0-05 | `ModelInvocationApplicationService.java:58` | application 层完成模型查找、能力校验、provider 调用、成功/失败 InvocationRecord 构造和 ID 生成 | 调用生命周期、usage/cost 缺失原因、失败语义无法作为领域规则演进 | 建立 `ModelInvocation` 聚合和 `InvocationLifecycleService` |
| P0-06 | `MybatisPlusTenantAccessRepository.java:45` | 单类 803 行，注入 10 个 mapper，同时管理租户、用户、凭证、角色、权限、菜单、审计和 bootstrap | infrastructure 成为业务流程中心，后续权限/租户/审计变更不可控 | 按聚合拆 repository impl 和 converter |
| P0-07 | `MybatisPlusTenantAccessRepository.java:49` | `DEFAULT_PERMISSIONS`、`DEFAULT_MENUS`、`bootstrapAccessControl` 在 repository impl 内 | 默认 IAM 目录事实源落在持久化实现，无法审计、版本化和领域测试 | 迁出为 IAM bootstrap use case + domain catalog policy |
| P0-08 | `MybatisPlusModelProviderRepository.java:28` | 单类 386 行，同时处理 provider、model、平台目录、invocation，9 个私有 `to*` 转换方法 | 多聚合持久化和业务映射混在一个实现类 | 拆 provider、platform catalog、invocation repository/query；转换迁入专门 mapper/converter |
| P0-09 | `backend/quality/ai-platform-architecture-tests/src/test/java/org/fujay/ai/platform/architecture/BackendMavenBaselineTest.java:133` | 架构测试未覆盖本轮 DDD blocker | 测试通过会制造质量假象 | 将 `harness_ddd_guard.py` 规则迁入 Java 架构测试或纳入固定质量门禁 |

## P1 High

| 编号 | 位置 | 不符合规范 | 影响 | 恢复方向 |
| --- | --- | --- | --- | --- |
| P1-01 | `ModelProviderApplicationService.java:8`、`ModelInvocationApplicationService.java:5` | model application service 直接依赖 `AiGatewayFacade`、`SecretCipher`、`SecuritySessionFacade` | 业务用例层接触平台 AI 和 provider credential 明文 | 在 model context 定义 `ModelGatewayPort`、`ProviderSecretPort`、`CurrentPrincipalPort`，由 infrastructure adapter 适配平台能力 |
| P1-02 | `ModelProviderApplicationService.java:68`、`PlatformModelCatalogController.java:40` | provider 密钥、模型目录、平台目录写操作缺少审计端口 | 高风险配置变更不可追溯 | 写操作统一发布 audit event 或调用 audit port，只记录 last4/摘要 |
| P1-03 | `PlatformModelCatalogBootstrapRunner.java:3` | bootstrap 直接 import MyBatis Mapper/PO，并写死 OpenAI/SiliconFlow 默认目录 | 平台模型目录事实绕过 domain/application | runner 只触发 bootstrap use case，默认目录进入 domain catalog policy |
| P1-04 | `OpenAiProviderBootstrapRunner.java:63` | bootstrap 生成 provider/model ID、加密密钥并构造 `ProviderRecord/ModelRecord` | 启动配置创建 provider 绕过权限、审计和一致性入口 | 改为调用受控 application command，并补启动来源审计和幂等策略 |
| P1-05 | `backend/infrastructure/ai-platform-persistence/src/main/java/org/fujay/ai/platform/persistence/config/MybatisPlusTenantConfiguration.java:20` | MyBatis-Plus 只注册租户插件，缺分页、乐观锁、非法 SQL、防全表更新保护 | 分页、并发和误更新保护不足 | 补全插件并用架构/集成测试锁定顺序 |
| P1-06 | `backend/apps/ai-platform-boot/src/main/resources/application.yml:43` | local secret、默认租户 `demo`、默认 admin password 作为启动默认值 | production-targeted 项目容易把本地默认当完成态 | 拆 local/production profile，生产缺少 master key/admin password/provider key 时 fail fast |

## P2 Medium

| 编号 | 位置 | 不符合规范 | 影响 | 恢复方向 |
| --- | --- | --- | --- | --- |
| P2-01 | `ModelProviderApplicationService.java:262`、`ModelInvocationApplicationService.java:102` | 日志和失败记录使用 `ex.getMessage()` | provider 返回体可能包含敏感调试信息 | 持久化稳定错误码和脱敏摘要，原始异常只进入受控观测链路 |
| P2-02 | `ModelProviderApplicationServiceTest.java:39`、`ModelInvocationApplicationServiceTest.java:40` | `InMemoryRepository` 测试强化贫血 repository record 合同 | 测试无法暴露 MyBatis、多租户、事务、审计真实问题 | 领域测试测聚合不变量，application 测 mock port，repository 用真实 mapper/schema |
| P2-03 | `AuthApiSmokeTest.java:22` | 424 行 smoke test 绑定本机 PostgreSQL/Redis，一条链覆盖登录、IAM、模型目录、provider、权限回退 | 失败定位困难，容易把 smoke 通过误认为领域质量通过 | 拆 auth/session、tenant lifecycle、IAM RBAC、model catalog、provider secret 集成测试 |
| P2-04 | `frontend/console-web/src/App.vue:221` | 前端登录表单默认 `tenantCode: 'demo'` | 生产态容易保留演示默认值 | 仅 local/dev profile 保留演示默认，生产构建不内置默认租户 |

## 内部 record 合理性判断

合理：

- `interfaces/request`、`interfaces/response`、`application/command`、`application/dto` 中用于边界传输的 record。
- 方法内部或私有局部 record，且不跨层传播、不承载业务事实源。

不合理：

- `domain/repository` 内部 `*Record` 作为 port 合同传播到 application service。
- repository impl 中用 private record 承载默认菜单、默认权限、默认模型目录等业务事实。
- bootstrap 中通过 private/default record 写平台目录，并直接调用 Mapper/PO。

判断标准：record 不是问题本身，问题是它是否替代了聚合/实体/值对象，或者把业务事实放进了 repository/bootstrap 这类技术层。

## 偏移产生原因

1. 早期切片追求纵向可运行，service/repository smoke 先行，领域聚合测试没有先固定业务不变量。
2. repository port 被当作数据访问 DTO 合同，内部 `*Record` 逐步取代领域对象。
3. application service 为了快速串通前后端，持续吸收状态校验、唯一性、绑定、调用生命周期、日志和 DTO 映射。
4. bootstrap 和默认目录为了便捷启动直接写表，导致平台事实源绕过 domain/application。
5. 原有架构测试只防框架类型泄漏和部分包边界，没有防 fat service、内部 record、超大 repository impl。
6. 新功能推进时沿用“加字段、补 DTO、补转换”的路径，持续放大贫血模型。

## 恢复优先级

1. 冻结 S5.2 功能开发，不再给 repository record、application service、超大 repository impl 加字段。
2. 先补质量门禁：保留 `scripts/harness_ddd_guard.py`，并补 Java 架构测试覆盖本报告 P0/P1 规则。
3. 先恢复 Tenant/IAM：拆 Tenant、User/Credential、Role、Permission、Menu、RoleBinding、Audit 聚合与领域服务。
4. 再恢复 Model：拆 Platform Catalog、ProviderConfig、ModelDefinition、ModelInvocation、ProviderSecret、InvocationLifecycle。
5. 重写 repository port：只保存/加载聚合或返回明确 query projection，禁止跨层 `*Record`。
6. 拆 repository impl：按聚合或 query 职责拆分，PO converter 独立，repository impl 不承载 seed 或业务规则。
7. 修复 bootstrap/config：启动 runner 只调用受控 use case；生产 profile 对默认密码/密钥 fail fast。
8. 最后恢复 S5.2，并按真实领域对象添加配额、计费、路由、重试。

## 恢复验收标准

- `python3 scripts/harness_ddd_guard.py` 通过。
- Maven 架构测试能拦截 repository port 内部 `*Record`、fat application service、bootstrap direct mapper/PO、application 构造 repository record。
- application service 不直接构造 repository record、不生成领域 ID、不解密 provider credential、不直接调用平台 AI facade。
- 每个 repository impl 聚焦一个聚合或明确 query，超大多聚合实现类消失。
- Tenant/IAM 和 Model 两个 context 有领域测试覆盖状态、唯一性、绑定、密钥边界、调用生命周期。
- local/demo 默认值只存在 local profile，production profile 必须显式配置密钥和管理员密码。

最新专项验收结果：

- `TenantAccessApplicationService`、`TenantAccessRepository`、`MybatisPlusTenantAccessRepository`、`LocalDevBootstrapRunner`、`LocalBootstrapProperties` 已从 tenant 源码删除。
- `TenantApplicationAccessSpec` 承接租户应用访问业务规则，application 只编排用例。
- `ai-platform-shared` 增加 `AggregateRoot`、`Entity`、`ValueObject` 标记接口；Tenant/IAM 和 Model domain 类型已显式声明聚合根、实体或值对象语义。
- `ConfiguredModel` 从 `domain/aggregate` 调整到 `domain/model` 并实现 `Entity`，未被引用的 `ModelProviderConfig` 已删除，避免 domain model 继续表达不清。
- tenant `infrastructure/persistence/store/**` 已删除，repository 拆分后不再允许通过 store 二次聚合持久化能力。
- `TenantUserTest` 覆盖管理员不可删除、不可禁用、不可改角色、只能改密码、天然拥有平台权限和普通用户权限不短路。
- `AuthApiSmokeTest` 覆盖管理员改密码成功、`PLATFORM_ADMIN` 角色权限修改被拒绝、管理员仍可读取角色列表。
- 浏览器验收覆盖 `demo/admin` 登录、菜单可见、角色权限弹窗保存被拒绝且平台管理员权限保持不变。

## 2026-05-12 后端继续排查补充

继续排查覆盖：

- business application/domain/controller import 边界。
- MyBatis-Plus、Spring AI、Sa-Token 直接使用边界。
- repository port、repository impl、persistence store 超大类与万能类。
- 主配置中本地 master key fallback。

新增发现和修复：

- store 层本身是不合理的折中：repository 已按业务拆分后再调用 store，会把巨型 repository 的复杂度迁移到另一层。已删除 tenant 下所有 `infrastructure/persistence/store/**`，由拆分后的 repository 直接持有 Mapper/PO 持久化逻辑，PO 转换进入纯 converter。
- domain model 语义仍需机械化约束。已在 shared 增加 DDD 标记接口，并要求 domain aggregate/model/value 类型显式实现 `AggregateRoot`、`Entity` 或 `ValueObject`。
- 主 `application.yml` 与 `SecretCipher` 内置 `ai-platform-local-secret-change-me`。已移除主配置和代码 fallback，本地默认仅保留在 `application-local.yml`；生产或默认 profile 必须显式提供 `AI_PLATFORM_SECRET_MASTER_KEY`。
- `BackendMavenBaselineTest` 已新增禁止 store、DDD marker 和 production secret fallback 架构测试。
- `scripts/harness_ddd_guard.py` 已同步增加禁止 store、DDD marker 和 master key fallback 检查，避免只依赖单一 Java 测试发现问题。
