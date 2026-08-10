# WP-0014：租户成员目录与生命周期

## 目标与范围

落实 ADR-0057、ADR-0059 的受限成员治理切片：受 `access:manage` 授权的当前租户管理员能够分页读取最小成员目录，建立已启用全局主体的普通成员资格，并启用或停用成员。租户、操作者、权限版本和会话均从 token 派生上下文恢复，不能由 HTTP 参数或前端状态伪造。

本切片不实现全局主体停用、密码重置、角色解绑、角色目录或路由预览；这些仍需按 ADR-0057 的后续设计卡实施。

## 领域与实现边界

- 代码图谱探索了 `TenantMembershipLifecycle`、`TenantMemberEstablishmentApplicationService`、`TenantMemberStatusController`；影响分析确认控制器只影响自动配置测试，避免扩大 `SubjectIdentityRepository` 的既有依赖面。
- `TenantMembershipLifecycle` 是无状态领域服务：建立结果固定为 `MEMBER`、`ENABLED`、权限修订 `0`，不授予隐式业务权限；停用时仍保护“最后一个有效访问管理员”。
- 应用层只负责 token 派生 `access:manage` 鉴权、事务编排与会话撤销；PostgreSQL 端口只提供启用主体行锁、同租户重复检查、最小目录投影与原子状态提交。
- 控制台只读取服务端分页；停用和新建分别使用二次确认与弹窗任务边界，非法主体标识在提交前失败关闭。

## 红绿与自动验证

| 层级 | 命令 | 结果 |
| --- | --- | --- |
| 应用、领域、真实 PostgreSQL 与自动配置 | `./mvnw -q -pl backend/contexts/organization-access -am test` | 通过；包含成员建立的启用主体、重复成员、无隐式权限、状态原子提交与回滚验证。 |
| 受限 HTTP | `./mvnw -q -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=AuthenticationApiTest -Dsurefire.failIfNoSpecifiedTests=false test` | 通过；成员目录、token 派生范围、建立、停用及旧会话撤销均回归。 |
| 前端 TDD、类型、静态检查与生产构建 | `cd frontend && npm test && npm run lint && npm run build` | 通过；成员建立客户端只发送 `subjectId`，路由与导航能力投影回归通过。 |
| 架构门禁 | `python3 scripts/quality/backend_architecture_guard.py verify` | 通过；成员 Mapper 的锁、受限投影与状态写入均已登记 SQL 例外分类。 |
| 工作包控制面 | `make project-verify` | 通过。 |

## 本机浏览器回归

使用当前本机 Docker 部署只重建 `control-plane` 与 `console-web`，复用既有数据卷、PostgreSQL、Redis 和 MinIO。统一登录后以租户管理员会话验证：

1. 从唯一入口进入 `成员与权限`，地址为 `/access/members?page=1`。
2. 页面显示当前租户的标准表格和服务端分页元数据，不平铺成员卡片。
3. “新建成员”打开模态弹窗，明确说明只接收既有启用全局主体且不授予隐式权限。
4. 输入非正整数标识时前端显示失败信息，不提交网络请求。

浏览器验收不读取或记录密码、会话令牌、密码哈希、API Key 或主密钥。容器运行配置只在内存中从现有容器恢复并通过 Compose 预检，未写回受版本控制文件。

## 结论

本切片的领域、应用、真实 PostgreSQL、自动配置、HTTP、统一 SPA、浏览器与工程门禁均已通过。本证据绑定本次中文 `feat` 提交；WP-0014 和 M6 仍为进行中，后续必须完成 ADR-0057 剩余角色治理，以及 ADR-0058 的供应链与 ADR-0059 的其余统一 SPA 任务后才可申请 M6 人工评审。
