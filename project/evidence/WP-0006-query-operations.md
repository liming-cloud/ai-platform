# WP-0006：查询用量、脱敏审计与治理控制台证据

## 结论

查询运行事实已经形成租户级用量汇总和脱敏审计只读投影，并通过控制面向具备
独立权限的租户管理员开放。运营上下文不复制查询写模型，不保存第二份审计事实；
所有统计和列表都从可信查询上下文公开的最小事实源读取。

实现提交：

`9a59c41fc807aa073159397f7e2548c6197ff597`

## 领域和权限边界

- 可信查询上下文只公开用量汇总事实和脱敏审计事实，不公开数据库类型。
- 评测与运营上下文负责权限、时间范围、配额视图和公共响应映射。
- 用量读取要求 `usage:read`，审计读取要求 `audit:read`。
- 接口只使用可信会话中的租户标识，不接受请求传入租户标识。
- 审计查询只选择运行标识、成员标识、助手版本、问题摘要、状态、拒答原因、
  令牌和时间，不读取问题正文、回答、引用正文或租户标识。
- 统计范围默认使用当前 UTC 自然日，自定义范围必须同时提供起止时间且不超过
  三十一天。
- 审计列表使用服务端编码的不透明游标，单页限制为一至一百条。

## 测试驱动证据

先新增应用服务和真实 PostgreSQL 投影测试并执行：

```bash
./mvnw -q \
  -pl backend/contexts/evaluation-operations,backend/contexts/trusted-query \
  -am \
  -Dtest=DefaultOperationsQueryServiceTest,PostgresQueryRunRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

首次执行按预期编译失败，明确缺少运营公共契约、服务和事实源。实现后同一命令
通过，覆盖：

- 当前 UTC 自然日默认范围和三十一天最大范围。
- 用量与查询运行状态、令牌和当前配额事实一致。
- 用量、审计权限相互独立。
- 稳定游标和倒序分页。
- 门店与校园租户隔离。
- 审计投影不读取敏感正文。

控制面接口测试使用真实 PostgreSQL、Redis、登录和可信上下文恢复：

```bash
./mvnw -q \
  -pl backend/runtimes/control-plane \
  -am \
  -Dtest=DefaultOperationsQueryServiceTest,PostgresQueryRunRepositoryTest,\
OperationsApiTest,ControlPlaneApplicationTest \
  -Dsurefire.failIfNoSpecifiedTests=false \
  test
```

结果通过，验证登录、租户上下文、时间参数、游标参数、未登录拒绝、无权限拒绝
和稳定公共错误。

## 全量回归

```bash
./mvnw -q test
cd frontend
npm run typecheck
npm run lint
npm run test
npm run build --workspace @foundation/console-web
make project-verify
git diff --check
```

结果：

- 后端 205 项测试通过，失败 0、错误 0、跳过 0。
- 前端 27 项测试通过，类型检查和代码规范检查通过。
- 控制台生产构建通过。
- 首次后端全量执行中，模型部署测试的临时 PostgreSQL 容器发生一次连接重置；
  该用例单独重跑通过，随后第二次全量回归全部通过。
- 项目治理 7 项测试和控制面完整性验证通过。
- 差异空白检查通过。

## 控制台产品闭环

- 顶级导航只保留已经形成闭环的“数据”和“治理”，不展示不可用入口。
- 导航能够实际切换任务，不再是静态标签。
- 数据发布与治理页共享同一短生命周期内存会话，不写浏览器存储。
- 治理页展示接受、回答、拒答、失败、运行中和令牌总量。
- 每日配额和并发配额使用进度线表达，不使用同质指标卡片矩阵。
- 审计表最多七列，标识只显示缩略值，问题只显示不可逆摘要。
- 页面覆盖加载、空、错误、无权限、部分成功、陈旧和正常状态。
- 治理工作区按需加载。主入口压缩后为 480.89 千字节，治理块为
  24.37 千字节，没有触发五百千字节构建警告。

本切片完成代码、状态策略和生产构建验证。最终浏览器任务验收与截图将在
WP-0006 关闭证据中统一执行。

## 容器和真实数据验收

```bash
docker compose \
  --env-file deploy/compose/.env.example \
  -f deploy/compose/compose.yml \
  up --detach --build control-plane console-web
bash scripts/development/deploy-smoke.sh
bash scripts/development/acceptance-query-operations.sh
```

结果：

- 五个应用入口健康。
- 门店和校园管理员都具备用量与审计读取权限。
- 两个租户的接口用量分别与 PostgreSQL 查询运行实数一致。
- 审计 JSON 没有问题正文、回答、引用、提示词、租户和密钥字段。
- 门店查询运行标识不会出现在校园审计列表。
- 游标第二页不会重复第一页记录。
- 每日限额 100、并发限额 4 在查询运行时和控制面保持一致。

最终镜像摘要：

- 控制面：
  `sha256:f648b18f15084edf9d06ca821bbf2674b2498b11067062e61f21545a211af73b`
- 控制台：
  `sha256:4fdd2843f65927a8dc6302a587f8cac3bc7f7371e5976ef0926fca1981e572ee`

## 能力边界

- M1 不支持在线调整配额、计费和审计导出。
- 令牌值取决于模型适配器返回的用量元数据；不提供时保持零，不进行猜测。
- 当前审计是查询运行事实投影，不替代后续跨上下文安全审计。
- 最小离线评测和最终浏览器任务验收仍待本工作包后续切片完成。
