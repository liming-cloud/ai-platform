# WP-0017：平台固定角色部署验证（待视觉质量收口）

| 字段 | 值 |
| --- | --- |
| 责任任务 | WP-0017 |
| 验证范围 | `platform-roles:0` 固定职责与账户导航；实际部署、身份隔离、登录回归、只读业务事实和原型对照 |
| 验证提交 | af1e0f45664c5ccd1818f8731e2782c10da8a9f8 |
| 实施基线 | 3b4e7c6ab07465c688f05dcfd7cd60cbdd508815 |
| 合同 | ADR-0088，合同提交 eb6b989de04f41ee08ffba47a2977236283ccb20 |
| 验证日期 | 2026-10-01 |
| 结论 | 功能与角色正文对照通过；390px、200% 文字的共享顶部导航存在原型与生产共同缺陷，视觉质量未收口 |

## 范围与实际版本

平台页只解释唯一固定平台管理员模板，直接显示四项职责和信息边界；唯一主要操作“管理账户”导航到 `/platform/accounts`。移除平台页的租户角色、详情抽屉和额外页签，租户角色分支保持原行为。固定职责随产品发布，没有新增角色 API、权限、数据库结构或业务对象。

| Compose 服务 | 实际 OCI revision | 运行镜像摘要 | 状态 |
| --- | --- | --- | --- |
| console-web | `af1e0f45664c5ccd1818f8731e2782c10da8a9f8` | `sha256:e9e2ba5cba1dcea31c7d3213d6009952e43dc10c034f20a3e1ef4aa8b23b5c78` | healthy |
| control-plane | `9cd1f8ff5fea06c7d0b33b6a5a641c587464d1d4` | `sha256:fc8583b6119ce2eb72fddc08f9fc9596bd2dd36f7310e1a348769d8cc562db86` | healthy |

只构建并替换前端；`make deploy-smoke` 四入口通过。后端与数据服务未重建，当前源码 HEAD 不能代表后端运行版本。未重跑开户、账户创建或其他会创建业务对象的验收。

## 已通过的验证

- `make frontend-test`：46 文件、232 项，类型检查、Lint、测试和构建通过；角色最小测试先出现两项失败，再修正至三项通过。
- `node --test scripts/quality/work-package-login.test.cjs scripts/quality/work-package-onboarding.test.cjs`：32/32。最初用 `umask 077` 将测试要求的 0640 反例裁成 0600，未满足反例构造条件；按测试正常的 `umask 022` 重验，安全断言和凭据校验没有修改。
- `make architecture-guard`：38 项及实际前后端门禁通过。后端源码未修改，不把既有 Maven 结果冒充本轮后端验证。
- `make project-status`、`make decisions-sync`、`make project-verify`、`make resume-context` 通过，治理单元测试 69 项；恢复输出为 11,495 字节。首次新增恢复说明超过 12 KiB，已将重复历史版本列表归回现有证据和矩阵，未放宽恢复预算或改写历史证据。
- 最终候选独立串行执行 `make acceptance-work-package WP=WP-0017 SLICE=roles`：**11/11**。覆盖唯一模板、完整职责、账户导航及刷新/前后退、三尺寸、全部可见文字翻倍、Tab/Enter、reduced-motion、租户/匿名拒绝、退出后不可访问；每个场景业务写入均为 **0**。
- 使用既有隔离平台管理员和租户管理员执行 `SLICE=login`：**15/15**。凭据只从当前用户拥有的 0600 文件读取，未打印、复制到仓库或写入证据。
- 浏览器前后各执行 `REPEATABLE READ READ ONLY`。12 个既有合成主体的身份、活动身份、管理员绑定、授权修订计数均为 12；主主体保持 `ACTIVE/version 3`。平台账户审计总数保持 126，活动 `PLATFORM_ADMINISTRATOR` 绑定保持 101。前后非秘密查询输出字节一致，SHA-256 均为 `6f69702f6eec704d29fb78f1781e07c104fc596aadc063795f3f6ed0359bbffc`；SQL SHA-256 为 `81233e3dd4e4d82ad860721c9734b56de4c7d8d43617d43ddeb3dafe536f6643`。不读取密码、摘要、Cookie 或 Token。

## 原型对照与质量阻断

唯一设计来源为 [OpenDesign 项目](http://127.0.0.1:49175/projects/249f81f2-6f1a-46ff-988c-da3cdedb35e7/conversations/88968f9d-740d-4032-a1a7-6b3248d9d4d9/files/index.html)，项目 ID `249f81f2-6f1a-46ff-988c-da3cdedb35e7`，已接受提交 `21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`，入口 `platform-roles`。通过 OpenDesign 读取文件并使用其原始预览；没有修改原型或建立副本。

当前 `full-platform.js`、`page-fixtures.json`、`prototype.css` 与该固定提交逐字节相同，SHA-256 分别为 `d650c1ae6c000263fd97be0d650f2c375ece3de8fb00998cb758955060a5adbe`、`4ecbee7d7bb6cb0f174a24e4fec00f2a644c194ceb024342526341d5b6b0d819`、`2e627c22e4a9ce1d37ce90ce95a92673b1bc13d3ddbcbf4f51f5eb33d215efa4`。

同一 Chrome 对原型和生产执行七组只读对照：390/1024/1440px 正常文字、上述三尺寸 200% 文字、390px reduced-motion。全部可见文本按直属元素统一翻倍一次，避免不同 HTML 标签导致漏放大。角色标题、对象行、主要操作、职责行、关注说明等关键区域的最大坐标/尺寸差为 **0.015625px**，字号、字体、字重、文字颜色和背景一致，文档级横向溢出为 0。几何容差仍为 1px，没有放宽断言。指标 JSON SHA-256 为 `af171eece50195aa4e6483b9f14e9543df62f1025dc70274a0f3548e74f1f125`。

**人工查看截图发现：390px、200% 文字时，原型和生产的共享导航均超出固定栏高，并遮挡部分顶部范围说明；账户菜单还挤压该说明使其逐字折行。角色正文一致不等于整页可用性通过。** 自动角色脚本覆盖正文裁切和主要操作命中，未覆盖此共享导航交叠，因此其 11 项通过不能解除这个人工发现。未自行接受响应式例外，未修改 OpenDesign 迁就生产；已向用户提出先修正并接受 OpenDesign 新版本、或保留质量阻断的决定，尚未获得本项结论。

已补最小只读失败复现：390px 正常文字的导航与范围说明交叠数，原型/生产均为 0；全部可见文字放大 200% 后，两者均为 1，零交叠断言明确拒绝。该失败保留，指标 SHA-256 为 `449896ebd6a8efdfb8be47c5b2a1b28bdf53c5c4052c43665fbec3ad8ed773c5`，不把源码测试或正文指标通过替代此整页质量反例。

所有观测脚本、日志和截图仅在本轮 0700 的 `/private/tmp/wp0017-roles-final.5eDM5tSY`，没有截图进入仓库。原型未实现的 M8 导航仍按 ADR-0083 延期，本次没有扩大延期范围。

## 失败记录与修正边界

旧部署 `401df22d19965024b63bbda1e2d4fc852c21ad3a` 缺少 24px 内容间距、宋体职责标题和移动端全宽主要操作，补充严格浏览器断言后被拒绝；修正仅作用于角色页及其紧邻页头。

首轮角色验收在退出后立即读会话，遇到浏览器跳转期间的异常。三次定向复现的退出接口均为 204，等待登录页后匿名检查均通过；按既有登录回归的等待方式修正，没有改变退出实现或匿名 401 断言。最终候选曾与视觉观测同时运行时停在账户导航等待，安全日志未能还原具体原因，该次不计为通过；保持源码和断言不变，独立串行完成上述 11 项角色和 15 项登录回归。失败日志与旧候选观测全部保留。

## 工程方法与下一动作

当前运行环境为 Codex，显式读取本地工程助手 `wayfinder`、`tdd`、`code-review` 技能；内联实施和固定 diff 评审，没有创建 Runtime 或 `StageRunResult`，正式阶段结果校验不适用。代码图谱探索 `PlatformRole`、`PlatformPermissions`、`visibleNavigation`、`RoleDirectoryWorkspace`、`ConsoleApp`、`PageHeading` 与退出助手，执行 `RoleDirectoryWorkspace` 影响分析：仅平台分支和 SPA 装配需要修改；现有会话能力继续授权，没有无调用方接口或后端结构变化。

当前 `platform-roles:0` 尚未标记 `verified`。下一动作是获得上述共享 200% 文字问题的产品决定，完成对应原型和生产质量复验后再收口；之后才进入 `platform-tenant-detail:0` 的独立合同。平台概览、租户列表/生命周期、租户入口选取等仍按矩阵待实施。本证据不完成 WP-0017 整包、66 页生产实现或 M6。
