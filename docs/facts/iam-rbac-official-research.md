# IAM / RBAC 企业级设计资料调研

Stage: architecture-facts  
Category: facts  
Status: current  
Review Status: not-reviewed  
Reviewers: architect, backend lead, frontend lead  
Approval: pending  
Inputs: NIST RBAC、OWASP Authorization、Keycloak Server Admin、Vue Router 官方文档  
Outputs: 多租户 IAM、菜单、权限和前端授权渲染约束  
Depends On: `docs/32-control-surface.md`, `docs/45-module-and-service-design.md`  
Load When: iam-rbac-development/frontend-menu-development  
Next: 回写 Tenant & IAM 模块设计、交付计划和实施证据

## 资料范围

| 来源 | 官方资料 | 本项目约束 |
| --- | --- | --- |
| NIST CSRC | https://csrc.nist.gov/projects/role-based-access-control/faqs | RBAC 必须围绕 user-role、role-permission、role activation、constraint 建模，权限不直接散落到用户 |
| OWASP Cheat Sheet | https://cheatsheetseries.owasp.org/cheatsheets/Authorization_Cheat_Sheet.html | 授权不同于认证，必须最小权限、默认拒绝、每次请求服务端校验、失败安全退出和审计 |
| Keycloak Server Admin | https://www.keycloak.org/docs/latest/server_admin/index.html | 企业多租户需要组织/成员、角色、角色映射和管理员可维护属性，不是单租户演示账号 |
| Vue Router | https://router.vuejs.org/guide/advanced/dynamic-routing.html | 前端可做动态路由/菜单注册，但授权事实源必须来自后端；客户端显隐不能替代服务端权限 |

## 企业级设计结论

- 登录只完成认证；授权必须基于当前主体、租户、角色、权限、资源和约束重新判断。
- RBAC 最小闭环是 `User -> Role -> Permission` 与 `Role -> Menu`，菜单只控制导航可见性，不能替代接口权限。
- 多租户平台需要区分平台管理和租户平台：平台侧维护租户生命周期、全局功能目录、平台模型基础配置；租户侧在平台允许范围内维护本租户用户、角色和模型配置。
- 新建租户不是单表插入，必须同事务完成租户、初始管理员、默认角色、默认权限、默认菜单和审计。
- 角色、权限、菜单必须可管理、可启停、可审计；禁用项不得继续出现在当前用户可用授权结果中。
- 前端登录后必须调用 `/api/auth/me` 获取角色、权限和菜单，并以该结果作为唯一菜单事实源。页面内按钮按 permission code 控制，接口仍必须由 Sa-Token 或 application permission check 拒绝未授权请求。
- 前端可以维护 `componentKey -> 本地页面组件` 注册表，但标题、排序、层级、可见性必须来自后端菜单数据；未知组件必须有可诊断空态，不能白屏。

## 本项目实现约束

- Tenant & IAM 当前最小完成态包括：租户创建/更新/启停、角色创建/更新/启停、权限点创建/更新/启停、菜单创建/更新/启停、角色绑定权限和菜单、`/api/auth/me` 返回授权菜单。
- 首期仍使用共享库共享表 + `tenant_id` 强隔离；所有 IAM 表使用 `id BIGINT` 雪花 Long 主键，业务编码只作为唯一业务键。
- 平台管理端菜单至少包含：租户管理、角色管理、权限管理、菜单管理、模型基础配置。
- 租户平台菜单至少包含：模型配置；后续知识库、工具和应用运行时进入各自切片后再开放。
- 任何 IAM 写操作必须记录审计，接口权限码必须在权限目录中可见，页面菜单必须在菜单目录中可见。
