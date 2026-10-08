# WP-0018：普通成员账户切片部署与原型一致性收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0018 |
| 日期 | 2026-10-08 |
| 状态 | 账户独立切片通过；WP-0018 整包和 M6 未完成 |
| 验证提交 | 71e3b42a93ece6874a19009483952e267b7e4d58 |
| 后端运行提交 | d71542a2675a1042c0a1051e5b9f0b78a5e563df |
| 前端运行提交 | 71e3b42a93ece6874a19009483952e267b7e4d58 |
| 原型修复提交 | 73d631a91e23306b46234133a87fd0714b10d533 |
| 验证范围 | 普通成员目录、三步原子开户、账号读取、唯一归属重置、当前资格停用恢复、初始功能与空助手拒绝、最小租户名称及导航外壳 |
| 未完成范围 | 独立功能保存、助手／策略统一授权、固定角色／完整工作区、组合操作及跨包正向旅程、九页十九操作整包验收、M6 人工接受 |

## 固定输入与方案选择

承接 [工作包](../README.md)、[生产契约](../production-contract-review.md)、[ADR-0093](../../../architecture/decisions/ADR-0093-member-account-and-effective-access.md)及[先前生产验证](WP-0018-account-production-verification-20261008.md)。旧证据仍表示当时原型门禁未通过，不改写为成功。开始及收口复核 Charter、ROADMAP、STATE 与本包 ADR，前沿及执行任务保持 WP-0018，里程碑保持 M6。

用户在 2026-10-08 委托比较提前／延后方案，并明确允许确认原型缺陷后先修原型、再修代码。ADR-0093 已接受以下选择：

| 方案 | 比较结果 |
| --- | --- |
| 全部延后第 4 片 | 第 1 片仍不满足名称和导航一致性，逐片收口与第 4 片形成循环依赖，不采用 |
| 仅提前真实租户名称和已交付导航外壳 | 消除共享外壳重复还原；只增加无租户参数的只读投影，采用 |
| 提前完整工作区、角色、策略和助手 | 扩大业务范围和依赖，当前账户验收不能覆盖，不采用 |

输入／基线 `2cca286848e174f5f7d3561712a9b1b86b382914`、调查 `5a6789c13a2a693bf77e8e83be7e197076e79ec8`、先前前端、原型修复、后端运行及本次最终源码依次可达。五项 fixture 已由 GOV-0002 提交，本轮未改动。源码与证据在原 `main` 工作树实施，不新增业务切片。

## 原型先修复，生产按原型承接

唯一设计来源是 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`：[成员目录](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-members&state=success&review=1)、[项目源码入口](http://127.0.0.1:58153/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)。端口可能随应用重启变化，项目 ID、路径与固定提交才是恢复依据。

入口／操作清单仍绑定 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，共享布局为 `47c4f6f31823617633ce0bac98074208781f5565`，目录为 `d36c7f46d0187c0421d4989c12f601d5c190d73f`。本次唯一原型修复固定为 `73d631a91e23306b46234133a87fd0714b10d533`：

- 实际原项目在 390px 普通字号有 1 个单元格裁切，200% 文字有 8 个；1024／1440 无此缺陷。普通字号未注入缩放样式，排除了测试注入原因。
- 经原项目 MCP 先修改 `prototype.css`，仅对 `tenant-members` 的小屏单元格使用自动高度并保留基准最小高度。没有改变导航、模拟数据或其他页面来迁就生产。
- 原项目三尺寸／两种文字大小共 6 个复验和 14 项原型测试通过；实际 HTTP 预览 17 个依赖文件均可读取，内容摘要与登记源码匹配。

生产随后承接暖色画布、字体、标题、租户名称、四组导航外壳、目录与向导、账户页及重置／停用弹窗。`GET /api/v1/tenant/workspace-context` 只返回当前可信租户管理员的 `displayName`，拒绝所有查询参数；普通成员／平台身份拒绝，缺失事实失败关闭，不新增表或 Maven 边。

原型完整四组七入口保持不变。生产当前连接成员管理、知识库、模型治理、评测治理四个已交付目标；工作区概览、访问治理及助手交付按此前接受的“对应功能实现阶段验证原型一致性”执行。导航展示与路由授权分开，已授权的 `/ask`、`/operations`、`/governance/query` 直接访问仍通过。未提前交付角色、策略、助手或工作区摘要。

## 保留失败，局部修正

| 真实失败或差异 | 最小修正与绑定提交 |
| --- | --- |
| 当前租户投影 HTTP 404，生产名称及导航未承接 | `d71542a2675a1042c0a1051e5b9f0b78a5e563df`；增加真实当前范围投影和 3 项 HTTP 反例，导航及会话代次回归 |
| 导航图标填色、透明度与原型不同 | `726ff8a4888aba582fe1de58d7da6788d525dcd5` |
| 1024px／200% 文字时页头动作键盘焦点受遮挡 | `265d010e52a8cf497e38d0a3b25f0961d7afceed`；增加焦点滚动避让，原断言复验 |
| 小屏目录网格、字号、间距及账户标签页不一致 | `9abd217b3cf5082d436b7ab7f68fb903976f02f9`、`5d54a4b1c3cacd6bfa8c0a39714a2985e187c602`；局部样式和步骤输入焦点 |
| 按钮、原生复选框、弹窗遮罩／宽度／阴影及导航选中环不同 | `db0c13ea99e3546f18c7ab9b1e70fa675d2bec92`；保留 Fluent Dialog 焦点机制，仅修正当前切片样式 |
| 1024px 操作列按钮高度约 89.94px，原型为 50px | `25c4447f0437fb735983b868b5e581ae36f1fa5a`；按原型恢复收缩和等高，三尺寸普通字号宽高匹配 |
| 上一修正在 390px／200% 文字重新触发裁切；Fluent 默认 `overflow:hidden` 改变自动最小宽度 | `71e3b42a93ece6874a19009483952e267b7e4d58`；当前操作按钮恢复 `overflow:visible`，42 个键盘布局及 21 个完整页面放大文字场景全部转绿 |

失败日志与截图保留于仓库外，未降低断言、放宽字号／裁切容差或删除步骤。`withTextScale` 的图谱源码确认只放大字号；没有通过固定控件宽度掩盖问题。没有为上述生产差异再次修改原型。

## 最终源码与真实部署验证

| 验证 | 结果 |
| --- | --- |
| `./mvnw -q verify` | 342 份 Surefire 报告、1,062 项，失败／错误／跳过均 0；后端绑定 d71542a，后续提交仅前端和证据变更 |
| `make frontend-test` | 最终候选 49 文件、253 项；typecheck、lint、构建通过 |
| 工作包入口测试 | Node 21 项、Python 18 项通过 |
| `make architecture-guard` | 48 项通过，后端依赖违规 0；成员目录已改用 `Page/IPage`，精确手工分页债务保持 4 |
| `make deploy-smoke` | 四个运行入口通过 |
| `make acceptance-work-package WP=WP-0018 SLICE=account` | 最终部署 9 组通过：目录与管理员保护、取消／刷新秘密清理、原子开户、冲突／伪造／旧版本拒绝、本人登录与空助手拒绝、唯一归属重置、旧会话撤销、停用恢复及布局 |
| `make acceptance-work-package WP=WP-0017 SLICE=login` | 当前镜像平台／租户登录回归 15 组通过；未重跑租户开户 |
| 最终只读布局 | 7 状态 × 3 尺寸 × 2 字号共 42 场景通过；另 21 个完整页面 200% 文字场景通过，覆盖键盘焦点、点击遮挡和 reduced-motion |

最终只读核实 Compose 七服务均 `running/healthy`，以实际容器镜像和 OCI revision 分别记录：

| 服务 | OCI revision | 镜像摘要 |
| --- | --- | --- |
| console-web | 71e3b42a93ece6874a19009483952e267b7e4d58 | sha256:901ae10b2dfa81605ae167d1c05d4fc983dd14fa5e7a6015ece627775de56dfc |
| control-plane | d71542a2675a1042c0a1051e5b9f0b78a5e563df | sha256:a729a60d838cccbce90e5418f6e57fb41a382bf0977343fb61cb2abca7538e08 |
| query-runtime | a726b323cf1f7d8ce9a4a674f5c76596128ac12f | sha256:76a5d76507c06bd82a118c3fb2c535bc48a7f981016b3266335c7e44707b19cb |
| ingestion-worker | a726b323cf1f7d8ce9a4a674f5c76596128ac12f | sha256:0c331d0db0e8014472622966835bd7a44c706b94cdf4fe188d3e371d5a5603d4 |

未把 HEAD 当作全部后端运行版本，没有重启 Docker、清数据或删除旧验收对象。本轮所有独立 Chrome 请求均经正常受控审批执行；无因审批服务容量不足而尚未执行的必需项。历史容量拒绝不代表本轮产品失败。

## 精确只读事实与原项目对照

本轮正式接口新成员为 `membershipId=2108127386938638338`、`subjectId=2108127386489847809`，租户为 `2093674235049279490`。`REPEATABLE READ READ ONLY` 事务只选择状态、版本及计数，最后 `ROLLBACK`：

- 同名身份及主体资格各 1；身份 `ACTIVE/version=1`，普通资格 `ENABLED/version=2`、`permissionVersion=2`，无待撤销标记。
- 仅 `TENANT_USER`；初始功能 `true/true/false/false`，版本 0；助手授权 0，查询运行 0。
- `CREATE/RESET/DISABLE/ENABLE` 各 1；管理员资格全局计数仍为 88。

最终生产与原项目 7 状态 × 3 宽度 × 2 字号共 84 次只读观测通过，无横向溢出、裁切或遮挡，业务写入 0；保存 84 张完整页面图及 14 张小屏视口图，仅在 `/private/tmp`。额外严格比较字体、颜色、控件、目录操作列宽高、弹窗、步骤和焦点样式。逐图查看了小屏七组原型／生产视口及 1024px 目录、390px 两倍文字目录；视觉结论为当前切片一致性通过，非用户新增人工接受记录，亦不声称本轮执行了原生桌面应用验收。

真实租户名称、成员状态、条目数和助手计数不复制原型模拟事实；名称长度导致的自然换行按相同布局处理。此前明确接受的演示密码说明例外继续适用，没有新增导航、间距或响应式例外。未实现目标仍按对应切片验收，不能据此声称九页全部一致。

## 证据恢复与工程过程

日志、一次性程序、失败复现、只读事实及截图在 `/private/tmp/wp0018-shell-parity.rX5CXj/`，目录 0700、文件 0600。集中清单 `evidence-manifest-closure.json` 包含 149 个非秘密文件（含 98 张截图），SHA-256 为 `b0c05582fc93e704d5f540777de05bf3c917dc654e69f8cd1f052938199f227f`。凭据文件及其摘要均不在清单内；用已知验收秘密检查这些文本证据，匹配数 0。临时文件不替代本仓库的结果记录；复验采用固定源码、独立运行镜像检查及正式验收入口，不为文档检查重复创建业务对象。

最终主要文件为 `account-closure.log`、`login-closure.log`、`deployment-facts-closure.json`、`account-facts-closure-validation.json`、`parity-green-overflow.json`、`shell-green-overflow.json`、`layout-overflow.log`、`full-text-overflow.log`、`visual-observations-closure.json`、`visual-review-closure.json`。原型、数据库、源码、构建和浏览器结果分别绑定，不用单个提交或截图替代其他验证。

Local Codex 使用个人工程助手 `2.2.0-rc.1+codex.local-20260929-051321`，显式加载 `engineering-assistant:wayfinder`、`codebase-design`、`tdd`、`code-review` 及安全 seam，依据本包／ADR-0093 的控制面执行内联任务；未产生 Runtime 或 StageRunResult，正式阶段结果校验不适用，不复用 WP-0017 Runtime。

CodeGraph 探索覆盖 `ProductShell`、`visibleNavigation`、`AuthenticatedConsoleApp`、租户读取端口、成员服务／目录及自动配置；对共享外壳、客户端及路由执行影响分析，对 `MemberAccountWorkspace`、`MemberCreateWorkspace` 执行局部影响追踪。结论用于分离导航展示与直接路由授权、复用组织事实端口、限定 CSS 作用域及排除字号测试机制问题；实际编译和真实验收另行证明结果。

证据与矩阵登记后，`make decisions-sync` 显式同步，`make project-verify` 77 项／30 个任务通过，历史资料仍按原 Git 对象校验；实现矩阵单独 24 项通过，`make architecture-guard` 48 项通过。`make project-status` 保持 M6／WP-0018，`make resume-context` 输出 8,989 字节，包含最终源码和下一动作，小于 12 KiB。对应日志为 `project-verify-closure.log`、`matrix-closure.log`、`architecture-closure.log`、`project-status-closure.log`、`resume-context-closure.log`，均在上述私有临时目录；未推进 STATE 或创建业务验收对象。

## 恢复前沿

账户独立切片闭合，矩阵仅将 `tenant-member-create:0`、`tenant-members:0/1/4/5/8` 六项标为 verified。`tenant-member-account:0` 包含进入尚未交付的独立设置页，保持 contracted；其余未完整交付操作同样只有已接受契约，不伪造组合操作通过。

下一动作是按 ADR-0093 开始第 2 片“四项对话功能独立保存”的设计与失败用例，包含独立版本、取消／刷新、交错保存及直接访问撤权；本次没有开始实施。第 3 片助手／策略统一授权、第 4 片固定角色／完整工作区仍未交付。已发布助手与问答正向旅程在 WP-0023／0024 联验，WP-0026 全量复验、WP-0014 人工接受保持有效。WP-0018 整包和 M6 未完成。
