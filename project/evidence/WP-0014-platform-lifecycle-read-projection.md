# WP-0014：平台生命周期读取投影证据

## 范围与边界

本切片落实 ADR-0049 方案 B。`organization-access` 新增只读、分页的
`TenantLifecycleSummary`；它不是聚合、实体或可写跨上下文对象。平台工作区只显示
`tenantId`、编码、显示名称、状态、修订、创建时间与最后变更时间。初始管理员、成员、主体、权限、凭据、
知识或问答正文以及当前操作者都不进入投影。

`lastChangedAt` 由同一租户生命周期发件箱的最新发生时间派生；没有事件的历史行退回根的创建时间。因此读取
不在 `TenantLifecycle` 上增加重复可写时间字段，也不把生命周期状态机判断移入仓储。

平台会话资格与生命周期治理权限分离：`platform-session:access` 只允许恢复平台会话；
`tenant-governance:write` 才允许读取或迁移生命周期。隔离演示夹具中的平台运营主体只拥有前者，浏览器能力
返回两个空集合，直接读取和写入均失败关闭。

## 先红后绿

先在真实 PostgreSQL／Redis 的 `AuthenticationApiTest` 固定读取契约。实现缺失时执行：

```bash
./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am \
  -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test
```

新增读取反例预期 `200`，实际得到 `500`，证明路由和查询应用服务尚未注册。随后以最小 seam 补齐
`TenantLifecycleReadPort`、事务只读的 `TenantLifecycleQueryService`、PostgreSQL 分页 adapter、HTTP 响应和前端
重新加载；同一命令转绿。运营主体反例再扩展为同一 bearer 对读取和建立命令都返回既有 `400` 失败关闭语义，
并确认错误不包含租户标识。

## 已执行验证

```bash
./mvnw -q -pl backend/contexts/organization-access,backend/platform/platform-security/platform-security-core,backend/platform/platform-security/platform-security-satoken -am test
./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am \
  -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test
cd frontend && npm run typecheck && npm run test -- --run apps/console-web/src/api/control-plane-client.test.ts
git diff --check
```

上述命令均通过。组织访问回归覆盖纯应用层拒绝（权限缺失时不调用读取端口）、真实 PostgreSQL 的排序／分页／
创建时间回退、自动配置与平台会话权限分离；安全模块覆盖会话解析；HTTP 回归覆盖最小字段、分页、无权限读取
与写入拒绝、无工作区浏览器能力；前端定向回归共 18 项，通过服务端投影加载并在写后重新读取。

提交前再次执行 `make validate`，项目治理、19 项架构门禁、完整 Maven 反应堆、13 个前端测试文件的 54 项
测试与两个生产构建，以及演示、真实模型、恢复、容量和交付门禁均通过。

## 六维检查与未完成项

| 维度 | 结论 | 依据 |
| --- | --- | --- |
| 正确性 | 自动验证通过 | 每次读取和写入均从服务端平台上下文重新授权，写后重新读取。 |
| 复杂度 | 可控 | 仅增加一个应用查询 seam 与一个最小分页 adapter，不复制状态机。 |
| 冗余 | 无新增重复事实 | 最后变更时间从既有发件箱派生，前端不再保存最近写入的生命周期对象。 |
| 性能 | 有界 | SQL 使用页大小和偏移，按受限字段聚合并计数；页大小继续受既有 `PageQuery` 上限约束。 |
| 架构 | 通过局部验证 | interfaces 仅映射 HTTP，application 授权并编排读取，infrastructure 仅执行投影映射。 |
| 测试质量 | 浏览器续证待补 | 已有纯应用、真实 PostgreSQL、自动配置、HTTP 和前端回归；仍需真实浏览器两页签、HTTPS Cookie 与自然过期证据。 |

本切片不标记 M6 或 WP-0014 完成，不激活 M7／M8。
