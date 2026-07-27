# WP-0008 安全平台边界整改证据

## 结论

`platform-security` 已成为真实的安全平台聚合模块：

```text
platform-security/
├── platform-security-core/
└── platform-security-satoken/
```

- `platform-security-core` 统一持有可信身份、会话端口、密钥和出站安全策略。
- `platform-security-satoken` 统一持有 Sa-Token 会话实现、令牌解析、属性和自动装配。
- `organization-access` 只保留成员认证、口令验证和权限业务，不再依赖 Sa-Token。
- 控制面和查询运行模块不再直接声明 Sa-Token Maven 依赖或导入框架类型。
- 两套重复的 Web 可信上下文解析器已由安全平台统一实现。
- 查询进程补齐独立雪花节点配置，容器中控制面与查询进程使用不同工作节点。

## 验证

绑定提交：

`834dddd2adad0db33b37afb309c26ee7502f74d4`

已通过：

```text
./mvnw -q clean test-compile：通过
platform-security-core 全部测试：通过
platform-security-satoken 全部测试：通过
organization-access 全部测试和真实 PostgreSQL 持久化测试：通过
AuthenticationApiTest：通过
ControlPlaneApplicationTest：通过
QueryRuntimeApplicationTest：通过
CandidateScenarioTest：通过
QueryApiExceptionHandlerTest：通过
make architecture-guard：通过
make deploy-config：通过
git diff --check：通过
```

架构债务变化：

- Java SQL 注解文件：保持为 0。
- Java UUID 文件：保持为 0。
- Sa-Token 越界文件：从 3 降至 0。
- 运行时非启动文件：从 46 降至 41。
- 超预算包：仍为 8，进入后续目录整改切片处理。

## 未完成项

本证据只关闭安全平台所有权问题。运行模块纯启动化、目录拆分和领域增强仍必须继续。
