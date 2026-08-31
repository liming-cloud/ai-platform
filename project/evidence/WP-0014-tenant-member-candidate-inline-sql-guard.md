# WP-0014 租户成员候选内联 SQL 门禁与真实分页证据

## 范围与结论

- 实现提交：`ceb946aa9`。
- 将 `JdbcTenantMemberCandidateDirectory` 中的生产 Java 内联 SQL 迁移到
  `TenantMemberCandidateMapper.xml`，基础设施适配器只负责分页参数、投影映射和搜索词转义。
- 普通列表使用 MyBatis-Plus `IPage` 与 PostgreSQL 服务器端计数／分页；候选仍只返回活动账户，
  并按当前租户排除已有成员。
- 后端架构门禁新增 `javaInlineSqlFiles` 递减基线，目标实现已退出债务清单；现存 15 个历史文件只可递减，
  新增同类 Java 内联 SQL 会失败。
- CI 在项目治理验证之后显式执行 `make architecture-guard`，避免架构规则只在本地存在。
- 本切片不改变平台管理员、租户管理员和普通用户的业务授权语义，不修改 HTTP 契约或前端。
  是否从租户成员候选中排除平台管理员，以及建立成员时是否必须再次校验身份互斥，仍需独立 ADR 决定。

## 代码图谱与影响面

编码前使用代码图谱探索 `JdbcTenantMemberCandidateDirectory`、
`TenantMemberCandidateDirectory`、`OrganizationAccessPersistenceAutoConfiguration` 和既有
`ModelDeploymentMapper.findPage` 分页模式。结果确认：

- 候选查询只有 organization-access 自动配置这一处基础设施装配调用方；
- 应用服务只依赖 `TenantMemberCandidateDirectory` 端口，替换适配器不改变应用或接口层；
- 既有普通列表模式是在 Mapper XML 接收 `IPage<?>`，仓储用 `Page` 返回总数和当前页；
- 影响范围局限于 organization-access 成员候选持久化、自动配置、架构门禁和 CI。

因此未扩大到身份所有权、角色分配、会话撤销或 UI 重构。

## 红绿 TDD

红灯命令：

```bash
python3 -m unittest \
  scripts.quality.test_backend_architecture_guard.BackendArchitectureGuardTest.test_new_java_inline_sql_is_rejected
```

首次结果：`FAIL`。门禁对临时新增的 `JdbcClient.sql("SELECT ...")` 返回空错误集，证明原规则只拦截
MyBatis SQL 注解，无法识别 Java 内联 SQL。

实现 `JAVA_SQL_CLIENT_REFERENCE`、`JAVA_SQL_EXECUTION_CALL` 和 `javaInlineSqlFiles` 后，同一命令转绿；
完整架构门禁回归结果为 28 项通过，扫描摘要为：

```text
架构债务递减门禁通过：SQL 文件 0，Java 内联 SQL 文件 15，UUID 文件 0，
运行时非启动文件 0，Sa-Token 越界文件 0，超预算包 0，旧 api 文件 0，
旧 adapter 文件 0，runtimes 文件 0，游标列表契约 0，手工列表分页 3
```

## 真实 PostgreSQL 与装配回归

定向真实数据库命令：

```bash
./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -f backend/pom.xml \
  -pl contexts/organization-access -am \
  -Dtest=PostgresTenantMemberCandidateDirectoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：通过。Testcontainers PostgreSQL 18.4 证明：

- 当前租户已有成员和停用账户不会进入候选；
- 只属于其他租户的活动账户仍可成为当前租户候选；
- 第 1／2 页、总数、总页数和稳定排序由数据库返回；
- 搜索词 `_` 被按字面值处理，不会扩大为 SQL `LIKE` 通配符。

完整 organization-access 模块首次回归暴露自动配置夹具未提供新 Mapper 的失败；补齐
`TenantMemberCandidateMapper` 测试 Bean 后，`OrganizationAccessAutoConfigurationTest` 和下列完整命令均转绿：

```bash
./mvnw -Dmaven.repo.local=/tmp/ai-mesh-m2 -q -f backend/pom.xml \
  -pl contexts/organization-access -am test
```

## 全量验证

```bash
make architecture-guard
make project-verify
make validate
git diff --check
```

结果全部通过。`make validate` 包含全量 Maven／真实 Testcontainers 回归、前端类型检查和 lint、
37 个前端测试文件共 180 项、生产构建，以及演示、真实模型、恢复、容量和交付操作门禁。

## 六维质量判断

- 正确性：红灯证明旧门禁失效，真实数据库覆盖范围、分页、计数、排序和转义。
- 安全性：SQL 只返回 `subject_id`、`login_name`、`status`，按当前租户排除成员，不读取凭据。
- 可靠性：分页插件在真实 PostgreSQL 上执行，自动配置与完整上下文回归通过。
- 可维护性：复杂 SQL 进入受登记的 Mapper XML，Java 适配器保持窄职责。
- 性能：筛选、排序、计数和分页均在数据库端执行，不把全量候选加载到 JVM。
- 可运营性：债务数量可见、只可递减，CI 显式执行门禁；剩余 15 个历史点可逐项迁移。

## 边界

本证据不代表 `HITL-0008-M6-REJECTION` 已关闭，不申请 M6 人工复验，也不激活 M7。下一切片应先以
新 ADR 固定平台账户、平台角色绑定与租户成员资格的互斥／所有权语义，再修改候选和命令侧授权规则。
