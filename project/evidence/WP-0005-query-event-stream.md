# WP-0005：查询事件序列与可重连事件流证据

## 结论

可信查询运行已经具备持久化、单调递增且可按游标重放的状态事件。
查询开始和终态事件分别与查询认领、查询完成事实处于同一数据库事务，
客户端可通过 `Last-Event-ID` 从断点之后恢复，不会重复产生完成事实、
引用或模型用量。

实现提交：`cf7a14cf494a91cdb34f9116a8e0a062340e86b3`

## 已证明行为

- 每个新查询首先持久化序号 1 的开始事件。
- 已回答、已拒答或失败终态使用大于 1 的序号；当前同步查询固定为序号 2。
- 终态事件完整保存回答、拒答原因、引用、模型用量和发生时间。
- 查询运行、引用和终态事件在同一事务提交，乐观锁冲突不会留下半完成事件。
- 历史查询运行通过第 11 版迁移回填开始与终态事件。
- 第 12 版独立迁移同时约束领域和数据库，禁止终态占用开始事件序号。
- 事件读取同时受租户、查询运行编号、`assistant:query` 和
  `knowledge:read` 权限约束。
- `GET /api/v1/query-runs/{id}/events` 以服务端事件流返回持久化事件，
  每条事件的编号与数据库序号一致。
- `Last-Event-ID: 1` 只重放序号 2；`Last-Event-ID: 2` 不再返回事件。
- 请求头和查询参数中的恢复游标冲突、格式错误或小于零时稳定拒绝。
- 相同幂等查询重复执行不会新增第二组查询事件。
- 门店与校园查询事件不能跨租户读取。

## 测试驱动证据

先增加“终态事件不能占用开始事件序号”的领域用例并确认失败：

```bash
./mvnw -q -pl backend/contexts/trusted-query -am \
  -Dtest=QueryRunEventTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

失败结果为预期未抛出 `IllegalArgumentException`。随后收紧领域不变量，
并追加第 12 版数据库约束迁移；领域和真实 PostgreSQL 测试转为通过：

```bash
./mvnw -q -pl backend/contexts/trusted-query -am \
  -Dtest=QueryRunEventTest,PostgresQueryRunRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

全新 PostgreSQL 实例按顺序成功应用 13 个迁移项并到达
`202607270012`。

## 全量回归与部署证据

```bash
./mvnw -q test
make project-verify
git diff --check
bash -n scripts/development/acceptance-trusted-query.sh
docker compose --env-file deploy/compose/.env.example \
  -f deploy/compose/compose.yml up -d --build \
  control-plane query-runtime
make deploy-smoke
make acceptance-trusted-query
make acceptance-trusted-query
make acceptance-trusted-query
```

结果：

- 后端 188 项测试通过，失败 0、错误 0、跳过 0。
- 项目治理 7 项测试和控制面完整性验证通过。
- 全新数据库迁移链和既有演示数据库增量升级都到达第 12 版。
- 八个组合服务处于健康状态，五个应用入口健康检查通过。
- 约束收紧前连续两轮完整容器验收通过，证明幂等重试不增加事件；
  最终镜像和第 12 版迁移部署后再次通过完整回归。
- 容器验收覆盖双租户模型部署、知识发布、索引、助手、带引用回答、
  证据不足拒答、幂等计量、事件断点重放和跨租户隔离。

最终镜像摘要：

- 控制面：
  `sha256:fdbbc793d44a86ec4e411149a55d3199d79eebb04bab86d8c6852f0fcd7a0088`
- 查询运行时：
  `sha256:45c5de5e23adab14781b4cf85284d37e8bace0cc7eb72353d159b5b1c25648ec`

## 能力边界

本切片交付的是持久化状态事件的可重放服务端事件流，不代表以下能力完成：

- 当前查询创建仍同步完成，事件接口在重放当前批次后关闭连接。
- 当前只产生开始与终态事件，不提供模型逐令牌实时输出。
- 最终用户查询站点尚未接入登录、助手选择、提问、等待、回答、引用、
  拒答和错误状态。
- 演示配额、审计查询接口、用量查询接口、并发容量和延迟分位值尚未完成。

`WP-0005` 继续保持进行中。
