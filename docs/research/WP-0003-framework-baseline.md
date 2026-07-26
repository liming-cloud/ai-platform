# WP-0003 框架使用基线研究

## 研究范围

本研究只回答 WP-0003 开工前的四个问题：

1. Sa-Token 1.45.0 在 Spring Boot 4 中承担什么职责。
2. 租户隔离和资源权限如何避免被框架能力替代。
3. MyBatis-Plus 3.5.17 的插件边界与顺序如何确定。
4. PostgreSQL 业务结构由什么机制初始化和演进。

## 一手资料

- [Sa-Token 1.45.0 官方文档](https://sa-token.cc/doc.html)
- [MyBatis-Plus Spring Boot 4 安装说明](https://baomidou.com/en/getting-started/install/)
- [MyBatis-Plus 多租户插件](https://baomidou.com/plugins/tenant/)
- [MyBatis-Plus 乐观锁插件](https://baomidou.com/en/plugins/optimistic-locker/)
- [MyBatis-Plus 插件主体与顺序](https://baomidou.com/plugins/)
- [Spring Boot 数据库初始化](https://docs.spring.io/spring-boot/how-to/data-initialization.html)
- [Flyway 迁移与事务](https://documentation.red-gate.com/fd/migration-transaction-handling-273973399.html)

## 结论

### Sa-Token

- 使用 `sa-token-spring-boot4-starter` 1.45.0。
- Sa-Token 只负责登录态、令牌生命周期、会话撤销和入口权限检查。
- 应用层接收平台自己的 `TrustedRequestContext`，不接收 `StpUtil`、Sa-Token Session 或框架异常。
- 登录标识使用租户成员标识，不使用可跨租户复用的人员标识。
- 租户来自服务端已验证会话，禁止从任意请求头直接信任。
- 注解或路由权限只能拦截入口；资源归属、行过滤和字段遮蔽仍由应用与领域策略决定。

### MyBatis-Plus

- 使用官方 Spring Boot 4 Starter 3.5.17，不重复引入 MyBatis Starter。
- 3.5.9 之后插件依赖为可选项，需要显式引入与 BOM 同版本的 JSQLParser 模块。
- 拦截器顺序固定为：租户改写、分页或乐观锁、危险 SQL 阻断。
- `TenantLineInnerInterceptor` 只作纵深防御；每个仓储方法仍显式接收 `TenantId` 并在 SQL 条件中使用。
- 乐观锁实体使用整数版本列；应用层将更新行数为零转换为稳定并发冲突。
- 禁止复用更新 Wrapper，禁止全表更新、无条件删除和业务代码临时忽略租户插件。

### Flyway 与 PostgreSQL

- 只使用 Flyway 管理业务结构，不同时启用 `schema.sql`、ORM 自动建表或 Mapper 启动建表。
- 迁移文件靠近上下文并纳入 Git，发布后只追加、不修改。
- PostgreSQL 能在事务中执行大多数 DDL；每个迁移保持可事务执行，失败即停止。
- M1 使用一个业务 Schema 和全局唯一迁移版本；表、索引和唯一约束显式包含 `tenant_id`。
- 禁止跨上下文外键和 Mapper 查询，表所有权由迁移目录和适配器包确定。

## 对 WP-0003 的直接约束

- 认证适配器从 Sa-Token 会话生成 `TrustedRequestContext`。
- 知识仓储接口必须显式接收租户标识，即使租户拦截器已经启用。
- Flyway 在应用启动前完成迁移，迁移失败时控制面不得继续启动。
- PostgreSQL 集成测试验证真实提交、回滚、唯一约束、租户隔离和乐观锁。
- 测试不能用 H2 或内存仓储替代 PostgreSQL 合同。

## 未解决问题

- 演示账号的密码哈希算法和凭据注入方式需要在认证实现切片前确定。
- MinIO SDK 与 Testcontainers 版本需要按正式版和许可证门禁单独解析。
- Sa-Token Redis DAO 的具体模块需要结合 1.45.0 依赖元数据验证，不能凭旧版示例选包。
