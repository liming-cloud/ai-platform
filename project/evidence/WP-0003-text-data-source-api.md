# WP-0003 文本数据源受控接口证据

## 证据边界

本证据只验收文本数据源的应用服务、权限、创建、列表和发布接口，不代表内容上传、对象存储或控制台任务流已经完成。

- 实现提交：`b7474dd72a5218f8b2160066ebe7607c38cc9546`
- 关联决策：`ADR-0014`
- 验证日期：2026-07-27
- 验证环境：macOS、JDK 26.0.2、Maven Wrapper 3.9.16、Docker Desktop、PostgreSQL 18.4 与 Redis 8.8.1 测试容器

## 红灯证据

```bash
./mvnw -pl backend/runtimes/control-plane -am \
  -Dtest=TextDataSourceApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

首次执行时数据源路由不存在，未登录查询返回 404，创建响应无法反序列化为数据源视图，四个接口场景全部失败。

```bash
./mvnw -q -pl backend/runtimes/control-plane -am \
  '-Dtest=TextDataSourceApiTest#缺少知识空间查询参数返回统一错误结构' \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

新增统一错误结构反例后，首次执行返回框架默认错误体，缺少平台错误编码和追踪标识。

## 绿灯证据

```bash
./mvnw -q -pl backend/runtimes/control-plane -am \
  -Dtest=TextDataSourceApiTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
make backend-test
make project-verify
```

结果：

- `POST /api/v1/data-sources` 在可信租户上下文中创建文本数据源并支持幂等重放。
- `GET /api/v1/data-sources` 要求知识空间标识，使用稳定游标分页并拒绝畸形游标。
- `POST /api/v1/data-sources/{id}/publish` 只发布内容就绪且乐观版本匹配的数据源。
- 未登录请求返回统一认证错误，只读成员不能执行创建或发布。
- 相同知识空间标识在不同租户下仍按可信租户事实隔离，跨租户列表为空。
- 应用层生成的幂等指纹包含操作、知识空间、数据源和预期版本，不依赖控制器输入顺序。
- 公共契约不暴露 Spring、MyBatis-Plus、Sa-Token 或数据库类型。
- 缺少必填查询参数和类型错误均返回统一的 `code`、`message`、`traceId`、`details` 结构。
- 全后端清理测试、真实 PostgreSQL 与 Redis 接口测试、架构规则、7 项治理测试和控制面一致性校验通过。

## 结构影响证据

代码图谱检查了文本数据源仓储公共接缝的三层影响范围，确认受影响对象集中在知识数据适配器、控制面装配和新增接口验收测试，未发现其他限界上下文依赖该内部端口。

## 当前结论

用户已经可以通过受控接口登记、查看和发布文本数据源。内容上传入口仍不得声明完成，下一步需要在批准的官方资料研究基础上确定对象存储客户端和安全调用规范，再连接既有流式暂存与数据库事务。
