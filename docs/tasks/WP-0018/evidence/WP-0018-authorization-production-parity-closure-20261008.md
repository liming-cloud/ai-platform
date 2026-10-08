# WP-0018：助手／策略统一授权切片部署与原型一致性收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0018 |
| 日期 | 2026-10-08 |
| 状态 | 第三切片源码、部署、数据库事实、原型及控制面门禁通过；WP-0018 整包和 M6 未完成 |
| 本片输入基线 | 028fd44a7b8e203b47d62a8cbb170ed38c1086fe |
| 验证提交 | 9255ef44bd521a76bd4a1fea22a7f2c1aee8f217 |
| 前端运行提交 | 9255ef44bd521a76bd4a1fea22a7f2c1aee8f217 |
| 后端运行提交 | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 |
| 原型固定提交 | 35571b0f823390bd6b80f5771e512b11be9403a4 |
| 验证范围 | 当前租户普通成员助手独立设置、策略目录与不可变版本、真实预览和原子发布、撤权／冲突／会话、真实规模选择，以及本片原型对照 |
| 未完成范围 | 第四切片固定角色／完整租户工作区、生产已发布助手赋权正例及完整问答／历史／引用旅程、九页十九操作整包验收、M6 人工接受 |

## 输入与交付边界

开始及收口复核 Charter、ROADMAP、STATE、[工作包](../README.md)及其全部 ADR。本包输入 `2cca286848e174f5f7d3561712a9b1b86b382914`、本片输入基线、后端和最终验证提交依次可达。产品前沿及执行任务仍为 WP-0018，未更新 STATE，未开始第四切片或 WP-0019。

依据已接受的 [ADR-0093](../../../architecture/decisions/ADR-0093-member-account-and-effective-access.md)及[生产契约](../production-contract-review.md)，真实隔离发布种子用于本片授权正例；不能据此宣称生产助手发布已经交付。WP-0023 负责生产发布后赋权，WP-0024 负责完整真实问答／历史／引用正向旅程。本轮没有新增并行 ACL、权限框架、Maven 依赖边或无调用方抽象。

本片交付：

- `GET/PUT /api/v1/tenant/members/{membershipId}/assistants`，仅返回六字段安全投影；完整集合与授权 revision 独立保存。管理员只读，普通成员、错租户、未发布及不存在对象拒绝；旧无变化请求也必须校验版本。
- 助手选择和策略发布共用成员—助手授权事实及同一 revision。保留未发布的既有选择只允许保留／移除，不允许新增；当前租户发布事实由公开 facade 核实并锁定。
- 策略目录、草稿保存、真实预览、发布和创建新版本分别执行；DRAFT／PUBLISHED／SUPERSEDED 不原地改写历史正文，最多一个草稿和一个当前发布版本。旧预览和旧发布不能恢复已撤销权限，新草稿不隐式赋权。
- 预览使用与运行时相同的有效访问交集，绑定草稿、成员功能、租户上限、授权 revision 和助手发布事实。发布重新核对并原子更新目标助手的成员集合，保留其他助手授权；审计或会话撤销失败回滚业务变化。
- 账户、功能、助手三页独立读取／保存与往返；非秘密草稿按会话代次、主体、租户和目标隔离，保留／放弃／取消及退出清理有效。真实搜索先在后端过滤，再按标准页码、页大小和总数分页，选择跨页／跨搜索保留。

## 失败复现与修复

失败、诊断和最终通过材料分开保留，没有降低断言、放宽字号／裁切容差或删除历史验收对象。

| 复现 | 局部修复与证明 |
| --- | --- |
| 助手接口、策略版本和页面不存在 | 成员助手、策略、草稿、路由及预览最小用例先失败；`46fb0dd5f0149e7bac155b87a8aa9a9f21d16914` 实现契约、事务、运行时裁决与前端，完整后端验证通过 |
| 普通开户读取发布对象后没有保持发布行锁 | `create-lock-red.log` 复现；在既有事务中通过公开 facade 锁定本租户已发布助手，保留统一锁顺序和原子开户 |
| SQL 守卫未展开带命名空间的 MyBatis include | `qualified-sql-red.log` 复现；补齐精确展开及缺失／循环拒绝，两个多表查询登记 MULTI_TABLE 原因，未提高分页债务预算 |
| 原型 390px 卡片最小高度不足，以及 1440px 预览数 3992 被省略 | 先在原项目的原生浏览器测试复现。按用户“确认原型有问题可先修原型”的授权补充 ADR-0093，分别提交 `3761e81451d9cced6edbc125d9295d553899234f`、`35571b0f823390bd6b80f5771e512b11be9403a4`；最终六项原型布局测试通过。只修缺陷，不改变页面、操作或权限契约 |
| 草稿／历史标签、空目录伪辅助行、小屏主要操作、分页字号与目录 24px 间距不符 | 对应真实失败日志保留；先补用例，局部修正状态文案、真实空态和原型布局。未用静态四成员／助手样本代替真实规模 |
| 导航行框和 CSS 构建精度 | flex 修复后，严格比较仍发现 200% 下 0.015625px 高度差。实际构建把 `1.428572` 截为 `1.42857`；新增高度断言在生产 `assistants/390/200` 真实失败。`23e36b4cb15a1ce2c43a194348f24cf7e6225c81` 局部采用精确行高计算，保持完全相等断言 |
| 视口切换后过早读取主要操作宽度 | `isolated-browser-maven-final-23.log` 保留；复用已有布局稳定检查，宽度容差仍为 0.01，没有跳过步骤 |
| 人工看图发现新建弹窗缺少标题分隔区，预览／保存／发布文案偏离原型 | 第 26 轮标题断言、第 27 轮反馈断言分别失败；`9255ef44bd521a76bd4a1fea22a7f2c1aee8f217` 补齐标题分隔、原型原文及真实版本替换。第 28 轮完整通过，最终比较包含标题分隔区 |

中间通过不替代最终版本。最终原型对照没有响应替换、候选 CSS 注入或 API mock。早期 Docker buildx 沙箱写入限制按正常受控权限执行恢复，没有绕过审批；本轮没有审批服务容量拒绝，没有因此尚未执行的必需验收。

## 源码、隔离浏览器与部署

| 门禁 | 实际结果与定位 |
| --- | --- |
| `./mvnw -q verify` | 343 份报告、1,087 项，失败／错误／跳过均 0；`backend-verify-final-3.log` 和当场冻结的 `backend-verify-final-3-summary.json`，不混入后续单项重跑的 XML |
| `make frontend-test` | 最终 `frontend-final-19.log`：52 文件、265 项，typecheck、lint 和构建通过 |
| 工作包工具 | 最终 Node 46 项及两个浏览器脚本语法检查通过，`work-package-node-final-9.log`；既有 fixture 校验通过，未修改 fixture |
| 隔离真实浏览器 | `isolated-browser-maven-final-28.log` 与 `isolated-browser-9972026749427961565.log`，七组全部通过；真实 PostgreSQL／Redis、真实前端构建、控制面与独立 query-bootstrap 进程，不连接生产数据库 |
| 规模及预览事实 | 1,000 名普通成员、200 个已发布助手；跨搜索／分页选择、刷新恢复、取消／离开／退出、草稿不赋权、保存／预览／发布、旧页 409、撤权后旧发布 409、新版本和已保留历史、旧会话 401 均通过。四项实际判定共 4,000，允许 8、拒绝 3,992、泄漏 0；普通成员目录只见明确授权的发布版本 |
| `make deploy-smoke` | 最终四入口通过，七个 Compose 服务 running／healthy |
| `make acceptance-work-package WP=WP-0018 SLICE=assistants` | `assistants-browser-production-parity-final.log`，最终部署八组全部通过：正式接口独占准备、无变化保存、版本／字段／CSRF、管理员／租户／发布保护、真实空目录、本人负例、三尺寸／两倍文字／键盘／reduced-motion、退出重登 |
| `make acceptance-work-package WP=WP-0017 SLICE=login` | `login-browser-parity-final.log`，最终部署和本轮新凭据的十五组通过，不创建租户 |

账户九组、独立功能十三组回归分别见 `account-browser-closure.log`、`features-browser-closure.log`，它们实际运行于前端 `6858ea490c5d9ba32fd3003745c8843f3f2614f2`、后端 `46fb0dd5f0149e7bac155b87a8aa9a9f21d16914`。此后的生产代码仅修改助手页专属导航和策略页面；没有把这些旧回归冒写成最终前端重跑。最终八组已重新验证账户／功能／助手往返；本轮后端从完整验证提交到最终前端的 Git tree 完全相同。

实际部署入口为 [可信 RAG](http://127.0.0.1:13000)。版本按容器事实分别记录，`deployment-facts-parity-final.json` 还固定后端 tree `f73d7d5ee97ed4cee0c9fceb25d6a610c098fa45`：

| 服务 | OCI revision | 镜像摘要 |
| --- | --- | --- |
| console-web | 9255ef44bd521a76bd4a1fea22a7f2c1aee8f217 | sha256:2afb2dbc853295c5d1621e7b16053af9d395bf6a2e1e4a93db646d086776c4d7 |
| control-plane | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 | sha256:a4bf11ac45b22546c655bf1860216f4f2dc08d43fbe6a01e34f40a89a80c02dc |
| query-runtime | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 | sha256:e86ae969f45e8422cc0088ec0be328be94fc64488c3d76201f2528e80218ead0 |
| ingestion-worker | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 | sha256:0c76fe72dddf8835107b74eeabc2a6730e26eb8d7140002499dcb5b576f7a347 |

`202610080003`／`202610080004` 迁移成功、四张策略表及 ASSISTANTS 审计约束由只读事实确认。没有把最终 HEAD 当作后端运行版本，没有重启 Docker、清缓存／数据卷、重置或删除业务数据。

## 精确只读数据库事实

最终助手验收租户 `2108209473691582466`、资格 `2108209478145933314`、主体 `2108209477797806081`，追踪后缀 `4ec1d40f55a1`。`verify-production-facts-parity-final.py` 使用 `REPEATABLE READ READ ONLY` 和最终 `ROLLBACK`，只选择精确范围的版本、状态、计数及审计类别，不读取秘密字段：

- 同名身份、主体资格和初始管理员资格各 1；角色仅 TENANT_USER。身份 ACTIVE、资格 ENABLED；身份、资格、permissionVersion、独立功能 version 和授权 revision 均为 0，无待撤销标记。
- 功能保存值 true／true／false／false；助手授权、策略、策略版本、策略审计、查询运行均为 0。只有 CREATE 1，无变化保存、取消及拒绝请求不增加写入。
- 账户回归资格 `2108202584463376386` 为 ENABLED、资格／权限 version 2，身份 version 1、功能 version 0；CREATE／RESET／DISABLE／ENABLE 各 1。
- 功能回归资格 `2108202829146488834` 的资格／权限 version 8、身份 version 0、功能 version 6；CREATE 1、FEATURES 6、DISABLE 1、ENABLE 1；租户上限 version 2、授权 revision 0。
- 前片历史成员 `2108156327682375682` 的精确状态、版本和审计与执行前一致；前一候选的助手验收对象也与已冻结事实一致。全局租户／管理员资格从 73／91 变为 79／97，匹配本轮六次正式开户；最终候选相对执行前仅增加 1／1。所有中间对象保留。

通过材料为 `database-facts-validation-parity-final.json`、`assistants-facts-parity-final.sql/json`、`account-facts-parity-final.sql/json`、`features-facts-parity-final.sql/json`、`prior-assistants-facts-parity-final.json` 和 `history-features-after-parity-final.json`。这些生产空态事实不证明隔离发布对象已进入生产。

## OpenDesign Cloud 原项目对照

唯一设计来源仍为项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`：[可用助手](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-member-assistants&state=success&review=1)、[策略目录](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=access-policies&state=success&review=1)、[策略版本](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=access-policy-detail&state=success&review=1)、[项目源码入口](http://127.0.0.1:58153/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)。端口可变，恢复依赖项目 ID、入口和固定提交。仓库 `docs/prototypes/wp-0016` 是原项目解析到的源码目录，没有另建副本。

入口／操作清单继续绑定 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`；布局、目录和本片输入分别承接 `47c4f6f31823617633ce0bac98074208781f5565`、`d36c7f46d0187c0421d4989c12f601d5c190d73f`、`73d631a91e23306b46234133a87fd0714b10d533`。上述两项获授权缺陷修复后的固定源码为 `35571b0f823390bd6b80f5771e512b11be9403a4`。`original-source-facts-final.json` 核对原项目 HTTP 的十七个依赖全部 200，摘要与固定 Git 内容一致。MCP 元数据刷新 Transport closed，实际对照使用原项目仍正常服务的 HTTP 与项目源码入口，不使用生产副本；本轮没有原生桌面应用验收结论。

七种状态为助手、未保存提示、策略目录、新建弹窗、草稿、预览和已发布；每种 390／1024／1440 × 正常／200% 文字。原项目 42 次、隔离真实实现 42 次，以及实际生产助手／策略目录 12 次，共 96 次。原项目与生产只读观测业务写入均为 0。

| 严格比较 | 结果 |
| --- | --- |
| 原型与隔离实现 | 8,928 项绘制／排版属性、1,116 项横向位置／宽度、138 项控件高度、78 项相对纵向锚点，差异 0 |
| 原型与实际生产 | 2,208 项绘制／排版属性、276 项横向位置／宽度、60 项控件高度、78 项相对纵向锚点，差异 0 |
| 人工逐图复核 | 最终生产十二张全部查看；七对原型／隔离代表状态十四张查看，记录 `manual-parity-review-parity-final.json`，不是用户人工接受 |

观测包含暖色画布、16px 正文及 Georgia／中文宋体标题、R／可信 RAG、三个独立对象入口、短按钮、24px 目录间距、标题分隔、禁用状态、分页、完整 3992 计数、键盘、reduced-motion 和可滚动弹窗操作可达性。最终聚合为 `original-observations-f495661e.json`、第 28 轮的四十二份隔离样式事实及 `production-observations-cabbd7ee.json`；比较器和两份结果为 `compare-parity-final.py`、`original-isolated-parity-parity-final.json`、`original-production-parity-parity-final.json`。

比较边界明确保留：真实名称、选择、数量和规模分页来自实际数据，不能复制原型四个助手或演示权限；因此自然换行、列表长度及滚动不冒称相同。原型专用“演示成员”切换器属于 ADR-0079 的模拟评审控件，不进入生产身份模型；390px 下额外 66.390625px 控件加 8px 间距形成 74.390625px 绝对内容原点差，原始坐标完整保留，业务区相对锚点分别比较。React／原生结构的 minHeight、minWidth、margin 计算差异分别保留 174／48 项，不能代替实际控件尺寸通过；生产真实空集合和依赖禁用状态单列二十四项事实绑定。这里没有宣称全屏图片逐像素相等，也没有新增导航、辅助文案、间距或响应式视觉例外。尚未交付导航依对应切片复验，不能将这 96 次扩张为九页／66 页全量通过。

## 工程过程与私有材料

材料集中于 `/private/tmp/wp0018-unified-authorization.wZyeXE/`，目录 0700，文件 0600；截图没有进入仓库。`evidence-manifest-parity-final.json` 登记 199 项非秘密材料及内容摘要，其中截图 96 张，清单 SHA-256 为 `d636dcb3631f3a0bc757c37e5665b0eab62cd27c86c66c4abf4a61b46fcd230d`。只按明确白名单收录最终观测及失败／通过日志；凭据、运行配置、秘密及其摘要均排除，所选文本与已知验收秘密值的匹配数为 0。仓库保留本结果、源码提交及原项目定位，临时材料不替代可复现的正式入口。

当前环境为 Local Codex，个人工程助手 `2.2.0-rc.1+codex.local-20260929-051321` 提供能力；显式加载 `engineering-assistant:wayfinder`、`codebase-design`、`tdd`、`code-review` 及 security-seams，使用工作包、ADR 和固定 diff 内联执行。没有创建 Runtime 或 StageRunResult，阶段结果校验不适用，没有复用旧 WP-0017 Runtime。

CodeGraph 先探索成员账户／有效访问、助手发布、知识库名称、客户端、路由、对象导航与分页守卫；对 `MemberAccountStore`、`MemberEffectiveAccessService`、`AssistantRepository`、`KnowledgeSpaceRepository`、`ConsoleRoute`、`MemberObjectNavigation` 和 `manual_list_limit_statements` 进行影响追踪。结论用于保持公开 facade、复用唯一授权集合、锁定发布事实、限制导航 CSS 作用域及补齐 SQL include 守卫；图谱不替代编译、事务和真实浏览器测试。

## 控制面验收与下一动作

本片只新增七项完整操作 verified：`tenant-member-assistants:0`、`access-policies:0`、`access-policy-detail:0/1/2`、`tenant-members:2`、`tenant-member-account:0`，全部绑定上述完整验证提交和本证据。功能／助手交错保存及账户进入三页的组合条件已齐备；原有七项验证不改写历史日期或来源。

WP-0018 当前为十四项 verified、五项 contracted。第四切片的 `tenant-roles:0`、`tenant-overview:0/1/2`，以及依赖真实问答联验的 `tenant-members:7` 仍未完成。下一动作是按既有 ADR 设计第四切片并核对概览的真实数据和目标页依赖，本轮不实施该片；所有切片和整包门禁完成后才进入 WP-0019。M6 人工门禁保持未完成。

登记后 `make decisions-sync`、`make project-status`、`make project-verify`、实现矩阵校验、`make architecture-guard`、`make demo-fixtures-verify` 和 `make resume-context` 全部通过，输出分别保存为对应命令的 `*-parity-final.log`。治理 77 项／30 个任务、矩阵 24 项、架构 50 项／后端边界违规 0、fixture 9 项／2 场景／8 检索用例通过；恢复入口 11,665 字节，清楚指向第四切片的设计与依赖核对。STATE 的执行任务、产品前沿与里程碑均未变化。按用户授权提交并推送后另核对实际 GitHub CI，不把本地门禁冒写为远端 CI 已通过。
