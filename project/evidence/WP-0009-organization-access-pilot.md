# WP-0009 组织权限经典四层试点证据

## 变更边界

- 删除组织权限上下文的 `api` 与 `adapter`。
- 按认证、成员、授权能力重组 `interfaces/application/domain/infrastructure`。
- Controller 直接依赖具体应用服务。
- 成员聚合仓储归入领域层，密码验证归入应用层外部端口。
- 领域层使用本上下文的成员、主体和权限值对象，不依赖平台安全模型。
- 持久化实体固定使用输入雪花标识。
- 上下文通过三个 Spring Boot 自动配置注册持久化、核心和 Web 能力。
- 删除控制面 bootstrap 中的组织权限集中 Bean 配置。

## 红灯与修复

首次全仓回归时，组织权限自动配置仅因传递依赖出现在模型运行时测试类路径中便被
激活，导致缺少会话端口的无关测试启动失败。

修复后能力默认不启用；只有
`foundation.organization-access.enabled=true` 时才装配。启用后缺少会话、标识生成器
或持久化依赖仍会直接启动失败。控制面明确开启该能力。

第二次控制面测试发现 Web 自动配置的 `ConditionalOnBean` 在常规业务配置注册前求值，
导致认证 Controller 未被导入。现改为能力启用后显式导入入口，由构造器依赖承担完整性
校验，避免条件求值顺序形成静默缺失。

## 绿灯

以下命令通过：

```bash
./mvnw -q -pl backend/contexts/organization-access,backend/contexts/model-runtime -am test
./mvnw -q -pl backend/runtimes/control-plane -am \
  -Dtest=AuthenticationApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q test
python3 scripts/quality/backend_architecture_guard.py
python3 -m unittest \
  scripts.quality.test_backend_architecture_guard \
  scripts.quality.test_project_governance
git diff --check
```

已验证：

- 组织权限模块 16 项测试通过。
- 认证接口在真实 PostgreSQL、Redis、Sa-Token 组合下完成登录、恢复上下文和注销。
- 错误密码与未知账号返回相同安全错误，不泄漏成员存在性。
- 模型运行时不再因组织权限传递依赖而装配无关能力。
- 全 Maven 反应堆测试通过。
- 旧 `api` 文件从 124 降至 122，旧 `adapter` 文件从 79 降至 71。
- 组织权限上下文旧包引用归零。
- 21 项架构与治理测试通过。

对应已验证实现提交：`6946c6bc1`。
