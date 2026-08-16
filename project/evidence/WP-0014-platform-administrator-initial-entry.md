# WP-0014：平台管理员干净基线与首次可操作入口

## 目的与边界

本切片落实人工验收前提：本地研发环境只预置一个活动的
`PLATFORM_ADMINISTRATOR`，人工必须能以该账户开始平台治理，随后在平台侧建立租户、账户、角色与平台模型供应链。
不向平台管理员授予租户正文、成员或业务数据读取权限，也不改变 ADR-0057、ADR-0058、ADR-0059 的服务端授权语义。

## 根因与设计

`PLATFORM_ADMINISTRATOR` 已由固定角色模板投影四项平台能力：
`platform-session:access`、`tenant-governance:write`、`platform:accounts:manage` 与
`model-platform:write`。但统一 SPA 以平台导航数组的第一个子项作为根路径默认值，而该子项
`platform-workspace` 专门服务于只有会话进入能力的 `PLATFORM_OPERATOR`。因此管理员会误入无数据工作区，无法从首次页面开始平台操作。

新增最小的 `defaultNavigationTarget`：首次进入跳过无数据 `platform-workspace`，选择首个实际可操作的已授权任务；当运营员只拥有该无数据入口时，仍保留原行为。路由、菜单、API 和服务端权限均不改写。

## TDD 证据

先在 `navigation-capability.test.ts` 增加两个断言并执行：

```bash
npm --prefix frontend test -- apps/console-web/src/session/navigation-capability.test.ts
```

红灯：`defaultNavigationTarget is not a function`。实现后同一命令绿灯，8 项通过：

1. `PLATFORM_OPERATOR` 继续默认进入 `platform-workspace`。
2. 包含三项平台写能力的平台管理员默认选择 `platform`，即 `/platform/tenants?page=1`。

## 本地研发基线

人工确认后，精确清除了唯一的已停用 `m6-local` 租户及其关联的知识空间、数据源、索引、模型配置、成员资格、自定义角色与生命周期事实；先前一次缺少模型配置凭据子表的事务失败并自动回滚，未产生部分删除。补齐子表后，第二次事务才提交。

提交后的只读核对结果：

| 事实 | 数量或状态 |
| --- | --- |
| 全局主体 | 1 |
| 租户 | 0 |
| 租户成员资格 | 0 |
| 活动平台角色绑定 | 1 |
| `platform.root` 角色 | `PLATFORM_ADMINISTRATOR` |

口令、密码摘要、cookie、会话令牌、API Key、主密钥和密文均未记录。

## 真实浏览器回归

在健康的本地 Compose `console-web` 上，以 `platform.root` 选择“平台管理”登录。实际首次 URL 为：

```text
http://localhost:13000/platform/tenants?page=1
```

页面显示“平台租户生命周期”“建立准备中租户”及“当前页没有租户”的服务端分页空态。展开“平台治理”菜单后，可见“平台工作区”“平台租户”“平台账户”“平台模型供应链”四项；这证明管理员具备完整平台任务入口，而运营员的无数据入口仍可保留为最小权限投影。

## 验证与六维检查

已执行：

```bash
npm --prefix frontend test -- apps/console-web/src/session/navigation-capability.test.ts
npm --prefix frontend run typecheck
npm --prefix frontend run lint
npm --prefix frontend run build
npm --prefix frontend test
make project-verify
```

定向 8 项、全量前端 98 项、类型检查、lint、构建和项目治理验证均通过；`console-web` Compose 容器健康。

- 正确性：实际管理员会话首次到达受权租户治理 URL，运营员保留无数据入口。
- 复杂度：只增加一个纯前端导航选择函数，没有新增角色、路由、状态或后端接口。
- 冗余：复用现有能力投影和路由格式化，未复制授权判断。
- 性能：只在已生成的小型导航数组上进行一次线性选择，不增加请求或数据读取。
- 架构：菜单继续只是服务端会话能力的只读投影，API 授权与租户隔离不受影响。
- 测试质量：保留预期红灯、定向绿灯、全量前端和真实 Compose／浏览器证据。

本记录不关闭 `WP-0014`，不改变 M6 进行中状态，也不构成 M6 人工验收通过。
