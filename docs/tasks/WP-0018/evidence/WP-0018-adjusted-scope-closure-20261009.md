# WP-0018：调整后完整范围的部署复核与收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0018 |
| 验证范围 | ADR-0094 调整后的八个主要入口、十五项责任操作；历史写入证据、后续源码审计及当前部署只读集成复验 |
| 实施基线 | ba81ef6ace6d10729468d8df8b0ee9664935b5d1 |
| 验证提交 | 8ade6bbe15a53fdf73a1fea794e642632f59b835 |
| 后端源码／运行提交 | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 |
| 原型固定版本 | 35571b0f823390bd6b80f5771e512b11be9403a4 |
| 依据／日期 | ADR-0079、ADR-0082、ADR-0093、ADR-0094；2026-10-09 |
| 结论 | 调整范围的 WP-0018 整包闭合；四项后置操作仍 contracted，M6 未完成 |

## 接受边界与原操作绑定

用户收到精确调整提案后答复“同意并推进任务”，接受依据见 [ADR-0094](../../../architecture/decisions/ADR-0094-member-scope-handoff-and-closure.md)。`tenant-overview` 及三项操作移交 WP-0027，`tenant-members:7` 的历史关闭后授权新会话正例移交 WP-0024。原型 ID、文案、分类及全部目标不变；全清单 78 页／155 项，M6 仍为 66 个生产页面／132 项生产操作。本轮归属调整新增 verified 为零。

本包八个主要入口为 `tenant-members`、`tenant-member-create`、`tenant-member-account`、`tenant-member-features`、`tenant-member-assistants`、`tenant-roles`、`access-policies`、`access-policy-detail`。十五项责任操作保留原验证日期、提交及证据：

| 操作 ID | 原验证提交 | 原完整证据 |
| --- | --- | --- |
| tenant-member-create:0；tenant-members:0/1/4/5/8 | 71e3b42a93ece6874a19009483952e267b7e4d58 | [账户切片](WP-0018-account-prototype-parity-closure-20261008.md) |
| tenant-member-features:0 | 6206dec074854ae253d555a389b582d78091aade | [独立功能切片](WP-0018-features-production-parity-closure-20261008.md) |
| tenant-member-account:0；tenant-member-assistants:0；tenant-members:2；access-policies:0；access-policy-detail:0/1/2 | 9255ef44bd521a76bd4a1fea22a7f2c1aee8f217 | [助手／策略统一授权切片](WP-0018-authorization-production-parity-closure-20261008.md) |
| tenant-roles:0 | 1359a3bac9c00b7f23709387bfbdcd8f75d22ac2 | [固定角色切片](WP-0018-fixed-roles-production-parity-closure-20261009.md) |

逐操作核验原提交可达、证据存在及正文的完整提交。后续源码复核确认：发布助手锁与关系写入仍在同一事务，重置／停用逻辑未变；独立功能和助手页分别保留保存版本与离开保护；授权切片后新增固定角色读取页、导航和本轮局部呈现修复，策略保存、预览、发布、新版本命令体未修改。后端与实际运行提交同树，树对象为 `f73d7d5ee97ed4cee0c9fceb25d6a610c098fa45`；当前完整回归中的成员、认证、事务、分页及查询权限相关二十套测试共 103 项通过。

千成员／二百助手、真实写入、并发撤权、隔离浏览器七组及 4,000 次裁决继续由原切片证明。允许 8／拒绝 3,992／泄漏 0 属于原授权切片，本轮没有重做写入或规模验收，没有创建业务对象或改动历史验收对象。

## 失败证据与局部修复

- 归属、缺少接受依据、跨包完成和入口反例先出现八项真实失败，再修复矩阵守卫和归属。只读入口配置测试先因实现缺失失败，再实现独立入口，不转调写入切片。
- 真实部署发现品牌字重、行高、标识前景色和创建／账户短按钮放大文字高度不符原型，仅修正共享品牌和本包控件样式。后续目录对照又发现页头按钮、分页精度、禁用绘制及原生选择框字号／盒高差异，保留 66 项差异和正式入口失败后修复。原生选择框按原型真实 `normal` 行高、字号与盒高比较，未放宽字体、裁切或几何断言。
- 公共绘制一度通过，但看图发现真实策略 403 仍是裸错误文字，不能据公共样式绿灯关闭工作包。新增两项读取失败分类测试和正式浏览器反例，真实失败后局部实现原型受限态及安全重试态；不改变公共 StatePanel 或策略命令，异常不回显服务端错误和跟踪值。
- 原型账户页须从成员目录选择对象并携带实际 `memberAccountId`；生产 403 与原型 `denied` 比较。错误对象、重复选择器、错误类名和状态配对的诊断均保留，校正测量后继续修复真实产品失败，未删除验收步骤。

## 实际部署与唯一原型

| 进程 | 实际 OCI revision | 实际镜像 |
| --- | --- | --- |
| console-web | 8ade6bbe15a53fdf73a1fea794e642632f59b835 | `sha256:7652c5de0335b5ffbf8f94a59ab7d587c7d446cc154482389bb74efac1356e54` |
| control-plane | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 | `sha256:a4bf11ac45b22546c655bf1860216f4f2dc08d43fbe6a01e34f40a89a80c02dc` |
| query-runtime | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 | `sha256:e86ae969f45e8422cc0088ec0be328be94fc64488c3d76201f2528e80218ead0` |
| ingestion-worker | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 | `sha256:0c76fe72dddf8835107b74eeabc2a6730e26eb8d7140002499dcb5b576f7a347` |

按既有 Dockerfile、`APP=console-web` 和精确 OCI revision label 构建，仅更新前端。最终七服务 running／healthy、四入口 smoke 通过；其余六服务镜像和启动时间未变。浏览器和数据库复核后再次检查仍相同，未重启 Docker、删卷、清缓存或重置数据。

OpenDesign Cloud 唯一原项目为 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，注册原目录的[入口文件](../../../prototypes/wp-0016/index.html)为 `index.html`。成员入口参数为 `screen=tenant-members&state=success&review=0`；其余七入口沿用上列稳定 ID。访问时由 MCP `get_project` 返回该项目当前 `studioUrl`／`previewUrl`，避免把短期服务地址当作长期事实源。

本轮正式 `get_project`／`get_artifact` 核实原注册、目录和完整来源，浏览器直接访问原项目。入口／操作人审基线为 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，最新已接受版本为 `35571b0f823390bd6b80f5771e512b11be9403a4`。本轮未改原型或建立副本；最终五个 HTTP 来源与固定 Git 对象、候选原目录逐字节相同。账户基线后仅有既有策略小屏 CSS 及其反例，没有改变账户设计。

## 最终验证与事实限制

| 检查 | 结果与范围 |
| --- | --- |
| `make frontend-test` | 52 文件／268 项；typecheck、lint、构建通过 |
| 完整后端 `./mvnw -q clean test` | 343 份 JUnit 报告／1,087 项，失败、错误、跳过均 0；执行源码 `1d8cd331d308a0a7f427b2da68bc33d6002fd8f0`，后续直到最终候选后端树未变，不冒称在最终文档提交重跑 Maven |
| Node／Python | 工作包脚本 52 项；入口 25 项、实现矩阵 28 项，合计 53 项通过 |
| `make acceptance-work-package WP=WP-0018`，SLICE 留空 | 60 场景通过；读取 1,803、认证 4、业务写请求 0、浏览器异常 0 |
| `make acceptance-work-package WP=WP-0017 SLICE=login` | 当前部署十五项登录、刷新、CSRF、角色入口及退出回归通过，不创建业务对象 |
| 原型／部署严格对照 | 八页 × 三尺寸 × 正常／200% 文字 × 原项目／生产，96 次观测；3,924 项严格比较差异 0、业务写入 0；包含目录控件和真实 403 状态面板 |
| 状态面板专项 | 十二次观测／672 项比较，差异 0；不代替上行八页复验 |
| 看图复核 | Local Codex 逐张查看最终 24 张：八页移动 200% 双侧十六张，目录／账户／角色／受限态桌面双侧八张；不代替 WP-0014 人工接受 |
| PostgreSQL | 既有精确合成对象的 `REPEATABLE READ READ ONLY`，`transaction_read_only=on`；前后 JSON 字段完全相同，文件排版不同不声称字节摘要相同 |
| 架构／治理 | 切换控制面后重跑通过：架构 50 项、依赖违规 0、存量手工分页精确债务 4；治理 81 项、30 任务／650 历史定位；参考数据 9 项、status／resume 通过，恢复输出 5,799 字节，小于 12KiB |

整包入口拒绝 SETUP、未知切片和不安全凭据／输出，复用既有三类身份。凭据为所有者独占普通 0600 文件，输出位于新 0700 临时目录。浏览器在发送前阻断任何业务写请求，仅精确认证建立及退出另计，不按 `/auth` 前缀泛化豁免。覆盖搜索分页、管理员只读、独立设置取消、开户草稿取消、三身份及匿名 URL/API 越权、旧会话失效、390／1024／1440、两倍文字、键盘和 reduced-motion。

精确合成对象标识只留在外部只读事实，仓库记录计数及状态：身份 ACTIVE、成员 ENABLED、TENANT_USER，身份／资格／权限／功能／授权 revision 均为 0，CREATE 保持 1，策略、版本、审计、助手授权及查询计数均为 0。目标租户普通成员 1、管理员 1，全局租户 79、管理员资格 97 未变。

原型演示数据不替代真实事实。现有会员开启对话及助手选择，历史和引用关闭；当前生产无已发布助手或策略，目录是真实空结果，新建前置不满足，版本读取真实返回 403。本轮对照相应受限态，正向创建／保存／预览／发布由原授权切片的真实隔离证据及同后端树回归承接，不冒称生产存在成功版本或本轮执行问答。演示成员切换器不复制到真实身份会话。

受限态“返回已授权入口”按 ADR-0079 的真实租户管理员角色返回 `/tenant/login`，不照搬模拟会员的 assistants 目标；这是身份契约承接，不是新增视觉例外。未交付导航按既有明确延期，不将已交付范围通过描述为完整工作区或全量逐像素一致。没有因审批容量不足尚未执行的必要验收。

## 外部材料与评审方法

完整日志、诊断、结果及截图只在 `/private/tmp/wp0018-package-closure-onrja0tc/`，目录 0700、文件 0600。`evidence-manifest-final.json` 的 SHA-256 为 `5adcdd4db3fd390d312889a34b3bed083e37ebbdc2724841668379ae4a822705`，仅收十一份逐份检查内容的安全结果 JSON，排除日志、脚本、截图、凭据及其摘要；后续控制面和 CI 另记。全目录摘要曾被自动审批以无法证明未知文件安全拒绝，未执行；有限白名单及内容检查正常批准。真实凭据值仅在内存扫描，当时 125 份文本匹配为 0，未输出秘密或密码摘要。首次证据写入因对象标识和短期地址被拒绝，未写入；本稿移除这些字段，外部事实与固定项目定位保持可复核。

Local Codex 个人工程助手 `2.2.0-rc.1+codex.local-20260929-051321` 提供限定技能；已显式加载 `engineering-assistant:wayfinder`、`engineering-assistant:tdd`、`engineering-assistant:code-review` 及 TDD 安全边界参考。控制输入为根 AGENTS、Charter、ROADMAP、STATE、工作包全部引用 ADR 及本次 ADR-0094。内联任务未产生 Runtime／StageRunResult，阶段结果校验不适用，不复用旧 WP-0017 Runtime。

CodeGraph 探索任务索引、矩阵守卫、验收配置、ConsoleApp、TenantMemberWorkspace、MemberDirectory、ProductShell、AccessPoliciesWorkspace 及 AccessPolicyWorkspace／StatePanel／ApiRequestError。影响分析覆盖索引五个关联符号、矩阵二十四个测试函数、ProductShell 两个、TenantMemberWorkspace 四个、MemberDirectory 两个及 AccessPolicyWorkspace 两个图节点；不把图谱节点数当作穷尽调用。结果使变更限于既有归属、只读入口和局部呈现，避免扩大公共 StatePanel 重构。固定 diff 评审核对写阻断、精确认证例外、状态安全、实际原型状态、历史绑定及目标守恒；真实回归和部署仍是验收依据。

## 收口与下一前沿

仅在上述门禁全部通过后完成 WP-0018 调整范围，STATE 激活 WP-0019 设计。下一步读取模型包全部 ADR，核对原项目十一入口、现有接口和厂商官方资料，形成专属契约及最小切片；本轮不开始模型业务实现。

激活任务仅把 WP-0019 的 backlog 正文迁至任务 README，并将历史索引一条 `destination` 更新到该现行路径；原资料提交、摘要、原因和验收时间未变，650 项原对象仍完整校验。最终文档提交与实际前端、后端版本分别记录，推送后另核对精确提交的 GitHub Actions，不以旧提交的 CI 代替。

`tenant-members` 因 WP-0024 的新会话正例仍为 in-progress；WP-0027 的租户概览三项仍 contracted。平台概览 M6/M8 事实及四个目标边界仍待决定。WP-0019～0025、WP-0027、WP-0026 及 WP-0014 人工复评均未完成，M6 未完成。五项 fixture 已由 GOV-0002 提交，本轮没有 fixture 差异或仓库截图新增。
