# WP-0014 OpenAI 风格企业产品重构自动回归

## 结论

ADR-0067、ADR-0068 对应的三角色、两级运营投影、名称选择器、OpenAI 风格统一 SPA、用户信息保护和本地 HTTP 验收入口已经实现并通过自动回归。当前结论是“自动门禁通过、浏览器登录后全流程待续证”，不构成 M6 人工接受，也不激活 M7。

## 实现提交

| 提交 | 结果 |
| --- | --- |
| `be3aa0255` | 接受三角色与运营投影决策 |
| `6d8e752c2` | 收敛三角色授权与能力投影 |
| `b27f3ce65` | 建立两级运营聚合、UTC 趋势与服务器分页名称选择器 |
| `02dbeb6dc` | 本地人工验收入口统一为 `http://localhost:13443` |
| `b6ac8eac2` | 重构统一管理壳、运营页、三步租户建立和可信问答体验 |
| `294c510da` | 登记三角色 API 冗余审计 |
| `9c517100c` | 收敛后端包结构并登记运营 Mapper SQL |
| `1ff96a013` | 固定浅色主题并尊重减少动态效果偏好 |

## 后端与真实基础设施

- `evaluation-operations` 负责平台和租户只读运营模型；组织、知识、模型、助手与查询上下文通过窄事实源提供脱敏统计，不跨上下文直接读取领域表。
- `GET /api/v1/platform/operations/overview`、`GET /api/v1/platform/operations/tenants`、`GET /api/v1/operations/overview` 已在控制面真实装配。
- `GET /api/v1/platform/accounts/candidates` 提供按登录名搜索的服务器分页候选；租户建立页面只显示登录名和状态，内部主体标识仅作为不透明请求引用。
- 运营范围固定为 `DAY_1`、`DAY_7`、`DAY_30`，按 UTC 自然日聚合；零分母比率、分页排序、跨租户隔离和平台脱敏均有自动回归。
- 最新 HEAD 重新构建 `control-plane`、`query-runtime`、`console-web`，现有 PostgreSQL、Redis、MinIO 与三个应用容器均健康；没有新增容器或 TLS 配置。

无会话 HTTP 复核：

```text
200 /
401 /api/v1/platform/operations/overview?range=DAY_7
401 /api/v1/operations/overview?range=DAY_7
```

## SPA 产品与安全边界

- 管理界面采用浅色表面、近黑主操作、细边框、紧凑侧栏和统一页头；问答页采用窄导航、对话列、固定输入区和引用抽屉。
- 平台和租户管理员首导航为对应范围的运营总览；普通用户只得到可信问答及授权知识能力。
- 租户建立改为“租户信息—租户管理员—确认建立”三步页面；评测、模型探测、对象存储和关联资源表单不要求用户输入 `SubjectId`、`tenantId`、`membershipId`、`modelId` 或版本号。
- `ControlPlaneClient` 只把稳定错误类别交给统一呈现层；页面不直接渲染后端 `message`，也不显示 `code`、`traceId`、`details`、异常、SQL、Endpoint、厂商原始响应或修订值。
- `400`、`401`、`403`、`409`、`429`、`500`、`503` 和未知错误的自动注入回归验证直观中文提示及内部信息隔离。
- API 冗余结论见 `docs/audits/WP-0014-m6-api-redundancy-audit.md`；`/api/v1/usage`、`/api/v1/audit-records` 和资源标识路径均有独立职责，未因名称相似删除。

## 自动验证

```text
make validate
结果：退出码 0。

项目治理：14 个工作包，当前前沿 WP-0014。
架构门禁：SQL 文件 0、超预算包 0、旧 api/adapter/runtimes 文件 0。
后端：完整 Maven clean test 反应堆通过，包含真实 PostgreSQL、Redis、MinIO、Flyway 与 Tomcat HTTP。
前端：typecheck、lint 通过；20 个测试文件、111 项测试通过。
构建：Vite 生产构建通过，2179 个模块完成转换。
交付：演示夹具 9 项、真实模型 5 项、恢复 8 项、容量 4 项、交付 6 项通过。
```

## 浏览器证据

已在 `http://localhost:13443` 完成以下无需真实凭据的 Chrome 回归：

- `1440×900`、`1024×768`、`390×844` 登录页无横向溢出；居中单列布局不再使用大面积蓝色品牌墙。
- 键盘 Tab 焦点可见；登录目标分段支持方向键切换和 Enter 激活。
- 真实 `401` 登录失败只显示“登录未成功，请检查登录信息后重试。系统不会显示凭据或会话细节。”，页面未出现错误码、追踪标识、异常或内部标识。
- 浅色主题和 `prefers-reduced-motion` 由浏览器 CSS 契约及前端回归共同固定。

截图：

- `project/evidence/artifacts/WP-0014-m6-ui/login-1440x900.jpg`
- `project/evidence/artifacts/WP-0014-m6-ui/login-platform-1024x768.jpg`
- `project/evidence/artifacts/WP-0014-m6-ui/login-390x844.jpg`

当前 Mac 锁定，且真实平台口令属于敏感输入；登录后平台运营页、租户建立向导、租户管理员和普通用户全流程，以及真实 Chrome 200% 浏览器级缩放必须在人工解锁并明确确认输入本地验收凭据后续证。本证据不得把该部分表述为已完成。

## 代码图谱与工程助手证据

- 代码图谱探索覆盖 `OperationsQueryService`、`ControlPlaneClient`、`ApiErrorResponse`、平台账户候选、运营范围和导航投影；影响分析确认运营重构不改变可信 RAG 运行链。
- 非局部包结构调整前检查了平台账户候选与运营视图的调用影响，据此按 `candidate`、`overview` 子包收敛，而没有移动无关领域代码。
- 当前插件运行环境为 Codex Desktop；能力来源为可用的 `engineering-assistant` 限定技能。
- 显式加载并使用 `engineering-assistant:wayfinder`、`engineering-assistant:repo-context`、`engineering-assistant:codebase-design`、`engineering-assistant:tdd`，并使用 `design-taste-frontend` 完成视觉预检与重构约束。
- 本轮未产生 `StageRunResult`，阶段结果校验不适用。
