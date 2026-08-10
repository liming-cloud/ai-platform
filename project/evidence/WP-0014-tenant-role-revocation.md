# WP-0014：租户内置角色撤销与成员权限弹窗

## 目标与范围

落实 ADR-0057、ADR-0059 的成员治理切片：受 `access:manage` 授权的当前租户管理员可以撤销成员的一项已绑定内置角色；撤销前必须保护租户至少保留一个有效访问管理员，提交后推进成员权限修订并使旧租户会话失效。成员目录在既有服务端分页结果中只携带有界的内置角色代码集合，控制台通过弹窗完成角色分配、撤销确认和失败提示。

本切片不实现自定义角色解绑、内置角色定义修改或停用、直授权限清理、角色目录分页和路由预览。ADR-0058 的凭据公开契约缺口仍未实施，不以该切片绕过。

## 领域与实现边界

- 代码图谱探索了 `TenantRoleBindingApplicationService`、`TenantCustomRoleBindingApplicationService`、`TenantRoleBindingStore`、`TenantRoleBindingController` 和自动配置链路；影响分析确认原绑定服务调用面较大，因此新增命名的撤销端口与应用服务，不改变既有绑定端口契约。
- `TenantRoleBindingService.revoke` 是无状态领域服务，只接收受锁成员、租户、角色及最小安全事实，返回 `TenantRoleRevocationResult`。它不依赖 Spring、SQL、会话或 HTTP；撤销最后一个有效访问管理员直接失败关闭。
- 应用层只执行 token 派生 `access:manage` 鉴权、锁定读取、调用领域服务、同事务提交和会话撤销。PostgreSQL 适配器只映射角色、直接权限和活动自定义角色的安全投影，并按领域操作结果删除绑定与推进权限修订。
- 成员分页读取投影中的 `builtInRoles` 固定上界为当前已发布内置角色目录，不是无界数据平铺；普通成员目录本身继续使用页码、页大小、总数和前后页元数据。
- HTTP 只接收当前租户内成员和角色资源目标：`DELETE /api/v1/tenant/members/{membershipId}/role-bindings/{roleCode}`。租户、主体、权限、会话和令牌均从 `TrustedRequestContext` 恢复。

## 红绿与自动验证

| 层级 | 命令 | 结果 |
| --- | --- | --- |
| 领域、应用、真实 PostgreSQL 与自动配置 | `./mvnw -q -pl backend/contexts/organization-access -am test` | 通过；覆盖撤销权限修订、最后访问管理员拒绝、应用授权、真实 PostgreSQL 删除与会话失败回滚。 |
| 受限 HTTP | `./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过；覆盖内置角色绑定、撤销、旧会话失效与新会话权限投影移除。 |
| 前端 | `cd frontend && npm test && npm run lint && npm run typecheck && npm run build` | 通过；69 项前端测试、类型检查、静态检查和生产构建通过。 |
| 架构门禁 | `python3 scripts/quality/backend_architecture_guard.py verify` 与 `python3 -m unittest scripts.quality.test_backend_architecture_guard` | 通过；20 项门禁测试通过，角色撤销的安全投影与原子状态转换均以分类原因登记。 |
| 工作包控制面 | `make project-verify` | 通过；9 项项目治理测试通过，当前前沿保持为 `WP-0014`。 |

## 本机浏览器回归

以当前本机 Docker 组合仅重建 `control-plane` 和 `console-web`，复用已有卷与本机服务。统一登录后以租户管理员会话验证：

1. 地址 `/access/members?page=1` 显示标准成员表格、服务端分页元数据和已绑定内置角色摘要；成员没有角色时显示“未分配”。
2. “管理角色”打开任务弹窗，展示固定已发布角色目录，已绑定角色才出现撤销动作。
3. 点击撤销动作会进入独立确认弹窗，明确说明旧租户会话会失效，并提示最后访问管理员将被服务端拒绝。

浏览器回归不提交角色变更，不读取或记录密码、令牌、密码哈希、API Key 或主密钥。运行所需机密只在进程内从既有本机容器恢复，未写回仓库。

## 结论

本切片已完成 ADR-0057 的内置角色解绑闭环及 ADR-0059 的受控角色管理弹窗。WP-0014、M6 仍保持进行中：后续先为 ADR-0058 的浏览器凭据契约补充接受的 ADR，再实现厂商连接、探测、部署与健康供应链；不得提前进入 M7。
