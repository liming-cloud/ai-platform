# WP-0014 控制面自动验收修复证据

## 范围

本切片只修复 M6 企业产品基础自动验收已经稳定复现的两处控制面启动／认证失效：

- `TenantCustomRoleDirectoryApplicationService` 标记为 `final`，与其 `@Transactional` 的 Spring CGLIB 代理要求冲突；
- 演示重复种子在版本化 `PlatformAccessGrant` 迁移之后执行，仍只写入旧授权事实，导致平台登录无法读取活动角色绑定而返回 `401`。

不新增账户、角色、导航、厂商、模型或 SPA 能力；M6 与 WP-0014 继续进行中，M7 不激活。

## 设计、诊断与代码图谱影响

代码图谱确认 `OrganizationAccessCoreAutoConfiguration` 将
`TenantCustomRoleDirectoryApplicationService` 作为具体类装配，类方法带有 `@Transactional(readOnly = true)`，
控制面因而必须创建 CGLIB 子类代理。去除 `final` 是保持既有应用服务和事务边界不变的最小修复。

代码图谱还确认 `PlatformAuthenticationController` 经
`PlatformAuthenticationService` 读取 `PlatformRoleBindingRepository` 的活动角色投影；缺少绑定即按无效凭据
失败关闭。Flyway 先执行版本化角色迁移、后执行重复演示种子，因此种子必须直接写入
`platform_subject_authorization` 和 `platform_role_binding`。新行使用 `ON CONFLICT DO NOTHING`，保持重复种子幂等。

关键符号／文件：`TenantCustomRoleDirectoryApplicationService`、
`OrganizationAccessCoreAutoConfiguration`、`PlatformAuthenticationService`、
`PlatformAuthenticationController`、`PostgresPlatformRoleBindingRepository` 与
`R__seed_demo_tenants.sql`。代码图谱探索给出的直接影响面已限定修改；本次没有变更共享契约，故无需额外的
非局部 `impact` 遍历。

## TDD、真实基础设施与 HTTP 回归

先新增 CGLIB 代理装配回归并执行：

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl contexts/organization-access -am \
  -Dtest=TenantCustomRoleDirectoryApplicationServiceProxyTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

红灯：Spring 报告无法为 `final` 的
`TenantCustomRoleDirectoryApplicationService` 生成 CGLIB 子类，证明测试先于修复。
移除 `final` 后，同一命令通过。

随后独立复现平台 HTTP 认证回归：

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn \
  -pl bootstrap/control-plane-bootstrap -am \
  -Dtest=AuthenticationApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

修复前稳定出现 3 项失败和 5 项错误：平台演示主体登录返回 `401`。补齐迁移后角色投影种子后，同一命令在
真实 PostgreSQL、Redis、Flyway 和 Tomcat 环境中通过，`AuthenticationApiTest` 的 21 项测试全部成功。

最终从 `backend` 执行：

```bash
/tmp/codex-maven-3.9.16/apache-maven-3.9.16/bin/mvn test
```

结果：整个反应堆 `BUILD SUCCESS`（2026-08-11 13:43:06 +08:00）。其中
`organization-access`、`model-runtime`、`control-plane-bootstrap`、`query-bootstrap` 和
`ingestion-bootstrap` 均通过；`control-plane-bootstrap` 共 43 项测试通过，覆盖真实基础设施和 HTTP 认证闭环。

## 性能工作负载测量

在预热后的本机工作区，以新增代理回归作为固定工作负载重跑同一 Maven 反应堆命令。单项测试耗时 `0.200 s`，
四模块反应堆耗时 `2.552 s`（进程墙钟 `3.50 s`）。本切片没有新增查询、缓存、批处理或浏览器加载路径；该测量
仅证明代理装配回归的局部工作负载未出现异常，不能替代后续产品性能基准。

## 浏览器与安全审查

本切片没有改变浏览器代码或请求契约，不能以 HTTP 自动测试冒充正式浏览器验收。受信任 TLS 部署环境仍不可用；
依据 ADR-0048 既定边界，未安装 CA、未忽略证书错误、未以 HTTP 或静态页面替代浏览器角色任务。该正式 HTTPS
浏览器回归仍是 M6 自动验收前的待续证项。

六维审查未发现阻塞项：

| 维度 | 结论 |
| --- | --- |
| 正确性 | 代理回归先红后绿；平台 HTTP 认证从稳定失败恢复为 21 项通过。 |
| 安全 | 认证仍只接受活动角色绑定，缺失绑定继续失败关闭；不写入或暴露任何密钥。 |
| 架构 | 保持现有应用服务事务代理和认证投影边界，不新增跨上下文依赖。 |
| 数据 | 重复种子直接写入迁移后投影，且由 `ON CONFLICT DO NOTHING` 保持幂等。 |
| 性能 | 不增加查询、批处理或缓存；仅恢复既有分页读取服务的代理装配。 |
| 注意力保护 | 无前端交互变更，不引入累加分页、额外主操作或嵌套 Dialog。 |

M6 与 WP-0014 继续进行中；本证据不构成 M6 自动验收、人工评审或 M7 准入。
