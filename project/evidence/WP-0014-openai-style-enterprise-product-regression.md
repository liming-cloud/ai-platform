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
| `ea5cdaaa4` | 清除 SPA 可见内部实现术语和可信问答助手版本号 |
| `a0c8dde71` | 修正模型连接、部署与租户风险的业务健康统计 |
| `c06e239c8` | 补齐运营总览部分失败恢复、URL 状态和活动租户模型风险语义 |

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
- 完成度审计新增全 SPA 可见文案门禁：红灯稳定检出 19 处“内部标识、修订、服务端分页、厂商原始响应”等实现术语和 2 处可信问答助手版本号展示；整改后定向测试和全量前端回归通过。管理页面现在以资源名称、状态、影响和下一步操作表达任务。
- API 冗余结论见 `docs/audits/WP-0014-m6-api-redundancy-audit.md`；`/api/v1/usage`、`/api/v1/audit-records` 和资源标识路径均有独立职责，未因名称相似删除。

## 自动验证

```text
make validate
结果：退出码 0。

项目治理：14 个工作包，当前前沿 WP-0014。
架构门禁：SQL 文件 0、超预算包 0、旧 api/adapter/runtimes 文件 0。
后端：完整 Maven clean test 反应堆通过，包含真实 PostgreSQL、Redis、MinIO、Flyway 与 Tomcat HTTP。
前端：typecheck、lint 通过；22 个测试文件、118 项测试通过。
构建：Vite 生产构建通过，2180 个模块完成转换。
交付：演示夹具 9 项、真实模型 5 项、恢复 8 项、容量 4 项、交付 6 项通过。
```

## 浏览器证据

已在 `http://localhost:13443` 完成以下无需真实凭据的 Chrome 回归：

- `1440×900`、`1024×768`、`390×844` 登录页无横向溢出；居中单列布局不再使用大面积蓝色品牌墙。
- 最新 `ea5cdaaa4` 镜像再次在上述三种尺寸检查：浅色主题、视口宽度与文档宽度一致，页面正文不含内部标识、修订、追踪字段或助手版本号。
- 键盘 Tab 焦点可见；登录目标分段支持方向键切换和 Enter 激活。
- 真实 `401` 登录失败只显示“登录未成功，请检查登录信息后重试。系统不会显示凭据或会话细节。”，页面未出现错误码、追踪标识、异常或内部标识。
- 浅色主题和 `prefers-reduced-motion` 由浏览器 CSS 契约及前端回归共同固定。

截图：

- `project/evidence/artifacts/WP-0014-m6-ui/login-1440x900.jpg`
- `project/evidence/artifacts/WP-0014-m6-ui/login-platform-1024x768.jpg`
- `project/evidence/artifacts/WP-0014-m6-ui/login-390x844.jpg`

当前 Mac 锁定，且真实平台口令属于敏感输入；登录后平台运营页、租户建立向导、租户管理员和普通用户全流程，以及真实 Chrome 200% 浏览器级缩放必须在人工解锁并明确确认输入本地验收凭据后续证。本证据不得把该部分表述为已完成。

## 运营模型健康语义补全

完成度审计发现原运营事实源直接按资源生命周期 `status` 分组，导致最近一次探测失败的草稿连接仍显示为普通草稿，并把验证已过期或来源连接修订漂移的发布部署继续计作可用发布。提交 `a0c8dde71` 在不改变公共 API 和运营视图类型的前提下修正为业务健康语义：

- 连接以最近一次真实探测结果分类为 `VERIFIED`、`PENDING`、`FAILED`，已停用连接保持 `DISABLED`。
- 只有已发布、验证未过期、来源连接已验证且连接修订与部署冻结修订一致的部署计为 `PUBLISHED`，其余统一计入 `UNAVAILABLE`。
- 租户风险判断同时识别租户自有健康部署和由健康平台目录支持的活动模型配置，避免把已经具有可用平台模型路径的租户误报为无模型可用。
- 平台租户摘要新增服务器分页顺序、缺失查询事实零值补齐及跨事实源注意力合并回归。

红灯证据：真实 PostgreSQL 夹具首次执行时，失败连接仍得到 `{DRAFT=2, VERIFIED=1}`，与期望的 `{FAILED=1, PENDING=1, VERIFIED=1}` 不一致；修正 SQL 健康分类后转绿。

验证命令与结果：

```text
./mvnw -pl backend/contexts/model-runtime -am -Dtest=JdbcModelOperationsFactSourceTest -Dsurefire.failIfNoSpecifiedTests=false test
结果：2 项真实 PostgreSQL／Flyway 测试通过。

./mvnw -pl backend/contexts/evaluation-operations -am -Dtest=OperationsOverviewServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
结果：3 项运营聚合服务测试通过。

./mvnw -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=OperationsApiTest -Dsurefire.failIfNoSpecifiedTests=false test
结果：5 项真实 PostgreSQL／Redis／Tomcat HTTP 测试通过。

make validate
结果：退出码 0；完整 Maven 反应堆、前端 22 个测试文件共 118 项、生产构建和全部质量脚本通过。
```

## 运营总览恢复性与活动租户风险补全

完成度审计继续发现四项会影响人工验收的稳定缺口，提交 `c06e239c8` 已完成红绿修正：

- 平台总览和租户安全摘要由 `Promise.allSettled` 独立结算；一侧失败时仍展示另一侧成功数据，手动刷新失败时才保留并明确标注上一份成功快照。
- `DAY_1`、`DAY_7`、`DAY_30` 与平台租户摘要页码共同进入规范 URL，刷新、前进和后退不会静默回到七天范围。
- 组织事实源提供活动租户集合，模型事实源只判断这些租户是否具备健康的租户部署或租户范围平台目录配置；从未建立模型记录的新活动租户也会正确进入“模型不可用”风险，个人范围配置不能冒充租户级可用性。
- 运营注意事项严重度固定为 `INFO`、`WARNING`、`CRITICAL`，下钻目标改为实际发布的 `/platform/operations`、`/data/files`、`/models/deployments`、`/governance/query`，不再返回无法到达的内部路径。

红灯证据：前端首先因缺少独立结算加载器、路由未携带范围和状态组件不可测试而失败；后端首先因新活动租户事实契约缺失而编译失败，并由服务断言检出小写严重度及未发布路径。补齐最小实现后全部转绿。

验证命令与结果：

```text
./mvnw -pl backend/contexts/model-runtime -am -Dtest=JdbcModelOperationsFactSourceTest -Dsurefire.failIfNoSpecifiedTests=false test
结果：2 项真实 PostgreSQL／Flyway 测试通过，包含无模型记录活动租户和个人范围配置边界。

./mvnw -pl backend/contexts/evaluation-operations -am -Dtest=OperationsOverviewServiceTest -Dsurefire.failIfNoSpecifiedTests=false test
结果：4 项服务测试通过，包含严重度、公开下钻路径、分页补零和跨事实源风险合并。

./mvnw -pl backend/bootstrap/control-plane-bootstrap -am -Dtest=OperationsApiTest -Dsurefire.failIfNoSpecifiedTests=false test
结果：5 项真实 PostgreSQL／Redis／Tomcat HTTP 测试通过。

cd frontend && npm run typecheck && npm run lint && npm run test && npm run build
结果：22 个测试文件、118 项测试、2180 个生产构建模块全部通过。

make validate
结果：退出码 0；完整 Maven 反应堆、真实基础设施、前端、治理、架构、演示、恢复、容量和交付门禁全部通过。
```

## 从零人工验收基线

2026-08-17 的完成度审计确认，身份侧已经只有活动的 `platform.root`，但 `model_runtime` 仍保留上一轮浏览器验收建立的平台连接、发现、部署和目录事实。这与“只初始化平台管理员，再从页面完成全部建立操作”的人工验收起点不一致。

执行前先将完整 `model_runtime` schema 和数据以 PostgreSQL custom dump 保存到未纳入版本控制的本机文件 `/tmp/wp0014-model-runtime-full-before-reset-20260817.dump`，权限为 `0600`，并用 `pg_restore --list` 验证目录可读。随后在单一 PostgreSQL 事务中以以下前置条件失败关闭：全库只有一个活动 `platform.root`、没有租户、成员、知识空间或查询；条件满足后只清空 `model_runtime` 业务表。外键声明的助手和查询级联目标原本均为零行。Redis `DBSIZE` 清理前后均为 `0`。

重置后精确事实：

| 事实 | 数量 |
| --- | ---: |
| 活动 `platform.root` | 1 |
| 活动 `PLATFORM_ADMINISTRATOR` 绑定 | 1 |
| 租户／成员 | 0／0 |
| 平台模型连接／部署／目录 | 0／0／0 |
| 知识空间／助手／查询 | 0／0／0 |

该操作没有删除 `platform.root`、角色绑定、认证切换事实、Flyway 历史、PostgreSQL/Redis/MinIO 卷或任何服务。人工登录后的主流程必须从这一基线重新建立全部产品资源，不能复用旧验收事实。

## 代码图谱与工程助手证据

- 代码图谱探索覆盖 `OperationsQueryService`、`ControlPlaneClient`、`ApiErrorResponse`、平台账户候选、运营范围和导航投影；影响分析确认运营重构不改变可信 RAG 运行链。
- 非局部包结构调整前检查了平台账户候选与运营视图的调用影响，据此按 `candidate`、`overview` 子包收敛，而没有移动无关领域代码。
- 模型运营补全前探索 `JdbcModelOperationsFactSource`、`ModelOperationsFacts`、连接／部署状态和部署可用性投影；影响分析确认该事实源仅由模型运行时持久化自动配置装配，公共 DTO 与可信问答运行链无需改变。据此复用既有可用性不变量，在 SQL 聚合层按探测结果、有效期和来源连接修订分类，而没有增加前端合成统计或跨上下文表读取。
- 当前插件运行环境为 Codex Desktop；能力来源为可用的 `engineering-assistant` 限定技能。
- 显式加载并使用 `engineering-assistant:wayfinder`、`engineering-assistant:repo-context`、`engineering-assistant:codebase-design`、`engineering-assistant:tdd`，并使用 `design-taste-frontend` 完成视觉预检与重构约束。
- 本轮未产生 `StageRunResult`，阶段结果校验不适用。
