# WP-0014：租户角色目录与自定义角色建立

## 范围与结论

本切片落实 ADR-0057 的角色目录第一阶段：六个拥有真实后端授权点的内置角色由领域目录定义；认证投影
读取角色绑定事实后再展开权限，不再在 SQL 中硬编码管理员角色。租户访问管理员可通过 token 派生上下文
建立包含已发布权限的自定义角色，角色与权限集合在真实 PostgreSQL 中原子保存。

成员停用、角色修改／停用、自定义角色绑定、角色影响成员的会话撤销、服务端分页角色目录和管理控制台仍未
实施，不能将本切片视为完整的账户权限产品闭环。

## 代码图谱与影响

- 代码图谱探索 `PostgresTenantMembershipRepository`、`TenantMembershipMapper`、`TenantRoleBindingMapper`、
  `TenantBuiltInRole` 与 `TenantRoleBindingService`，确认会话签发只消费 `TenantMembershipRepository` 的权限
  投影，因而角色权限只能在该受限读取 seam 展开。
- `TenantBuiltInRole` 影响分析确认新增目录值还会触达角色绑定应用服务、真实 PostgreSQL 存储和控制面
  HTTP 回归；实现同步覆盖这些路径。
- 控制面 HTTP 红测暴露所有上下文 Flyway 位置会被同一应用加载。新增 `V202608100004` 前曾与
  model-runtime 的 `V202608100002` 冲突；自动门禁现拒绝跨上下文重复迁移版本。

## TDD 红绿记录

1. 红：

   ```bash
   ./mvnw -q -pl backend/contexts/organization-access -am \
     -Dtest=PostgresSubjectAuthenticationRepositoryTest \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   新增 `TENANT_MODEL_MANAGER` 绑定反例后，数据库检查约束只允许旧管理员角色，真实 PostgreSQL 拒绝写入。

2. 绿：同一测试在扩展迁移和认证投影改为领域目录展开后通过，确认重新签发会话包含
   `model:read` 与 `model:write`，同时保留遗留直授权限的只读兼容。

3. 红：

   ```bash
   python3 -m unittest \
     scripts.quality.test_backend_architecture_guard.BackendArchitectureGuardTest.test_duplicate_flyway_migration_version_is_rejected_across_contexts
   ```

   迁移重复版本尚未被门禁识别，断言失败。

4. 绿：同一门禁测试及 `python3 scripts/quality/backend_architecture_guard.py verify` 通过；重复版本成为即时失败，
   不依赖债务基线或允许清单豁免。

5. 红：

   ```bash
   ./mvnw -q -pl backend/contexts/organization-access -am \
     -Dtest=TenantCustomRoleApplicationServiceTest \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   自定义角色命令、存储端口和应用服务尚不存在，测试编译失败。

6. 红：

   ```bash
   ./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am \
     -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test
   ```

   自定义角色 HTTP 回归先得到 `404 NOT_FOUND`；公开入口和自动配置尚未存在。

7. 绿：

   ```bash
   ./mvnw -q -pl backend/contexts/organization-access -am \
     -Dtest=TenantBuiltInRoleTest,TenantCustomRoleTest,TenantMembershipTest,\
TenantRoleBindingServiceTest,TenantRoleBindingApplicationServiceTest,\
PostgresTenantRoleBindingStoreTest,PostgresSubjectAuthenticationRepositoryTest,\
TenantCustomRoleApplicationServiceTest,PostgresTenantCustomRoleStoreTest \
     -Dsurefire.failIfNoSpecifiedTests=false test
   ./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am \
     -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test
   python3 -m unittest scripts.quality.test_backend_architecture_guard
   python3 scripts/quality/backend_architecture_guard.py verify
   ```

   纯领域、应用授权、真实 PostgreSQL、Redis／控制面 HTTP 和 20 项架构门禁单元测试均通过。

## 安全与一致性反例矩阵

| 反例 | 证据 |
| --- | --- |
| SQL 中硬编码某个内置角色的权限 | 认证投影先读取角色码，再由 `TenantBuiltInRole` 展开；模型角色 PostgreSQL 回归覆盖。 |
| 未发布或平台级权限进入自定义角色 | `TenantCustomRole` 只接受 `TenantPublishedPermission` 目录，领域反例拒绝。 |
| 空权限、重复 HTTP 权限或伪造租户／主体／会话 | 严格请求只接受名称和去重权限数组；上下文完全来自 `TrustedRequestContext`。 |
| 同租户活动角色重名 | 应用层预检配合 PostgreSQL 部分唯一索引，持久化并发约束不依赖前端。 |
| 迁移版本跨上下文重复 | 全局 Flyway 版本门禁和已恢复的 control-plane 启动回归共同证明失败关闭。 |

## 六维审查

| 维度 | 结论 |
| --- | --- |
| 正确性 | 内置角色、认证权限投影、自定义角色创建与权限集合保存均有真实数据库及 HTTP 回归。 |
| 复杂度 | 保留既有内置绑定表；自定义角色只实现已授权的建立，不提前引入未验收的绑定状态机。 |
| 冗余 | `TenantPublishedPermission` 是目录约束而非第二套 ACL；业务服务仍各自验证真实权限码。 |
| 性能 | 认证按成员读取有界角色码；活动名称由部分唯一索引约束，不做全表扫描。 |
| 架构 | 接口、应用、领域和基础设施分层；应用构造器不注入领域实体或值对象，门禁通过。 |
| 测试质量 | 记录实际红灯，使用 PostgreSQL、Redis、HTTP 和自动配置，不以 mock 替代迁移或认证验证。 |

## 提交与状态

本证据绑定本次实现提交。WP-0014 和 M6 保持进行中，不进入 M7。
