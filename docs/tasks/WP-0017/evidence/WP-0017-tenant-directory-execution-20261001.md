# WP-0017 租户目录查询与读取归属执行证据

## 结论与范围

本记录覆盖 `platform-tenants:0–1` 的查询与读取归属修复，不是目录切片完成证据。后端与前端源码验证通过；实际候选部署上两项原失败复现、13 组只读查询回归及 15 项登录回归通过，所核对业务事实前后相同。目录表格、搜索控件、完整分页与原型视觉验收尚未完成，区域列及异常统计的事实冲突待用户决定。实现矩阵保持原状态，WP-0017 整包和 M6 未完成。

固定仓库基线为 `7d97b4d31754c46d8f713a762d9d99c6a13d1858`，ADR-0090 合同提交为 `5f2acc48532ee3a91efa4f8fd7dada73ab3ebd20`，修复源码为 `90a28e1bed18fbbda628cb7831ee84982f847adb`。只触及组织上下文的目录读取、既有 HTTP 合同和前端目录请求／路由；不增加上下文、公开 facade、扩展依赖或数据库迁移。五项 fixture 内容与基线相同。

## 原型与待决定事项

- OpenDesign 原项目：`249f81f2-6f1a-46ff-988c-da3cdedb35e7`。
- [原项目设计入口](http://127.0.0.1:49175/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)。
- [租户目录原型](http://127.0.0.1:49162/api/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/raw/index.html?screen=platform-tenants&state=success&review=0)。
- 页面固定版本：`21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`；共享布局：`47c4f6f31823617633ce0bac98074208781f5565`。
- 本轮直接读取原项目内容，`full-platform.js` 与页面固定 Git 对象字节一致，SHA-256 为 `d650c1ae6c000263fd97be0d650f2c375ece3de8fb00998cb758955060a5adbe`；`prototype.css` 与共享布局固定 Git 对象字节一致，SHA-256 为 `645f7b50c68c1defd668eda26175db87c8798ea61a7fdf677a5a3a4c25bb2a87`。

原型的“驻留区域”和“安全边界异常 0”没有当前生产事实源。已向用户提出两种承接方式：接受去除区域列、说明真实数据来源、异常数显示“尚未核实”；或先在 OpenDesign 修订并接受目录设计。尚未收到选择，不把等待时间当作接受，不填充模拟区域或数字，也不登记视觉例外。根 AGENTS 的“若与原型发生实质冲突，先恢复决策一致性”适用；已授权的查询与异步修复独立继续。

## 真实失败与最小测试

所有临时脚本、日志均在权限 `0700` 的 `/private/tmp/wp0017-tenants.Nby8Vthj`；未保存截图、浏览器 trace、Cookie 或 Token。凭据仅由既有校验器从当前用户独占的 `0600` 文件读取，内容不进入本记录。

| 反例 | 修复前结果 | 修复后源码验证 |
| --- | --- | --- |
| 真实 Chrome 使用既有租户精确编码搜索 | 返回总数 70、第一页 20，预期唯一对象，失败 | 相同脚本与断言在实际候选返回总数 1、行数 1；数据库及 HTTP 验证通过 |
| 真实 Chrome 延迟旧页实际请求，切换第二页 | 第二页 URL 被旧页迟到结果覆盖，失败；只延迟请求，未替换服务端响应 | 相同脚本与断言在实际候选保持第二页结果；7 项清理／失效单元测试通过 |
| 真实 PostgreSQL 搜索与状态参数 | 两项反例均返回 2 而非 1；既有排序正例通过 | 5 项持久化测试通过，使用实际生产适配器 |
| 前端 URL 恢复查询、状态、页大小 | 2 项反例失败、31 项通过 | 路由 33 项通过 |
| 客户端向服务器传递字面查询与状态 | 1 项反例失败、42 项通过 | 客户端 43 项通过 |

数据库反例先直接向原映射器传递相同的过滤参数证明忽略条件；修复后升级为实际生产适配器调用，保留并加强过滤、总数与字面字符断言。新增服务测试的首轮执行有两项测试输入缺少既有 `SESSION_ACCESS`，已纠正合法会话输入；该输入错误不是产品失败，不代替前述红灯。过滤模型、服务及真实持久化的最终定向验证为 11 项通过。

过滤在 PostgreSQL 以参数绑定执行，转义 `%`、`_` 和反斜线。未知状态、超长与空字符拒绝；权限检查先于过滤和数据读取。管理员只返回当前租户资格数量，不读取或返回身份、成员明细与秘密。框架分页与同范围总数保持一致。前端旧结果只能归属于当前查询；取消、会话失效和迟到成功／失败不能恢复旧页。

## 源码与治理验证

| 命令或入口 | 本轮结果 |
| --- | --- |
| `./mvnw -q verify` | 337 个本轮报告，1030 项测试，失败／错误／跳过均为 0 |
| `make frontend-test` | 类型检查、lint、构建通过；47 文件、242 项测试通过 |
| 定向 API 与路由测试 | 2 文件、76 项通过，包含在前端完整结果中 |
| 定向过滤、权限与 PostgreSQL | 11 项通过，包含在后端完整结果中 |
| `make architecture-guard` | 38 项守卫测试及后端／前端门禁通过；依赖边界违规 0，既有手工分页债务 3 |
| `make project-verify` | 69 项治理测试通过；29 任务与历史证据校验通过 |
| `make resume-context` | 源码阶段 11858 字节，加入本记录后 11967 字节；最终文档复核另见下文 |
| `git diff --check` | 通过 |

Maven 数量只统计完整验证日志开始之后更新的 `target/surefire-reports/TEST-*.xml`，不把历史报告计入本轮。源代码测试不证明生产浏览器或原型一致性。最终文档更新后 `make decisions-sync` 已执行，`make project-verify` 的 69 项及项目校验再次通过，`make project-status` 仍为 M6／WP-0017，`make resume-context` 为 11936 字节；21 项临时证据摘要逐一匹配，fixture 相对本轮基线无差异。移出任务活跃证据短列表的旧开户证据文件仍保留，原绑定及历史验收事实不变。

## 实际部署与业务事实

部署前实际健康容器为：

| 服务 | OCI revision | 镜像 ID |
| --- | --- | --- |
| console-web | `a82202465fdf62bc1b8d157fa5902c4e93db78c6` | `sha256:2fc2019d7e096af9017f779c809f2e1726ed678821b70d27c45646f7ffeb0d49` |
| control-plane | `9cd1f8ff5fea06c7d0b33b6a5a641c587464d1d4` | `sha256:fc8583b6119ce2eb72fddc08f9fc9596bd2dd36f7310e1a348769d8cc562db86` |
| query-runtime | `ef11d6aad0ffb8d8a656d5a17a3477d93b95a5c8` | `sha256:4a847f9631eee38204096f9ea66c159dcfcfbb25f17406ffcecab473795d5c5d` |

两份候选镜像构建成功，只用正常 Compose `up --detach --no-build --no-deps --wait` 更新 control-plane 与 console-web，实际容器健康，OCI revision 均为 `90a28e1bed18fbbda628cb7831ee84982f847adb`：

| 服务 | 实际候选镜像 ID |
| --- | --- |
| console-web | `sha256:c92cf071801a3b27dd75d342d6c032d2106edf156c698198e5dbce7ff79e26d1` |
| control-plane | `sha256:cc5ca1f77a04a197de00578c5d9c510ac37d315c8dbbbf9ccb73c12d54cfdc18` |

query-runtime 保持上表的 revision 与镜像；不能把 HEAD 或控制面候选版本当作查询进程版本。`make deploy-smoke` 的四入口通过。本轮没有重启 Docker、清缓存、删除卷、重置数据或重跑开户写验收。正常受控审批均已执行，没有容量拒绝；未完成的目录视觉验收不归因于旧审批容量事件。

部署前使用上轮已保存的 `overview-readonly-facts.sql`，事务为 `REPEATABLE READ READ ONLY`。精确目标 tenantId `2104457357567197185`、subjectId `2104457357567197186`，编码 `wp17sl-3dd282e46f49`：生命周期 `ACTIVE/1`，主体 `ACTIVE/0`，管理员／启用管理员均为 1，跨租户资格 0；配额／功能／安全版本均为 0，生命周期事件 1。全库生命周期 70、身份 189、平台账户业务审计 126。不读取密码摘要或其他秘密字段。

本轮只读回归使用上述既有对象，拒绝 `SETUP`；真实 Chrome、Playwright `1.62.1` 完成 13 组：平台登录、精确投影、大小写／空白、真实名称与状态、稳定分页与总数、字面字符、空／越界页、非法条件、页面 URL 向服务器传参、匿名、租户拒绝、退出失败关闭及实际请求／异常观测。正式认证登录／退出以外的 API 写请求实测为 0，页面异常 0。该临时回归不包含目录最终表格、搜索控件、创建取消、完整分页控件或原型视觉验收。

首轮 13 组回归在第 11 组因脚本误设拒绝状态为 403 失败，日志及脚本原件完整保留。最小真实租户会话观测确认实际为 401。ADR-0048 的独立 login type／Cookie 合同、`SaTokenPlatformContextArgumentResolver` 及既有角色／概览验收均要求没有平台会话时精确 401；这是验收预期错误，不是越权读取。更正为精确 401，增加租户身份仍有效、拒绝响应不含 `items`／`totalElements` 的断言后重跑全组，13 项通过；没有改成接受多个状态或仅判断非 200。

之后串行运行标准 `make acceptance-work-package WP=WP-0017 SLICE=login`，15 项通过，继续使用既有平台／租户凭据，无 `SETUP`。两项原失败复现中的旧 `businessWrites: 0` 是脚本常量，不作为请求观测证据；零业务写结论来自上述 13 组实际请求监听及数据库事实。

部署与全部浏览器执行后，重复相同 `REPEATABLE READ READ ONLY` 脚本，输出与部署前逐字相同，SHA-256 均为 `9a96acd6703ba0b64ba45105a111d4d00a9755fce5aceb5909ce96fd46ccf096`。该结论仅覆盖列出的业务状态、版本和计数，不把正常认证会话操作称为所有数据库零写入。

## 证据定位

下列文件均在本轮临时目录，SHA-256 固定内容；仓库只保存本记录与源码测试。

| 文件 | SHA-256 |
| --- | --- |
| `search-red.cjs` | `e649836b5e97aeaf8624b593b257ab43936f9025f88bd88c7950aac028c3e015` |
| `late-page-red.cjs` | `cd5a0a80b78dad55df83eaf5abf15597b9876405ac586a0a31d155286950cf72` |
| `backend-filter-red.log` | `07944749f641d1ad2d217873f984bf5fbf60e1836a359a8e1078c2e548ea7c29` |
| `backend-filter-green-final.log` | `0926c597736d6fcb458d56ef61b87d57f3c10a2ab2a5284b54ea7b6bf327cde2` |
| `route-red.log` | `49148160cd6378eb2e7d3bd62d7105f1a6f80b1900b74bfe0bb7cfc5310ca780` |
| `client-red.log` | `635f466349262e1dcf0a4a3a1bf2c7062c565b4a307be48d50db3977b27a74af` |
| `frontend-owner-validation.log` | `fea86eba4febebce37e1ca182bf0e5e1ab43dbe628b42742ae4c99a761c0c386` |
| `backend-validation.log` | `b7a98407884977d5664802dc29281c392b71f4de270669673bc0c08363db028b` |
| `directory-regression-v1.cjs` | `9c787e1c08bf9d474788090042a1ea89135d91d1157b9587872a7e61e88936d5` |
| `directory-regression.cjs` | `8c4fa71436e98fa3d8b33258c48adcbefb5ae2cb404ea38e751edce6eada38a0` |
| `search-red.log` | `8b75e5b2b09c7c20786f5d2b995a9c28578af1c12cd8d89f673e4c09b0e52e97` |
| `search-green.log` | `99864c69d7e3c9e61af1652b7d02a0d859735c375c9896903bf7c6452e7e488b` |
| `late-page-red.log` | `0cf559fba82670758fd2d59fb446e6e7faa8039b6e982bda4d88484a6d930034` |
| `late-page-green.log` | `1d830719be145270468c867b3bc8459e46d63dcbb2d8a29be7e8bebdedf92e46` |
| `directory-regression.log` | `5db0682db1c52da588ebaecfed952ea1208a742f372c8c097efa7700d3a17117` |
| `directory-regression-final.log` | `587e53c5fbc6480aa23fb2acf77401b999269547a439bb822a3bb9de8d2263f3` |
| `tenant-permission-probe.log` | `6deb19bce5cf76eb0b1d7620e50615b0d611f493da995ce8f921bb65e6e3d6dd` |
| `login-regression.log` | `bf59bee10cfb8a023d1f27cefddd9febdb688dc302453a42d8c55380e3e135b9` |
| `control-plane-build.log` | `b2c76fd005b9096f18982c078eec81ce80d74373fcc9f1921b6e7b82ce0e82a3` |
| `console-web-build.log` | `7885eadf2c4f406521bad7821695ee1139981318b97555dc941ec888bd7b55aa` |
| `candidate-deploy.log` | `312f2c4de8e08e331679d68e8af7f93d7a634a6cc57bbc58a349f1c0918a7844` |
| `deploy-smoke.log` | `99838758ae45eb3026f4f1c29ae70322c01ab3a72bdba7849c6b759cc299e776` |

临时文件不是持久验收门禁；目录完成结论仍须绑定受控验收入口、实际部署与固定 OpenDesign 证据。

## 工程助手与影响分析

运行环境为当前 Codex 的本地 `engineering-assistant`，能力来自已安装包 `2.2.0-rc.1+codex.local-20260929-051321`。显式加载 `wayfinder`、`tdd`、`code-review` 的 `SKILL.md`，以及 `wayfinder/references/hitl.md`、`tdd` 的 `security-seams.md`／`mocking.md` 和 `code-review` 的 `security-seams.md`。工作为内联切片，无 Runtime、`StageRunResult` 或质量节点结果，阶段结果校验不适用；不复用旧 Runtime 声称全绿。

代码图谱先探索 `TenantLifecycleQueryService`、`TenantLifecycleController`、`TenantLifecycleReadPort`、`TenantLifecycleReadMapper`、`PostgresTenantLifecycleReadAdapter`、`PlatformTenantLifecycleWorkspace`、`listTenantLifecycles` 与 `parseConsoleRoute`。影响分析分别返回服务 29、工作区 4、客户端 118、路由 4 个关联符号；客户端广泛关联主要源自同类共享符号，精确调用方与文件核对把修改限定到目录方法、工作区与路由，未扩大认证客户端或其他功能。图谱不能完整展示 mapper XML、中文测试及 JSX 关系时，补充精确文件范围读取；编译、真实数据库和测试提供最终正确性证据。固定 diff 已检查权限先后、字面转义、迟到结果、既有默认路由及受限字段。

## 下一动作

当前候选的查询／读取归属功能回归已通过。用户决定原型冲突承接方案后，继续实现目录表格、搜索、筛选、完整分页与只读导航，再分别完成登录、只读事实、真实浏览器和固定原型视觉验收。全部通过后才将 `platform-tenants:0–1` 标为 `verified`、提交收口并按既有授权推送；本轮不转入平台概览、三类入口切换或 WP-0018。
