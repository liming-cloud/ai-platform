# WP-0003 演示租户认证基座证据

## 证据边界

本证据只验收 WP-0003 的人员认证、租户成员事实、可信会话和 Docker 控制面登录切片，不代表知识空间、数据源、对象存储、幂等、配额和控制台任务流已经完成。

- 实现提交：`4e0c258b2205a7ecb21d606860366bbf28b69810`
- 验证日期：2026-07-27
- 验证环境：macOS、JDK 26.0.2、Maven Wrapper 3.9.16、Docker Desktop

## 安全边界

- 密码只通过可清零的字符数组进入应用端口，请求、命令、认证结果和响应的字符串表达均不输出密码、密码摘要或令牌。
- 密码摘要采用带算法和版本标识的 PBKDF2-HMAC-SHA256 格式，每个凭据独立随机盐，迭代次数为 600000。
- 编码长度、迭代次数和密码长度均有上限；畸形摘要先失败关闭，避免被构造为高成本计算。
- 不存在成员和密码错误向公共接口返回相同的状态码与错误码，并执行等价密码校验路径。
- 客户端提交的租户代码只用于受约束查询；会话中的租户、成员和权限来自 PostgreSQL 的服务端事实。
- 人员会话由 Sa-Token 1.45.0 建立并存入 Redis；令牌撤销后不能再恢复可信请求上下文。
- 数据库查询同时约束租户和成员，两个租户可拥有相同登录名而不会串读。
- 密码算法边界和升级策略记录在 `ADR-0012`。PBKDF2 参数参考 OWASP 密码存储建议：<https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html>。

## 红灯证据

测试优先实施过程中依次观测到以下可解释失败：

1. 密码适配器和认证应用服务尚不存在，相关单元测试编译失败。
2. PostgreSQL 集成测试暴露最终类被代理、Spring Boot 4 Flyway 模块缺失和 UUID 映射三个真实集成问题。
3. Redis HTTP 集成测试在控制面未接入组织访问配置时无法创建认证端点。

上述失败分别通过显式运行时组装、引入 Spring Boot 4 Flyway 启动器、显式 UUID 文本映射和 Redis 会话装配修复，没有用内存仓储替代数据库语义。

## 自动验证

```bash
./mvnw -q -pl backend/contexts/organization-access -am test
./mvnw -q -pl backend/runtimes/control-plane -am test
make backend-test
make project-verify
```

结果：

- 密码摘要生成、正确密码、错误密码、Unicode 密码和畸形摘要限制测试通过。
- PostgreSQL 18.4 全新容器执行 Flyway 迁移，租户隔离、禁用租户、未知租户和未知成员反例通过。
- Redis 8.8.1 全新容器完成登录、可信上下文恢复、注销和撤销后拒绝恢复测试。
- 错误密码和未知成员均返回 `401 / AUTHENTICATION_FAILED`。
- 全后端清理测试通过。
- 7 项项目治理测试和控制面一致性校验通过。

## Docker 组合部署

```bash
make deploy-up
make deploy-smoke
```

结果：

- PostgreSQL、Redis、MinIO、控制面、查询运行时、摄取工作进程和两个前端共八个服务健康。
- 五个应用入口健康检查通过。
- 控制面镜像编号为 `sha256:ba30c8df276b9912dc7a4d235bf4a754b6fa5c138e061b37453d17e9d305cdd6`。
- `demo-store/admin` 通过真实 HTTP 登录，返回 `Authorization` 令牌和 3600 秒有效期；注销返回 204。
- 错误密码和未知成员通过真实 HTTP 均返回相同的 401 和错误码。

本次 Docker 组合更新保留了工作区既有数据卷，没有执行破坏性的删卷操作。全新数据库和 Redis 初始化由 Testcontainers 自动验证；Docker 组合全新卷验收仍保留为 WP-0003 最终关闭条件。

## 当前结论

演示租户认证基座已经形成可运行闭环，可以作为知识空间写接口的可信身份入口。WP-0003 仍处于进行中，下一前沿是受权限保护的知识空间 PostgreSQL 纵向切片。
