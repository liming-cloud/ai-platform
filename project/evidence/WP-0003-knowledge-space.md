# WP-0003 知识空间纵向切片证据

## 证据边界

本证据验收知识空间的领域、权限、PostgreSQL 持久化、幂等、配额、游标分页和控制面公共接口，不代表数据源内容、MinIO、发布流程和控制台页面已经完成。

- 实现提交：`02e4a64aea30c6acc485c93e6c5371a1cc9c2fa7`
- 关联决策：`ADR-0011`、`ADR-0013`
- 验证日期：2026-07-27
- 验证环境：macOS、JDK 26.0.2、Maven Wrapper 3.9.16、Docker Desktop

## 代码关系证据

编码前通过代码关系索引检查了以下边界：

- `knowledge-data` 原本只有模块骨架，没有可复用业务实现。
- `TrustedRequestContext` 和 `AuthenticatedSessionGateway` 是现有可信身份公开契约。
- `OrganizationAccessConfiguration` 是 Sa-Token 与 Redis 会话的运行时装配点。
- `AuthenticationController` 是 Bearer 令牌解析的既有影响范围。

据此把权限检查放入知识空间应用服务，把 Bearer 解析和可信上下文恢复放入控制面参数解析器，把 MyBatis-Plus、事务模板和 PostgreSQL 锁限制在持久化适配器。

## 测试优先证据

第一组红灯：

```bash
./mvnw -q -pl backend/contexts/knowledge-data -am test
```

领域聚合、公共命令、应用服务和仓储端口尚不存在，测试编译失败。

第二组红灯：

```bash
./mvnw -q -pl backend/contexts/knowledge-data -am test \
  -Dtest=PostgresKnowledgeSpaceRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

真实 PostgreSQL 先后暴露 UUID 自动结果映射和事务锁返回值映射问题。修复采用显式 UUID 文本投影和明确的锁查询返回类型，没有使用内存数据库或进程锁规避真实语义。

第三组红灯：

```bash
./mvnw -q -pl backend/runtimes/control-plane -am test \
  -Dtest=KnowledgeSpaceApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

Flyway 与演示租户已就绪，但知识空间路由返回 404，证明公共接口尚未接入。

## 自动验证结果

```bash
./mvnw -q -pl backend/contexts/knowledge-data -am test
./mvnw -q -pl backend/runtimes/control-plane -am test \
  -Dtest=KnowledgeSpaceApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false
make validate
```

结果：

- 知识空间聚合校验租户、创建者、名称、说明、草稿状态、版本和创建时间。
- 创建和列表在应用层分别强制 `knowledge:write` 与 `knowledge:read`。
- 同租户同键同请求返回原资源，同键异参失败且不产生第二个资源。
- 两个租户可使用相同名称和幂等键，各自列表只返回本租户资源。
- 四路并发创建在配额为 3 时只能成功 3 个，数据库最终数量为 3。
- 游标分页无重复资源，畸形游标失败关闭。
- 未登录返回 `401 / AUTHENTICATION_REQUIRED`。
- 缺少写权限返回 `403 / ACCESS_DENIED`，数据库不产生资源。
- 全后端、前端类型检查、前端规范、前端测试、两套前端生产构建和 7 项项目治理测试全部通过。

## Docker 组合部署

```bash
make deploy-up
make deploy-smoke
```

结果：

- 保留既有数据卷完成 Flyway 增量迁移。
- PostgreSQL、Redis、MinIO、三个后端运行入口和两个前端共八个服务健康。
- 五个应用入口健康检查通过。
- 控制面镜像编号为 `sha256:1b2b513df35c19fdb83997f8f857c61cab89075ea3d3e6a4aed3590bd3541056`。
- 真实执行“登录 -> 创建知识空间 -> 相同请求重放 -> 列表”成功。
- 两次创建返回相同资源标识，第二次响应头 `Idempotency-Replayed` 为 `true`，列表包含该资源。

## 当前结论

知识空间已经形成受可信身份保护的后端纵向闭环，可以作为数据源和原始内容发布的上游边界。WP-0003 仍处于进行中，下一前沿是文本数据源登记、MinIO 内容上传和发布状态。
