# WP-0018：租户固定角色独立切片部署与原型一致性收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0018 |
| 日期 | 2026-10-09 |
| 状态 | 固定角色独立子片的源码、真实部署、浏览器、只读事实和本片原型对照通过；完整工作区、WP-0018 整包和 M6 未完成 |
| 本片输入基线 | 374a8e7232024d422bf5c310c8f0f131fa3f3036 |
| 验证提交 | 1359a3bac9c00b7f23709387bfbdcd8f75d22ac2 |
| 前端运行提交 | 1359a3bac9c00b7f23709387bfbdcd8f75d22ac2 |
| 后端运行提交 | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 |
| 原型固定提交 | 35571b0f823390bd6b80f5771e512b11be9403a4 |
| 验证范围 | tenant-roles 的两个固定模板、职责说明、管理成员导航、刷新前后退、管理员只读、三类身份直接访问、退出、三尺寸／两倍文字／键盘／reduced-motion |
| 未完成范围 | tenant-overview:0/1/2、tenant-members:7、完整租户工作区和生产问答正向旅程、WP-0018 整包、66 页生产实现及 M6 人工接受 |

## 输入与实现

开始及收口复核 Charter、ROADMAP、STATE、[工作包](../README.md)及其全部 ADR，执行 `make project-status`。输入 `2cca286848e174f5f7d3561712a9b1b86b382914`、本片基线和验证提交可达；实际部署和原型版本分别确认。产品前沿、执行任务均保持 WP-0018，STATE 不变。

依据 [ADR-0093](../../../architecture/decisions/ADR-0093-member-account-and-effective-access.md)及已接受原项目，仅重做租户角色分支：标题为“租户固定角色”，四项事实说明管理员、普通成员、自定义角色与授权依据；唯一主要操作进入成员目录。移除旧角色分配说明、详情抽屉及多余任务页签，页面自己拥有真实 h1。平台固定角色分支、服务端权限、业务 API、后端、fixture 和原型均未修改。

没有将“管理成员”解释为同级管理员治理或角色编辑。实际目录的管理员行显示只读、没有操作按钮；账户投影仍为既有十二字段，固定角色是 TENANT_ADMINISTRATOR，passwordResetEligible 为 false。普通成员及平台身份不能从 URL 或 API 取得租户治理事实。

## 红绿反馈与失败材料

| 复现 | 修复与证明 |
| --- | --- |
| 租户页缺少原型 h1，并出现角色分配和详情流程 | `fixed-role-red.log`：四项中一项失败；局部替换租户分支及装配，最终前端 266 项通过 |
| 既有入口测试仍把已交付 assistants 当作非法切片 | `acceptance-entry-baseline.log` 保留既有失败；按实际契约恢复有效入口测试，新增 roles 明确范围／禁止 SETUP 的两个反例先失败，最终 22 项通过 |
| 新角色验收脚本假设账户含 readOnly 字段 | `roles-browser-initial.log` 保留失败；源码及既有十二字段投影证实字段不存在。`admin-projection-red.log` 先失败，改为同时核对只读 UI、固定角色和禁止重置事实，仍拒绝额外秘密字段，未修改业务接口或删除保护断言 |
| 原项目直接跳转受保护页超时 | `original-role-observe-diagnostic.log` 保留；补齐原项目已有的模拟登录前置，实际角色 DOM 可读，未认定为产品缺陷或修改原型 |
| 200% 文字按钮比原型少 0.015625px | `role-parity-paint-final.json/log` 保留 21 项相关几何差异；真实部署 `roles-height-serialized-red.log` 在 390/200 场景失败。复用已有精确比例写法，仅在租户角色按钮设 `calc(1em * 20 / 14)`；最终实际高度 54px，几何门槛保持 0.01px，未放宽断言 |
| 恢复全文与十二项 ADR 导航超过 12 KiB | 首轮 `project-verify-closure.log` 和 `resume-context-closure.log` 如实失败；压缩任务正文中重复的历史验证摘要，保留原证据和恢复决定，预算及治理工具不改，第二轮恢复成功 |

`roles-height-red.log` 的成员目录前置失败发生于登录回归与角色验收重叠运行，未形成高度结论；保留材料并改为串行执行，随后成功到达上述精确高度失败。最终各浏览器门禁串行运行，不用被其他会话干扰的结果代替最终证据。

## 源码与实际部署

| 门禁 | 结果与私有材料 |
| --- | --- |
| `make frontend-test` | `frontend-final.log`：52 文件、266 项，typecheck、lint、测试及构建通过 |
| 工作包工具 | `node-final-2.log`：49 项；`acceptance-entry-final.log`：22 项；Shell 入口及新增浏览器脚本语法通过 |
| `make architecture-guard` | `architecture-final.log`：50 项，后端依赖边界违规 0，前端产品门禁通过；没有抬高手工分页存量预算 |
| `make demo-fixtures-verify` | `fixtures-final.log`：9 项、2 场景、8 个检索用例通过；fixture 未修改 |
| `make deploy-smoke` | `deploy-smoke-height-final.log`：四入口通过；七个 Compose 服务 running／healthy |
| `make acceptance-work-package WP=WP-0018 SLICE=roles` | `roles-browser-height-final.log`、`roles-report-height-final.json`：十三组全部通过，业务写入及页面脚本错误均为 0 |
| `make acceptance-work-package WP=WP-0017 SLICE=login` | `login-browser-height-final.log`：最终部署十五组通过，不设置 SETUP、不创建租户 |

十三组覆盖：完整固定职责、导航及刷新前后退／管理员只读、键盘进入目录、390／1024／1440 × 100%／200% 六组、普通成员拒绝、平台拒绝、匿名要求登录、退出后不可访问。新增正式入口复用现有三类身份，仅输出非秘密报告，不准备、重置或删除业务对象。

真实入口为[租户固定角色](http://127.0.0.1:13000/access/roles)，需当前租户管理员登录。本次实际运行事实冻结于 `deployment-facts-height-final.json`：

| 服务 | OCI revision | 镜像摘要 |
| --- | --- | --- |
| console-web | 1359a3bac9c00b7f23709387bfbdcd8f75d22ac2 | sha256:87a72bd1eb4ea2bde8e494898b444e35e16759ae54a7313f8358a346d08dca83 |
| control-plane | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 | sha256:a4bf11ac45b22546c655bf1860216f4f2dc08d43fbe6a01e34f40a89a80c02dc |
| query-runtime | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 | sha256:e86ae969f45e8422cc0088ec0be328be94fc64488c3d76201f2528e80218ead0 |
| ingestion-worker | 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 | sha256:0c76fe72dddf8835107b74eeabc2a6730e26eb8d7140002499dcb5b576f7a347 |

只更新 console-web。其他六个服务启动时间保持，未重启 Docker、清缓存或数据卷。后端 Git tree 为 `f73d7d5ee97ed4cee0c9fceb25d6a610c098fa45`，与此前完整验证的 46fb0dd5 一致，`git diff 46fb0dd5f0149e7bac155b87a8aa9a9f21d16914 HEAD -- backend` 无差异；本轮没有将此前 1,087 项 Maven 结果写成新重跑，也没有把 HEAD 当成后端运行版本。

## 精确只读事实

复用第三片已验证的合成租户 `2108209473691582466`、普通资格 `2108209478145933314`、主体 `2108209477797806081`，追踪后缀 `4ec1d40f55a1`。凭据从既有 0600 文件只在内存读取，没有覆盖或复制凭据。

`roles-facts.sql` 使用 `REPEATABLE READ READ ONLY` 和最终 `ROLLBACK`，只读取精确范围的状态、版本和计数。`db-facts-before.json` 与 `db-facts-after.json` 完全相同，`database-facts-validation-final.json` 通过：

- 同名身份、主体资格、租户普通成员和初始管理员各 1；目标仅 TENANT_USER，身份 ACTIVE、资格 ENABLED，无待撤销标记。
- 身份、资格、权限、独立功能 version 及授权 revision 均为 0；只有 CREATE 1。
- 助手授权、策略、策略版本、策略审计及查询运行均为 0；未新增业务对象或审计动作。
- 全局租户 79、管理员资格 97，执行前后不变。没有读取密码、摘要、会话、正文或其他秘密字段。

## 原项目对照与明确范围

唯一设计来源仍为 OpenDesign Cloud 项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`：[固定角色入口](http://127.0.0.1:58138/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=tenant-roles&state=success&review=1)、[项目源码入口](http://127.0.0.1:58153/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)。端口可变，按项目、screen 和固定提交恢复。

当前 MCP 正常读取原项目，解析目录仍为 `docs/prototypes/wp-0016`。固定源码 `35571b0f823390bd6b80f5771e512b11be9403a4` 与当前原型目录无差异；实际 HTTP 的 index、CSS、full-platform、workspace-journeys 和 page-fixtures 五项关键来源均 200，内容与固定 Git 对象逐字节相同，见 `original-source-facts-final.json`。未修改原型或创建替代副本。

`measure-role-parity.cjs` 对原项目与最终部署分别执行三尺寸／两倍文字，共十二次只读观测；`role-parity-height-final.json/log` 的 2,346 项实际绘制属性、552 项横向位置／尺寸及相对纵向锚点比较差异为 0。双方业务写入为 0；`manual-parity-review-final.json` 记录十二张最终截图全部实际查看，属于 Local Codex 复核，不是用户里程碑人工接受。

原始 CSS 值全部保留。LTR 的 start／left 解析为同一绘制方向；9999px／999px 胶囊圆角按 CSS 实际半高缩减后相同；生产 body 透明，实际 main 祖先画布与原型均为 `rgb(250, 249, 245)`。六组仍分别保留 23 项序列化差异，不把透明 body 或同义值误判为绘制差异，也不以属性相同替代实际按钮尺寸验证。

比较范围为本片固定角色正文、标题／摘要、职责区域、关注说明及管理成员操作；暖色画布、16px 正文、Georgia／中文宋体标题、R／可信 RAG 与适用布局一并查看。真实租户名称按身份显示，其自然换行不能复制原型样本。租户概览、助手交付及其他未交付导航仍由对应切片复验，390/200 下由未交付导航形成的外壳高度不同不计入本片相对正文锚点；原始完整截图保留，不宣称整个导航、完整租户工作区或全屏逐像素相等。没有新增导航视觉例外，也没有用无目标按钮补齐尚未交付功能。

## 工程过程与材料保护

本轮材料在 `/private/tmp/wp0018-fixed-roles.ScwDA2/`，目录 0700、文件 0600，截图未入库。业务验证结束时 `evidence-manifest-final.json` 登记 99 项非秘密材料、其中 36 张历轮截图，清单 SHA-256 为 `103460aac4a2cad3ef561eb281ee8557c2aee39612a1012bfdc4fa009d9a7010`。凭据及其摘要排除，所选文本匹配已知验收秘密值为 0；其后控制面与 CI 日志另存，不冒称已在这份固定清单内。

默认执行器及 CUA 因可写根符号链接配置未启动；正常受控执行审批通过，独立 Chrome 真实运行。没有绕过审批、修改权限配置或因此留下本片必需验收未执行。本轮没有容量拒绝或 OpenDesign 原生桌面应用验收结论。

当前插件环境为 Local Codex，个人工程助手 `2.2.0-rc.1+codex.local-20260929-051321` 的已提供技能为能力来源；显式加载 `engineering-assistant:wayfinder`、`codebase-design`、`tdd`、`code-review`，并读取 wayfinder HITL 与 TDD security-seams。按 WP-0018、ADR-0093、固定 diff 与内联红绿反馈执行，未创建 Runtime／StageRunResult，阶段结果校验不适用，没有复用旧 WP-0017 Runtime。

CodeGraph 先探索 RoleDirectoryWorkspace、AuthenticatedConsoleApp、PageHeading 及账户投影；对前两者执行深度二的影响分析。结果用于把变更限制到租户分支与页面装配，保留平台分支和服务端固定角色，不修改重要领域服务或公共契约。图谱只证明结构，不能代替编译、部署或浏览器结果。

## 控制面与下一动作

仅新增 `tenant-roles:0` 为 verified，绑定本证据及完整验证提交。WP-0018 当前十五项 verified、四项 contracted，仍有 `tenant-overview:0/1/2` 与 `tenant-members:7` 未完成；前三片及本片的独立通过不等于完整第四片、整包或 M6 通过。

现行顺排要求本包完成后进入 WP-0019，而概览和完整有权新会话依赖后置知识／索引／助手／问答包，形成待决定的计划依赖。已提出移交既有 WP-0027／WP-0024 或前移依赖的选择，尚未收到决定；当前十九项归属和原型全部保留，不提前修改 ADR、激活 WP-0019 或标记整包完成。未触及共享按钮的小数行高存量登记到 WP-0026 全量复验，不能从本片局部修复推断其他入口均通过。

更新恢复入口后 `make decisions-sync`、`make project-status`、`make project-verify`、实现矩阵单元测试与校验、`make architecture-guard`、`make demo-fixtures-verify` 和 `make resume-context` 均通过。治理 77 项／30 个任务、矩阵 24 项、架构 50 项、fixture 9 项／2 场景／8 个检索用例通过；`*-closure-2.log` 保留压缩后的治理与恢复结果，恢复命令完整输出 11,750 字节，未提高 12 KiB 预算。其余实际命令定位见上表。本地门禁不代表远端 CI，提交并按既有授权推送后再核对最终提交的 GitHub Actions。
