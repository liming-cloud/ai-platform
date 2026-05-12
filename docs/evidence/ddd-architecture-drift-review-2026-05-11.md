# DDD 架构偏移复核

Stage: evidence  
Category: architecture-drift  
Status: current  
Review Status: agent-reviewed  
Reviewers: architect, product engineer, qa  
Approval: agent-approved  
Inputs: 用户人工中断反馈、`docs/30-project-harness.md`、`docs/45-details/01-backend-code-organization.md`、源码扫描  
Outputs: DDD 偏移结论、恢复路径、防再发控制面  
Depends On: `docs/32-control-surface.md`  
Load When: architecture-drift/domain-recovery/before-implementation  
Next: 将 DDD 守卫作为后续 S5 Model Gateway 功能扩展的强制质量门禁

## 结论

2026-05-11 复核时后端存在 blocker 级 DDD 架构偏移。当前已完成恢复：业务规则已从胖 application service、repository port 内部 `record` 和超大 repository impl 中收敛回聚合、领域对象、领域服务、application port 和聚焦 repository 实现。

S5.2 配额、重试/路由、成本计费等功能可以恢复推进，但必须先设计领域模型，并保持 DDD 守卫和 Maven 架构测试绿色。

## Tenant Access 人工复审补充恢复

针对人工复审指出的 tenant access 专项问题，当前代码已完成以下修复：

- `TenantAccessApplicationService` 已删除，controller 直接调用 `TenantAuthApplicationService`、`TenantAdminApplicationService`、`IamAdminApplicationService`，保留原 API 路径和响应语义。
- `TenantApplicationSupport` 已从 application support 工具迁移为 `TenantApplicationAccessSpec`，业务判断归入 domain specification。
- `TenantAccessRepository` 和 `MybatisPlusTenantAccessRepository` 已删除，改为 `TenantRepository`、`TenantUserRepository`、`TenantAuthorizationRepository`、`IamRoleRepository`、`IamPermissionRepository`、`IamMenuRepository`、`TenantProvisioningRepository`、`TenantAuditRepository` 等聚焦端口和 MyBatis-Plus 实现。
- `domain/model` 下纯 repository 参数 record 已移除或改造，command 持久化入口接收 `Tenant`、`TenantUser`、`UserCredential`、`IamRole`、`IamPermission`、`IamMenu` 等领域对象。
- tenant 启动时自动创建管理员的 `LocalDevBootstrapRunner` 和 `LocalBootstrapProperties` 已删除，管理员初始化进入 Flyway `V5__tenant_admin_sql_seed_and_system_account.sql`。
- 管理员账号通过 `system_account` 持久化标识保护：不可删除、不可禁用、不可改角色权限，允许修改密码，并在权限判断中天然拥有平台权限。
- tenant application/domain 不再直接依赖 `ai-platform-security` 实现细节，当前身份、登录令牌、密码哈希和 ID 生成都通过本模块 application port，由 infrastructure adapter 适配平台 facade。

本次专项恢复的偏移原因是：此前恢复更关注 Model Gateway 和通用 DDD guard，Tenant/IAM 仍保留了“总入口 service + store 包装 + 启动便利初始化”的妥协结构；应用服务 smoke 测试也没有覆盖系统管理员不可变规则。恢复后新增 Maven 架构测试约束冗余总入口、万能仓储、tenant 启动管理员初始化和 business application/domain 对平台基础设施的直接 import，防止同类结构再次进入主干。

## 扫描证据

命令：

```bash
python3 scripts/harness_ddd_guard.py
```

初始结果：`ddd_guard=blocked failure_count=22 warning_count=3`。

恢复后结果：`ddd_guard=pass warning_count=0`。

关键命中：

| 类型 | 文件 | 证据 |
| --- | --- | --- |
| Fat application service | `TenantAccessApplicationService` | 482 行、17 个私有 helper，直接消费 `TenantRecord/UserRecord/RoleRecord/MenuRecord` 等仓储 record |
| Fat application service | `ModelProviderApplicationService` | 451 行、21 个私有 helper，构造 `ProviderRecord/ModelRecord/InvocationRecord`，并在 application service 中生成 ID |
| Invocation 领域散落 | `ModelInvocationApplicationService` | 构造 `InvocationRecord`、生成 ID、组装调用记录，领域对象未承载调用生命周期 |
| Repository port 贫血化 | `TenantAccessRepository` | 在 repository 端口内声明 9 个内部 record，包含租户、用户、角色、权限、菜单、bootstrap 输入 |
| Repository port 贫血化 | `ModelProviderRepository` | 在 repository 端口内声明 5 个内部 record，包含 provider、model、platform option、invocation |
| 超大 repository impl | `MybatisPlusTenantAccessRepository` | 803 行、同时承载租户、用户、凭证、角色、权限、菜单、审计、bootstrap、默认菜单/权限 seed |
| 超大 repository impl | `MybatisPlusModelProviderRepository` | 386 行、9 个私有转换方法，持久化映射逻辑集中在 repository impl |
| Repository impl 内部 record | `MybatisPlusTenantAccessRepository` | 内部 `DefaultPermission`、`DefaultMenu` 让默认目录事实源落在持久化实现类里 |
| Application 直接依赖平台 AI/密钥能力 | `ModelProviderApplicationService`、`ModelInvocationApplicationService` | 直接 import `AiGatewayFacade`、`SecretCipher`，应用层解密 provider credential 并调用平台 AI facade |
| Bootstrap 绕过 application/domain | `PlatformModelCatalogBootstrapRunner`、`OpenAiProviderBootstrapRunner` | 直接 import Mapper/PO 或构造 repository record 写入模型目录和默认 provider |

## 偏移原因

1. 早期切片过度追求可运行纵切，测试聚焦 service/repository smoke，没有先用领域测试固定聚合行为。
2. 控制面只禁止了框架类型泄漏、PO/Mapper 泄漏和 fake 完成态，没有明确禁止 fat application service、repository port 内部 `*Record`、超大 repository impl。
3. `Repository` 被当成数据访问 DTO 端口使用，内部 `record` 逐步替代了真正的聚合、实体和值对象。
4. Bootstrap、默认菜单/权限、状态规则、唯一性检查、绑定规则被直接放进 repository impl，导致 infrastructure 承载业务事实。
5. 新功能推进时以“加字段、补 DTO、补转换”为主路径，继续扩大既有贫血模型，而不是先恢复领域模型再扩展能力。

## 已收敛红线

- `domain/repository` 禁止声明对外传播的内部 `record`，尤其是 `*Record`。
- `application/service` 只做用例编排、事务边界、权限/租户上下文协调；不得构造 repository record、不得承载领域校验、不得生成领域 ID。
- `infrastructure/persistence/repository` 只实现单一聚合仓储端口；不得同时承载多个聚合、bootstrap seed、默认目录、业务绑定策略。
- PO 到领域对象的转换不得隐藏在超大 repository impl 私有方法堆里；需要拆到专门 mapper/converter，并只做结构映射，不写业务规则。
- 内部 `record` 只允许用于局部、私有、不可跨层传播的数据结构；`Command/DTO/Response` record 合理，repository port 内部 record 不合理。

## 恢复路径

1. 先冻结 S5.2 功能开发，停止继续给 `ModelProviderApplicationService` 和 repository record 加字段。
2. 以 Tenant/IAM 和 Model 两个 bounded context 为单位重新画聚合边界：
   - Tenant/IAM：Tenant、User、Credential、Role、Permission、Menu、RoleBinding、Audit 行为归位。
   - Model：PlatformProviderType、PlatformModelOption、Provider、Credential、Model、Invocation 行为归位。
3. 为每个聚合补领域测试，先覆盖状态、唯一性、能力校验、绑定规则、密钥边界、调用生命周期。
4. 重写 repository port 形态：保存/加载聚合或明确的领域查询投影，不再暴露 repository 内部 `*Record`。
5. 拆分超大 repository impl：按聚合或查询职责拆分实现，bootstrap seed 移出 repository impl。
6. application service 退回用例编排层，再恢复 S5 Model Gateway 后续功能。

## 防再发控制

新增脚本：

```bash
python3 scripts/harness_ddd_guard.py
```

当前该脚本必须通过，作为 DDD 恢复后的强制质量门禁。脚本已覆盖 fat application service、repository port 内部 record、application 构造 repository record、application 生成领域 ID、model application 直接依赖平台 AI/密钥能力、bootstrap direct Mapper/PO、超大 repository impl、repository impl 默认 seed、禁止 persistence store 和 DDD 标记接口。Maven 架构测试同步覆盖 tenant access 冗余入口、万能仓储、启动管理员初始化、business application/domain 直接依赖平台 infrastructure/facade、禁止 store 层和 domain 类型语义标记的问题。后续与后端测试、前端测试、浏览器验收一起运行。

## 最新验证

```bash
python3 scripts/harness_ddd_guard.py
mvn -f backend/pom.xml test
pnpm --dir frontend/console-web test
pnpm --dir frontend/console-web build
```

结果：

- DDD guard：`ddd_guard=pass warning_count=0`。
- 后端全量 Maven：13 个 reactor 模块全部 `SUCCESS`，`BackendMavenBaselineTest` 20 项通过，`AuthApiSmokeTest` 2 项通过。
- 前端 Vitest：1 个测试文件、2 项测试通过。
- 前端构建：`vue-tsc --noEmit && vite build` 通过。
- 浏览器验收：`demo/admin` 登录后拥有平台菜单；尝试修改 `PLATFORM_ADMIN` 角色权限返回业务拒绝，角色权限数量保持不变，截图证据为 `.codex-runtime/tenant-ddd-role-protection.png`。
