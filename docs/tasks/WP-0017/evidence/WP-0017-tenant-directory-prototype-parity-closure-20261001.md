# WP-0017：租户目录切片部署与原型一致性收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0017 |
| 验证范围 | `platform-tenants:0–1`；生产只读目录、建立入口、搜索及原型一致性 |
| 实施基线 | 7d97b4d31754c46d8f713a762d9d99c6a13d1858 |
| 验证提交 | a908aaad7efab91f77c4221a2d27ba1754de2091 |
| 后端查询验证提交 | 90a28e1bed18fbbda628cb7831ee84982f847adb |
| 最新目录原型提交 | d36c7f46d0187c0421d4989c12f601d5c190d73f |
| 合同／日期 | ADR-0090；2026-10-01 |
| 结论 | 目录／建立入口／搜索独立切片闭合；WP-0017 整包及 M6 未完成 |

## 交付范围与实际运行

生产承接已接受的四列只读目录、真实生命周期及管理员资格摘要、名称／编码搜索、状态筛选、10/20/50/100 标准分页和有效页跳转。查询条件保存在 URL，刷新与历史恢复，条件变化回到第一页；加载、失败、空结果、越界及旧请求迟到有明确反馈。“建立新租户”导航到已交付原子开户，取消不创建对象；“查看详情”进入同一目标的已交付概览。删除目录中重复的生命周期写操作与管理员弹窗，既有后端命令及其历史证据不变。

后端过滤在 PostgreSQL 执行，名称／编码不区分大小写，`%`、`_`、反斜线按字面匹配；权限检查先于过滤和事实读取。管理员摘要读取真实资格计数，不向目录返回身份或秘密。前端异步结果绑定查询、页码、页大小、会话与刷新轮次，失效或迟到请求不能恢复旧事实。没有新增数据库结构、上下文依赖、公开 facade 或其他业务功能。

| Compose 服务 | 实际 OCI revision | 运行镜像 ID | 状态 |
| --- | --- | --- | --- |
| console-web | `a908aaad7efab91f77c4221a2d27ba1754de2091` | `sha256:3cb75b782cba7a4ba4bb6f295312385911b8a7d13270283b2072e10a6485fcdb` | healthy |
| control-plane | `90a28e1bed18fbbda628cb7831ee84982f847adb` | `sha256:cc5ca1f77a04a197de00578c5d9c510ac37d315c8dbbbf9ccb73c12d54cfdc18` | healthy |
| query-runtime | `ef11d6aad0ffb8d8a656d5a17a3477d93b95a5c8` | `sha256:4a847f9631eee38204096f9ea66c159dcfcfbb25f17406ffcecab473795d5c5d` | healthy |

按最终前端提交构建，只用正常 Compose `up --detach --no-build --no-deps --wait` 更新 console-web，`make deploy-smoke` 四入口通过。后端查询候选已由[查询执行证据](WP-0017-tenant-directory-execution-20261001.md)分别绑定；本次最终 UI 部署没有更新后端或查询进程。最后核对 7 个服务均健康，上表 revision 与镜像符合实际，不能用收口文档 HEAD 代替运行版本。

## 先修订设计，再承接生产

唯一设计来源仍为原 [OpenDesign 项目](http://127.0.0.1:49175/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)，项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，入口 [platform-tenants](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenants&state=success&review=0)。

用户明确授权“修订并接受 OpenDesign 目录设计”。先在同一原项目解决无事实源的区域／异常数字、完整名称换行、状态与资格语义，固定初版 `9a1747609d41bf546e78486954539cd4b79fbbb9`；随后发现原型空结果错误使用 `colspan=20`，先证明四列表格结构缺陷，仅恢复目录空行为四列，固定最新 `d36c7f46d0187c0421d4989c12f601d5c190d73f`。生产依据上述接受结果实现，不修改原型迁就生产，也没有新增视觉例外。原型修订、失败与接受详见[原项目接受证据](WP-0017-tenant-directory-opendesign-acceptance-20261001.md)。

最新原型契约／渲染 **10/10**、真实浏览器 **12/12** 通过；77 个非目标页面的七态及两类查询共 **1078** 个共用分页输出无变化。最终直接读取原项目七份资源，与最新固定 Git 对象及工作树逐字相同：

| 原项目资源 | SHA-256 |
| --- | --- |
| full-platform.js | `8ac4ea977b1b8fdbffa50171a6365896730e5ae1d80256acc2515fd0cf17a2ce` |
| page-fixtures.json | `5e0bb8ec3db2713966615958050a5bf42a79dc84f00a9e352fec8a32e8a2e24e` |
| prototype.css | `6a0559abb3f40ced67559302ec1e64a3ac6e4e91ab5d49d55a74e4df7d31e53a` |
| object-workspaces.js | `80e5d53d47ee6daacd8e08b5f18f2cd4962c256347ad14d3f17248686180ccab` |
| tokens.css | `ad0cd644358eb724f3aa2261b2d86fe357bf10e7603bc3d21295132177d55385` |
| page-inventory.json | `bb53681c82e4b2f445316107ad689590660472aab550e7fc930aac72f9fd07c8` |
| ACTION-LEDGER.json | `77ff6446f4707f0eb2fd46d3d546d1f638270565281a756ba9507713f3a4316f` |

原 66 页／操作清单的 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`、共享移动布局 `47c4f6f31823617633ce0bac98074208781f5565` 及历史人工接受保持原绑定，目录修订仅替代本切片。没有新 OpenDesign 项目或可编辑副本。

## 最终生产浏览器与视觉验收

真实 Chrome／Playwright `1.62.1` 在最终前端镜像执行 `make acceptance-work-package WP=WP-0017 SLICE=tenants`，**15/15** 通过，实际监听业务 API 写请求 **0**、页面异常 **0**。使用既有隔离租户，受控入口明确拒绝 `SETUP`；没有 API 响应替身或创建业务对象。

- 真实名称、编码、排序、同范围总数、70 个租户的管理员资格聚合、字面字符、大小写／首尾空白和状态过滤；非法条件精确拒绝，租户／匿名／退出后请求先验证平台身份，不泄露行或总数。
- 四列目录、无旧区域／模拟异常数／额外写操作；条件 URL、刷新／历史、页大小、下一页及有效／无效整数跳转、空结果、越界页真实总数和返回第一页。
- 建立入口／取消及同一目标详情导航均无业务写入；真实请求延迟后快速切换、网络离线与重试不恢复旧事实。
- 390/1024/1440px 正常与 200% 文字，全名称／编码、控件中心命中、无裁切／行重叠；390px 原生键盘与 reduced-motion 导航。

实际库中 70 个目录对象均有管理员资格，因此生产验证的零资格样本数为 **0**。零／正资格及真实文字转义分别由生产 `TenantDirectoryTable` 的 SSR 反例测试、后端查询测试验证；原型使用合成零资格夹具。未创建对象补样本，也不宣称实际生产已观察到零资格对象。

之后串行运行 `make acceptance-work-package WP=WP-0017 SLICE=login`，最终镜像 **15/15** 通过，覆盖平台／租户入口、指定租户、错误角色、刷新、CSRF、退出和 reduced-motion；没有覆盖或重写旧凭据文件。

视觉只读对照 **10 组**：三尺寸正常、三尺寸 200% 文字、三尺寸过滤空结果及 390px reduced-motion。比较标题、页头、建立入口、只读徽标、摘要、搜索、筛选、表格与事实、详情、分页总数／页大小／跳转等 **18 类区域**；几何容差保持 **1px**，字体、字号、字重、行高、颜色、背景、边框、圆角、内距和间距精确比较，差异 **0**，业务写请求 **0**，页面异常为空。空结果仅在两侧均无事实行时不比较四类事实元素，其他缺失区域视为失败。

为比较相同文字长度，观测仅在原型浏览器的既有合成存储中设置与既有目标相同的非秘密名称、编码和生命周期。未写回 OpenDesign 文件，未注入生产事实或替换 API 响应。原项目代码始终为上述固定版本，生产数据始终来自实际接口。人工查看最终三尺寸正常／扩字及空结果、reduced-motion 生产截图和原型扩字对照，确认完整换行、颜色、标题、控件、分页没有遮挡；暖色画布、16px 正文、既有标题字体与主题保持。未交付导航范围沿用 ADR-0083，未增列例外。

## 失败保留与局部修复

修复前保留搜索忽略条件、迟到结果覆页及后端过滤失败；新表格／标题、验收配置与入口、零资格呈现均有最小失败测试。真实部署发现重复标题、目录间距／窄屏详情按钮及空结果差异后，分别局部修正 `ConsoleApp` 的目录标题分支与目录 CSS，不扩大共享组件。原型四列空结果错误由独立 `AssertionError` 先证明，再按既有目录修订授权接受；没有降低容差、删除验收步骤或改写旧失败。

执行脚本的多行 locator 严格匹配、reduced-motion 场景未切回 390px、退出导航被重复 `goto` 中断，均单独复现并修正观测顺序；保留全行事实断言、实际视口及退出后精确 401。首次摘要核对使用不存在的账本文件名返回 404，按现行矩阵改为 `ACTION-LEDGER.json` 后七份资源校验通过。这些观测问题不作为产品缺陷或通过证据。旧审批容量事件没有发生在本轮；本轮验收均已正常受控执行，没有因审批容量尚未运行的项。

## 只读业务事实与源码验证

部署／浏览器前后执行同一 `REPEATABLE READ READ ONLY` 脚本，输出逐字相同，SHA-256 均为 `9a96acd6703ba0b64ba45105a111d4d00a9755fce5aceb5909ce96fd46ccf096`。目标 tenantId `2104457357567197185`、subjectId `2104457357567197186`：生命周期 `ACTIVE/1`，主体 `ACTIVE/0`，管理员／启用管理员均为 1，其他租户管理员资格 0；配额／功能／安全版本均为 0，生命周期事件 1。全库生命周期 70、身份 189、平台账户业务审计 126。

只核对上述业务状态、版本和计数，不读取秘密字段，不把正常认证会话操作描述为所有数据库零写入。没有重跑开户验收、重启 Docker、清缓存、删卷、重置数据或修改其他设置。

| 验证 | 实际结果／绑定 |
| --- | --- |
| `make frontend-test` | 最终前端源码：类型检查、lint、构建通过；47 文件／243 项 |
| Node 验收脚本配置测试 | 34/34，含 `tenants` 的只读配置与 SETUP 拒绝 |
| Python 工作包验收入口 | 14/14，含明确切片及只读限制 |
| `./mvnw -q verify` | 后端 `90a28e1b…`：337 份报告／1030 项，通过；来自前述查询执行证据，未伪称在最终 UI 提交重跑 |
| `make project-verify` | 源码与收口控制面均为 69 项、29 任务及 650 项历史定位通过 |
| `make architecture-guard` | 38 项及实际门禁通过；后端依赖边界违规 0，既有手工分页债务 3 不变 |
| `make project-status`／`make resume-context` | 收口控制面更新后通过；保持 M6／WP-0017，恢复命令输出 12127 字节，未超过 12 KiB |
| `git diff --check`／fixture 核对 | 通过；相对本轮基线 fixture 无变化，截图未进入仓库 |

## 证据定位与方法

日志、脚本、指标及截图仅在 0700 的 `/private/tmp/wp0017-tenants.Nby8Vthj`；受控验收源码与本记录进入 Git。凭据只由现有校验器读取既有 0600 文件，密码、Cookie、Token 和密码摘要不进入证据。关键非秘密摘要如下：

| 临时证据 | SHA-256 |
| --- | --- |
| tenant-directory-frontend-accepted-final.log | `14179136c9f1b1e68c55fc47e5921b113551f8525830a3e892b41657914e1098` |
| tenant-directory-browser-final.log | `ff0d17eff1b87ede0dbcea7a5cd844744b8061f470eed7c67a122c9e9e7b2a28` |
| tenant-directory-login-final.log | `91f906157020573ba1c7eef4bcc979c8e5b8fbaf36faf51c25f77081dfa8cbb6` |
| directory-parity.cjs | `85a74f202518dc922b94f607f9349ef80ac7bc824f5c4a6ec0feaeca0a0445b4` |
| directory-parity-metrics.json | `d542aafc4da392bac7096350297488b494fc16023d25de1d1833209d3be3d7ed` |
| directory-parity-diff.json | `4f53cda18c2baa0c0354bb5f9a3ecbe5ed12ab4d8e11ba873c2f11161202b945` |
| tenant-directory-running-final.json | `fc66ffbfe8693bcfa07ad667bcecf702b15cac4b19220aa066736871bbe55739` |
| opendesign-directory-source-final.json | `f81903e7749a1c8a7f996ff331da17f6d16c20efe67c5931c0251c520f06a571` |
| opendesign-directory-empty-red-assertion.tap | `93a943b0cd39a4815dce5f7f22aca7bb190f2e5e8781ceb0c3f1344f15ea771f` |
| opendesign-directory-empty-green.tap | `9fc1309a27f3100ef5368939a4dee58d28b84f0a7475438fda957827590926ac` |
| opendesign-directory-empty-browser-green.tap | `064b8630ec4042292299ccf763260c3e2cdd2ab56b05aa43d4f3f59772b70966` |
| opendesign-directory-empty-scope.json | `c1a5200f3c65e27233f858a8f7797147c68e8a56a5251fa1d64ad0d808862a48` |
| tenant-directory-node-green.tap | `e34c4e4141caf5988136ed875c76561414c88d7020aba1de2c82a6e9084a6125` |
| tenant-directory-entry-green.log | `dc918e3334ee8a00b951da033e6c0f9c8c017387a463378de78df9a54fc32032` |
| tenant-directory-project-closure-accepted.log | `9113198542fab387b5c674d43fde35dbe7dfdde8f84755a43a5f32e03ce3f0e8` |
| tenant-directory-architecture-closure-final.log | `5ae47b5d3106e942699a1da30dfc84a1fdbd03a51d659a6e4f8b33a8f2ded70a` |

收口治理首次及补充校验正确拒绝新记录缺少标准“责任任务／验证提交／验证范围”元数据；两份失败日志保留。补齐绑定后全部 69 项及真实控制面通过，只改证据格式，没有修改校验器或绕过提交绑定。矩阵保持其他页面正文与格式，仅更新目录对象。

Codex 本地 `engineering-assistant` 能力来自 `2.2.0-rc.1+codex.local-20260929-051321`。显式加载 `wayfinder`、`tdd`、`code-review` 的 `SKILL.md` 及其 HITL／security-seams／mocking 相关参考，执行内联切片与固定 diff 评审；无 Runtime 或 `StageRunResult`，阶段结果校验不适用，未复用旧 Runtime 声称通过。

代码图谱先探索目录查询链、`PlatformTenantLifecycleWorkspace`、路由／客户端、`renderPlatformTenants` 与 `boundedDirectoryMarkup`；分析工作区 4 个、原型渲染／分页各 4 个关联符号。发现重复标题后追踪 `AuthenticatedConsoleApp`／`ConsoleApp` 各 2 个关联符号，只改变目录标题分支；请求归属复用实际调用方，未新增全局抽象。图谱不能完整返回 JSX／中文测试／CSS 时补充精确范围读取，最终以编译、实际接口和浏览器证据判断，不以结构索引替代验证。

## 收口与下一前沿

仅将 `platform-tenants:0–1` 和目录页标记 `verified`，绑定最终前端提交与本证据；任务恢复指针关联本记录及原型接受／查询历史证据。12 个入口中 **10 个**已验证，租户登录部分已验证；21 项本包生产操作中 **19 项**已验证。

仍待独立处理 `platform-overview:0` 首个阻断项、`tenant-login:1` 三类登录入口切换，以及既有分页门禁退出条件。产品前沿与执行任务保持 WP-0017，STATE 无前沿变化，整包证据／提交保持 `pending`，M6 未完成。本轮不自动开始上述切片、WP-0018、知识接入或查询；后续先确认其合同与原型边界。旧部分执行证据和旧接受不改写，本记录不将 66 页原型接受描述为生产全部实现。
