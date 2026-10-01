# WP-0017：租户概览与五页设置导航收口

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0017 |
| 验证范围 | `platform-tenant-detail:0` 真实基本信息、安全保障、五页设置导航与原型一致性 |
| 验证提交 | a82202465fdf62bc1b8d157fa5902c4e93db78c6 |
| 实施基线 | fab1b3d9c672fee52b4afa3ddf6bbf5ed446548b |
| 合同 | ADR-0089；既有读取与租户登录入口继续遵守 ADR-0080 |
| 原型共享布局提交 | 47c4f6f31823617633ce0bac98074208781f5565 |
| 验证日期 | 2026-10-01 |
| 结论 | 租户概览独立切片闭合；WP-0017 整包与 M6 未完成 |

## 交付与真实运行对象

概览从既有生命周期和限量管理员读取恢复真实名称、编码、状态与管理员摘要，显示不可关闭的三项安全底线。概览、管理员、资源配额、可用功能、登录安全保持独立路由和同一目标，支持刷新、前后退和返回目录。修正安全保障标题语义及字体、租户编码字体、状态徽标圆角、设置导航正常／悬停色及登录入口悬停反馈；保存仍由各独立页负责，没有新增 API、权限或数据库结构。

| Compose 服务 | 实际 OCI revision | 运行镜像摘要 | 状态 |
| --- | --- | --- | --- |
| console-web | `a82202465fdf62bc1b8d157fa5902c4e93db78c6` | `sha256:2fc2019d7e096af9017f779c809f2e1726ed678821b70d27c45646f7ffeb0d49` | healthy |
| control-plane | `9cd1f8ff5fea06c7d0b33b6a5a641c587464d1d4` | `sha256:fc8583b6119ce2eb72fddc08f9fc9596bd2dd36f7310e1a348769d8cc562db86` | healthy |
| query-runtime | `ef11d6aad0ffb8d8a656d5a17a3477d93b95a5c8` | `sha256:4a847f9631eee38204096f9ea66c159dcfcfbb25f17406ffcecab473795d5c5d` | healthy |

按固定提交构建并仅替换 console-web，`make deploy-smoke` 四入口通过。最终浏览器结论绑定上述实际候选；`dee3b95e9cbe9d44e24ac587878a01aec7bbdf1c`、`48b73049f7001db861cde98dad327d892a8d1e80` 的中间结果不替代最终验收。后端运行提交与仓库 HEAD 分开记录。

## 原型来源与一致性

唯一来源为原 [OpenDesign 项目](http://127.0.0.1:49175/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)，项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`、入口 `platform-tenant-detail`。业务页面与原验收仍固定于 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`；共享移动布局使用 ADR-0088 已接受的 `47c4f6f31823617633ce0bac98074208781f5565`。本轮没有修改原型或创建副本。

MCP 读取两次返回 `Transport closed`；原 OpenDesign HTTP 服务正常，项目元数据确认原 `resolvedDir`，浏览器直接访问同一原始入口。核对其 HTTP 实际内容、工作树与固定 Git 对象，三者相同：CSS SHA-256 为 `645f7b50c68c1defd668eda26175db87c8798ea61a7fdf677a5a3a4c25bb2a87`，`full-platform.js` 为 `d650c1ae6c000263fd97be0d650f2c375ece3de8fb00998cb758955060a5adbe`，`page-fixtures.json` 为 `4ecbee7d7bb6cb0f174a24e4fec00f2a644c194ceb024342526341d5b6b0d819`。连接故障没有被描述为产品验收失败；本轮无因审批容量未执行的验收项。

七组对照为 390/1024/1440px 正常文字、同三尺寸 200% 文字、390px reduced-motion。29 个关键区域覆盖共享外壳、标题、返回操作、全部五项导航、基本信息、徽标、登录入口和安全保障。字体、字号、字重、行高、字距、颜色、背景、圆角及相关边距一致；固定宽度、同文案控件高度与相对间距最大差 **0.03125px**，保持 1px 容差。文档横向溢出与导航／范围说明交叠均为 **0**；指标 JSON SHA-256 为 `8767dfbb4df62bc33c9142e4a429008c010b83f5bab13695d38fe9bdc7170ef1`。

对照没有修改原型 DOM。按 ADR-0080 排除无生产依据的演示驻留区域，真实名称、编码、管理员与状态由正式接口提供；业务文字换行及行数造成的自然高度不被伪称为像素相同。分别核对固定字体、列宽、内距、导航与分区相对间距和可操作性，不新增视觉例外。未交付 M8 导航沿用 ADR-0083 的范围。人工查看最终三尺寸正常／扩字六张生产截图及原型 390px 扩字截图，确认导航颜色、标题层级、换行、完整正文和入口没有裁切或遮挡。

截图、观测脚本和详细日志仅在 0700 的 `/private/tmp/wp0017-overview.io5J4tSF`。对照脚本 SHA-256 为 `5d182c2ecc64d3d2fcd6f32d405366cca4af9fb83253439d0c514dde290aebd8`。仓库没有截图；既有 0600 凭据未覆盖，密码、Cookie、Token 和摘要没有进入证据或日志。

## 功能与回归

`FOUNDATION_ACCEPTANCE_TENANT_ID=2104457357567197185 make acceptance-work-package WP=WP-0017 SLICE=overview` 在最终镜像通过 **15/15**。覆盖真实事实、标题与正常／悬停色、五页目标与当前项、刷新／历史、返回目录、真实 404 与重试、真实网络离线恢复、真实延迟加载、三尺寸、200% 文字、键盘、reduced-motion、租户／匿名拒绝和退出失效。使用既有隔离对象，拒绝 `SETUP`；没有 API 响应替身，每个场景业务写请求 **0**。四个独立设置页只回归共享导航，不冒充各自写操作重新验收。

最终镜像的 `SLICE=login` **15/15** 通过。另执行三尺寸 200% 文字的原生 Tab／Enter／返回，管理员入口均获得焦点并命中自身，控件高 `53.984375px`，业务写请求 **0**。键盘脚本及日志 SHA-256 分别为 `e50a74f0f6ca6d170c90ca64a74ae4d5bd1160e4a2ed94be1bf529bbd9866433`、`3933d24609720a12196cfcef57dec560b1b3229ad4305b9fea9fcdb782a72de5`。

最终概览与登录日志为临时目录中的 `overview-browser-closed.log`、`login-browser-closed.log`，SHA-256 分别为 `a9d175dfcfdcc06727d351c36f34bf5fb976fc7e5a7d49e340e38883f7e9825a`、`1992e26a268509ba4f5e12bccf8e5240955d4e333bc46b8516adf8c8db15d338`；原型指标位于 `parity-closed/final-metrics.json`，前述摘要均绑定本轮最终候选。

源码 `make frontend-test`：46 文件、**232 项**，类型、Lint、测试与构建通过；验收助手 Node 测试 **32/32**，切片入口单测 **12/12** 和语法检查通过。最终 `make architecture-guard` **38 项**及实际守卫通过，跨后端依赖边界违规为 0；既存手工分页债务仍为 3，未借本片扩大整改。没有后端源码修改，本轮未执行 Maven 或重新运行会创建业务对象的开户、账户、设置写验收。

## 只读数据库事实

浏览器前后执行 `REPEATABLE READ READ ONLY` 的 13 条精确查询。租户 `2104457357567197185` 保持 `ACTIVE/version 1`，其初始主体 `2104457357567197186` 保持 `ACTIVE/version 0`；生命周期与锚点各 1，管理员及启用管理员各 1，跨租户管理员资格 0。配额、功能与安全版本均为 0，生命周期事件 1；全局生命周期 70、身份 189、平台账户审计 126。

前后输出字节相同，SHA-256 为 `9a96acd6703ba0b64ba45105a111d4d00a9755fce5aceb5909ce96fd46ccf096`，SQL 为 `4e732f8501e2dff470fac6ea06ef71c06fb1249e03f1c430761b001f7c49a4f7`。只读非秘密状态、版本与计数，不读取凭据或摘要；认证流程自身可能更新会话与登录状态，因此此结论限于所核对的业务事实。

## 红绿与评审

旧实际部署 `ad18b680c0fc8e5b61f5e028bf5087cb98ed57f7` 的安全保障标题断言失败；切片入口两项反例先失败再通过。随后严格对照捕获徽标圆角声明差异并局部对齐。人工看图发现机器只核对当前导航项，漏掉未选中项的颜色；新增真实断言在 `48b73049f7001db861cde98dad327d892a8d1e80` 失败，悬停最小复现也失败。扩大至全部五项导航，最终相同断言通过，原失败日志保留，没有放宽字号、裁切或几何容差。

Codex 本地已安装工程助手技能显式加载 `wayfinder`、`tdd`、`code-review`，依据本 ADR、Charter、ROADMAP、STATE 和 WP-0017 内联执行。固定评审范围为实施基线至验证提交；没有 Runtime、`StageRunResult` 或 `QualitySnapshot`，阶段结果校验不适用，不复用旧 Runtime 宣称完成。

代码图谱探索并分析 `TenantOverview`、`readTenantOverview`、`TenantSettingsNavigation` 的影响。同名租户运营总览未修改；图谱未列全 JSX 消费方，随后定向确认五个已知页面共用导航，因此增加逐页只读回归。异步目标归属、迟到结果与失效会话沿用已有测试，没有新增重复读取、模拟保存或后端抽象。

## 控制面与下一前沿

Charter、ROADMAP、STATE 及工作包边界已复核；矩阵仅关闭 `platform-tenant-detail:0`，页面两项操作至此均有各自提交与证据。历史登录入口 `:1` 的原提交与验收不改写。控制面执行 `make decisions-sync`、`make project-status`、`make project-verify` 与 `make resume-context` 后收口；WP-0017 的整包提交与证据仍为 `pending`，STATE 指针未改变，五项已提交 fixture 内容未修改。

收口治理单测 **69/69**，29 个任务的项目校验通过，650 项历史定位保持不变并由治理校验复核；恢复上下文为 **11,558 字节**，满足 12 KiB 预算。固定差异评审与 `git diff --check` 通过，源码与证据提交后再按既有授权推送及核对对应 GitHub CI。

当前 WP-0017 的 12 个入口中，9 个入口的本包操作已验证，租户登录入口部分已验证，平台概览和租户目录仍待实施；21 项生产操作中 17 项已验证。下一独立切片为 `platform-tenants:0–1` 的租户目录／建立入口／搜索合同，随后仍需平台概览首个阻断项和三类登录入口切换。普通成员与授权、模型、知识、评测、检索、助手、问答及查询治理归 WP-0018～0026；本片不完成 66 页生产承接、WP-0017 整包或 M6。
