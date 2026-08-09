# WP-0014 企业产品基础重定标与影响证据

## 目标

把产品顺序从“继续堆叠 RAG 业务页面”调整为“先完成企业访问控制、模型供应链和统一控制台基础，再验收核心闭环”。本证据只登记设计与影响，不证明生产实现已经完成。

## 仓库事实

- `ProviderConnectionController` 已有创建、分页列表和连接探测，但缺少模型发现、能力探测、探测历史与完整可用性产品闭环。
- `ModelConfiguration` 已有平台、租户、个人范围和授权，但页面没有形成连接、模型、能力、发布和运行健康供应链。
- `TenantMembershipRole` 只有 `MEMBER` 与 `TENANT_ADMINISTRATOR`；`PlatformPermissions` 只有平台会话与租户治理写权限。
- `BrowserSessionCapabilityService` 只投影工作区类别、显示角色和会话期限；前端导航仍是静态数组。
- 前后端已经存在 `PageQuery`／`PageResult`。服务端分页默认 20、最大 100，前端客户端也接收总数和总页数；资源页面尚未把分页和 URL 恢复设为统一硬规范。
- `ProductShell` 只是共享头部与页签，`ConsoleApp` 和 `query-web` 仍拥有分裂的导航与应用状态。

## 代码图谱探索与影响分析

聚焦探索了 `ProviderConnection`、`ProviderConnectionService`、`ModelConfiguration`、`SubjectIdentity`、`TenantMembership`、`BrowserSessionCapabilityService`、`ConsoleApp`、`ProductShell`、`ControlPlaneClient`、`PageQuery` 和 `PageResult`。

非局部影响分析结果：

- `BrowserSessionCapability` 影响 47 个后端、共享客户端、控制台、查询应用和测试符号。
- `ProviderConnectionService` 影响 73 个应用服务、Controller、自动配置和测试符号。
- `PageResult` 影响 100 个前端客户端、列表调用、助手应用与共享分页契约符号。
- `ProductShell` 当前只有共享组件自身的直接影响，说明统一路由和工作区外壳仍未形成真实调用 seam。

上述影响使本切片停止在 ADR 和规范层，不直接修改公共会话、模型或分页契约。后续实现必须分别建立 TDD 路线和迁移顺序。

## 接受的设计

- ADR-0057：账户、角色、权限、范围、会话失效和菜单能力投影。
- ADR-0058：厂商定义、连接、凭据、模型发现、真实能力探测、发布和健康供应链。
- ADR-0059：统一人员企业 SPA、标准服务端分页、URL 可恢复状态、列表／详情／弹层渐进披露和注意力保护。

## 后续验收门

企业基础验收必须证明管理员无需修改数据库即可完成账户和角色治理、厂商连接与探测、模型验证与发布；普通用户只看到授权菜单；所有资源列表服务端分页；一次登录可以在统一路由树中完成授权任务。该门未通过前不得用新增助手、评测或问答页面宣称产品闭环可用。
