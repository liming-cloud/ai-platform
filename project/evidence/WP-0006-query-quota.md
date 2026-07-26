# WP-0006：原子查询配额证据

## 结论

查询运行已经具备服务端强制执行的每日接受限额和并发运行限额。限额判断、
幂等判断、查询运行插入与开始事件处于同一 PostgreSQL 事务；同一租户的
接受事务通过顾问锁串行化，因此并发请求不能共同越过限额。

实现提交：

`29050035252d88681b311ca984f1b68251378f33`

## 已证明行为

- 每日周期固定为 UTC 自然日，查询运行冻结周期、每日限额、占用序号、
  并发限额和接受时租约截止时间。
- 幂等查询先于配额计数处理；相同请求在额度用尽后仍可重放且不重复计量。
- 同一幂等键绑定不同请求时仍返回冲突，不能借重放绕过限制。
- 达到每日限额或并发限额时不执行候选检索和模型调用。
- 并发占用使用带截止时间的运行租约；进程未完成时，租约过期后不会永久
  阻塞后续查询。
- 查询完成、拒答或失败时在同一终态事务清除有效运行租约。
- 超额统一返回 `429 QUERY_QUOTA_EXCEEDED`，详情只包含限额类型、限额和
  重试时间。
- 部署配置明确提供每日限额、并发限额和租约时长；非法值在运行时装配时
  关闭失败。
- 普通接口和日志不增加原始问题、回答、引用或租户信息暴露。

## 测试驱动证据

先新增领域、应用和真实 PostgreSQL 用例并执行：

```bash
./mvnw -q \
  -pl backend/contexts/trusted-query,backend/runtimes/query-runtime \
  -am \
  -Dtest=QueryRunTest,QueryRunEventTest,PostgresQueryRunRepositoryTest,\
DefaultQueryRunServiceTest,DefaultQueryRunEventServiceTest,\
QueryApiExceptionHandlerTest,QueryRuntimeApplicationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

首次执行按预期编译失败，明确缺少：

- `QueryQuotaPolicy`。
- `QueryQuotaSnapshot`。
- `QueryQuotaExceededException`。
- 查询仓储的配额接受合同。

完成实现后，同一命令通过。测试覆盖：

- 未占用配额的查询提案不能进入终态。
- 配额事实从接受状态保留到查询终态。
- 每日限额、并发限额和租约过期。
- 四个并发事务竞争一个每日名额时只接受一个。
- 幂等重放不重复占用，幂等冲突保持拒绝。
- 超额发生后检索和模型调用次数均为零。
- 429 错误字段稳定且不包含业务敏感正文。

## 全量回归与治理证据

```bash
bash -n scripts/development/acceptance-query-quota.sh
./mvnw -q test
make project-verify
git diff --check
```

结果：

- 后端 198 项测试通过，失败 0、错误 0、跳过 0。
- 第十三号迁移在全新测试库完成，并通过真实 PostgreSQL 仓储测试。
- 项目治理 7 项测试和控制面完整性验证通过。
- 验收脚本语法与差异空白检查通过。

## 容器与增量迁移证据

```bash
docker compose \
  --env-file deploy/compose/.env.example \
  -f deploy/compose/compose.yml \
  up --detach --build control-plane query-runtime
make deploy-smoke
make acceptance-query-quota
```

结果：

- 原有数据库卷从第十二号迁移增量升级到
  `V202607270013__add_query_quota_facts.sql`。
- 迁移记录成功，历史查询缺失配额周期的数量为 0，运行中查询缺失租约的
  数量为 0。
- 五个应用入口健康。
- 完整前置验收依次通过模型部署、知识发布、索引、助手和可信查询。
- 配额验收把当日限额设置为“已有数量加一”，只允许一个新查询；第二个查询
  返回 429，数据库查询数量没有越过限额。
- 额度用尽后重放首个请求仍返回同一查询运行。
- 验收结束后查询运行时自动恢复默认每日限额 100，并再次通过健康检查。

最终镜像摘要：

- 查询运行时：
  `sha256:8c2871f6fd36ab2a0119281c4dbe238248a499dc9c013541a09e44dd27854d36`
- 控制面：
  `sha256:90a98ace42f8a18f43b451c33c89ac8a03581dbeed1c466bd17620a03cc1ec3d`

## 能力边界

- M1 配额来自部署配置，尚不支持管理员在线调整、套餐或审批。
- 同一租户的接受事务会被串行化，容量阶段需要以性能证据决定是否分片。
- 运行租约不会续期；超过租约时长的真实调用可能与后续请求并行，租约时长
  必须覆盖演示模型调用预算。
- 审计和用量只读接口尚未实现，下一切片直接读取本次冻结的查询运行事实。
