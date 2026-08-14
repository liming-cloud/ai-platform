# M6 自动验收与人工评审请求

## 决定状态

| 字段 | 值 |
| --- | --- |
| 决定编号 | HITL-0007-M6-REVIEW |
| 决定类型 | 里程碑人工评审 |
| 当前结论 | 自动验收通过，待人工评审；不得进入 M7 |
| 评审基线 | `e987aad466c786493b6e26a60b5e8c9db159ff16` 及本次测试契约修正 |
| 自动验收时间 | 2026-08-14 |
| 本地验收入口 | `http://localhost:13000` |

## 本次交付范围

- ADR-0057：账户、固定角色和自定义角色、令牌派生权限、旧会话撤销，以及导航能力投影。`PLATFORM_OPERATOR` 只得到无数据平台工作区；`PLATFORM_ADMINISTRATOR` 可管理平台账户、租户、目录和平台模型供应链。
- ADR-0058：平台范围厂商连接、加密凭据、探测、不可变模型发现、部署、发布／停用、可用性投影与目录部署快照；租户模型配置只消费当前可用的已发布目录快照。
- ADR-0059：单一企业 SPA、URL 可恢复的服务端分页、单一高风险确认 Dialog、写后重读及注意力保护。首份发布页要求显式选择知识空间和嵌入模型配置。
- 本轮只修正 HTTP 集成测试使用旧数值型雪花标识的问题，使其与浏览器的字符串标识契约一致；未改变生产接口或权限语义。

## 自动验收依据

| 项目 | 结论 | 可复核证据 |
| --- | --- | --- |
| 账户、角色、权限与导航能力投影 | 通过 | `WP-0014-browser-navigation-capability-projection.md`、`WP-0014-platform-administrator-role-projection.md`、`WP-0014-local-identity-baseline-and-browser-regression.md`；真实 PostgreSQL、会话撤销、HTTP 与统一 SPA 回归覆盖。 |
| 厂商连接、模型探测与可用性供应链 | 通过 | `WP-0014-platform-model-supply-connection-foundation.md`、`WP-0014-platform-provider-connection-probe.md`、`WP-0014-platform-provider-model-discovery.md`、`WP-0014-platform-model-deployment.md`、`WP-0014-platform-model-catalog-snapshot.md`；真实 PostgreSQL、受控 HTTP 与 Compose 装配已回归。 |
| 统一 SPA、服务端分页与注意力保护 | 通过 | `WP-0014-console-pagination-and-attention-guard.md`、`WP-0014-publication-resource-picker-pagination.md`；前端 95 项测试和产品门禁禁止累加分页与嵌套 Dialog。 |
| 目录到首份发布的真实闭环 | 通过 | `WP-0014-enterprise-foundation-browser-regression.md`；本地 Compose 的 PostgreSQL、MinIO、摄取工作器和受控模型运行时完成索引并发布，唯一 `ACTIVE` 索引保留。 |
| 本轮 HTTP 契约回归 | 通过 | `AuthenticationApiTest` 使用创建响应返回的字符串 `customRoleId`，定向 Maven 回归退出码为 0；严格接口仍拒绝数值型请求，防止浏览器大整数精度丢失。 |
| 全量工程门禁 | 通过 | `make validate` 退出码为 0：治理、架构、后端全反应堆、前端类型检查／lint／95 项测试／构建、演示与运维脚本均通过。 |
| 真实运行健康 | 通过 | `docker compose --env-file deploy/.env -f deploy/compose/compose.yml config -q`、Compose 服务健康，以及控制面 `:18080` 与摄取工作器 `:18082` 健康检查均退出码为 0。 |
| 本地浏览器回归 | 通过 | 以既定本地账户直接恢复 `http://localhost:13000` 会话：展示租户主导航；平台治理菜单仅投影平台工作区、租户、账户、模型供应链；供应链页展示“本地确定性模型源”已验证、凭据已配置、探测通过，服务端分页为第 1 页共 1 条。 |

## 固定比较点技术评审

| 维度 | 结论 |
| --- | --- |
| 正确性 | 通过。全量门禁曾捕获旧测试数据类型与严格 HTTP 契约不一致；仅更新测试夹具后定向和全量回归均通过。 |
| 安全 | 通过。账户、角色和菜单均由令牌派生上下文授权；浏览器不接收 Endpoint、密钥或原始厂商响应。索引工作器使用受控主密钥，未将秘密写入证据。 |
| 复杂度 | 通过。修复限于一个测试请求值，无新增抽象、路由或状态。 |
| 性能 | 通过。普通目录保持服务端页码、页大小和总数；前端产品门禁阻止浏览器累加列表。 |
| 架构 | 通过。代码图谱复核 `ModelConfigurationRuntimeService`、`IndexBuildWorker`、`PlatformRoleBinding` 与 `TenantCustomRoleBindingRequest`；控制面 Web 入口未装配至摄取工作器。 |
| 测试质量 | 通过。真实 PostgreSQL、Redis、pgvector、MinIO、Tomcat、HTTP、Compose 和浏览器均参与验收；本次失败用例已用真实浏览器字符串契约收敛。 |

## 请人工复核

- 以平台管理员身份确认：账户、角色和导航确实反映业务职责，且 `PLATFORM_OPERATOR` 的无数据平台工作区不会误导为租户或账户治理权限。
- 以模型平台负责人身份确认：只有平台范围、已发布且当前可用的部署可形成目录快照；浏览器不应接触 Endpoint、密钥或厂商原始响应。
- 以租户管理员身份确认：首份发布要求显式选择知识空间和嵌入模型，目录到真实索引发布的操作语义符合预期。
- 以产品负责人身份确认：分页、单一确认 Dialog、写后重读与统一 SPA 的注意力保护满足日常企业操作；不应以页面菜单替代服务端授权事实。
- 若结论为通过，须另建 M6 人工批准记录并关闭 `WP-0014`、更新 `STATE.yaml` 后才可进入 M7。

本记录不替代人工评审结论，不关闭 `WP-0014`，不改变当前 `M6` 里程碑。根据 ADR-0063，本地 `http://localhost:13000` 是本阶段允许的真实浏览器验收入口；这不构成生产 TLS 部署批准。
