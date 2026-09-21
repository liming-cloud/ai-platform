# WP-0016 业务闭环整改候选

## 状态与范围

唯一工作包为 WP-0016。基线 `a6e5acad4`；本轮产品决定绑定 `b1db144b4`。原型人工验收仍不通过，不进入生产实现，不推进 WP-0017。

候选原型及红绿测试绑定提交 `699becdeb`。本轮 OpenDesign 运行 `9dcf38e2-3067-4227-9f33-59481267eb85` 已成功结束，使用原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7` 的本地目录；随后主代理补充对象级历史过滤并复核。入口仍为 `docs/prototypes/wp-0016/index.html`，无需新建项目。

用户确认：平台管理员控制租户可用功能，租户管理员控制普通成员的对话功能；先验证功能能用，再验收功能好用。已接受 ADR-0075，保持三种固定角色和管理员治理边界。

## 两层验收

| 场景 | 功能能用 | 功能好用 |
| --- | --- | --- |
| 导航 | 专属页和通用页同源，详情指向正确父目录 | 真实 SVG、文字完整、当前项明显、三档宽度和键盘可用 |
| 创建租户 | 名称与编码校验、合资格初始管理员、保存后目录与详情一致、刷新恢复 | 必填项明确，安全底线与具体配额可理解，不能跳过必填步骤 |
| 成员管理 | 搜索已有账户、添加结果、重复拒绝、普通成员停用与恢复，不能变更管理员 | 添加成员而非虚假邀请，结果就地可见，取消无副作用 |
| 功能控制 | 平台租户上限和普通成员权限均能保存/取消/恢复；有效权限取交集 | 上级限制与成员选择有区别，普通用户不接触管理概念 |
| 平台运行状态 | 选择不同异常、查看对应影响、重新检查及结果 | 描述受影响业务与下一步，不混淆 RAG 质量评测 |
| 备份与恢复 | 选择备份、检查、隔离演练、进度和报告/重试 | 明确是备份恢复验证，不能误认作生产覆盖操作 |

全部 68 页仍须逐操作登记覆盖，不把导航或通用弹框记为状态变更；尚未实现的行为必须保留在操作账本。

## 验证状态

OpenDesign 在原项目内完成生成，主代理独立复核后补齐对象级历史过滤。浏览器此前被拒绝，本轮已再次询问但尚未获得新授权；没有运行浏览器、截图或替代渲染。

- 初始红灯：`docs/prototypes/wp-0016/artifacts/business-closure-red.tap`，9 项失败。
- 独立红灯：`reviewer-regression-red.tap` 为 1 通过 / 5 失败；`reviewer-directory-red.tap` 为 2 项失败；`reviewer-object-scope-red.tap` 为 1 项失败。
- 绿灯：`node --test docs/prototypes/wp-0016/scripts/remediation-contract.test.mjs docs/prototypes/wp-0016/scripts/business-closure.test.mjs docs/prototypes/wp-0016/scripts/reviewer-regression.test.mjs`，22 项通过，报告为 `artifacts/business-closure-green.tap`。
- `node docs/prototypes/wp-0016/scripts/verify-static.mjs`：4,035 条断言、427 个共享渲染场景、1,428 个真实本地 HTTP 入口请求通过。它们不证明浏览器布局或事件链成立。
- OpenDesign 静态 lint：P0/P1/P2 均为 0；执行命令沿用已锁定应用内的绝对路径 `daemon-cli.mjs lint <index.html> --fail-on p1 --json`。
- 8 份 JS 语法、5 份 JSON 解析及 `git diff --check` 均通过；`make project-status project-verify resume-context` 通过，14 项治理测试通过，当前前沿仍为 WP-0016，恢复输出未超 12 KiB。

## 已修正与仍未完成

本轮修正真实 SVG、共享导航、文档版本语义与内容数量、租户创建/管理员选择/刷新回显、普通成员添加/停用/恢复/取消、两级功能配置、平台异常记录和隔离恢复演练。独立复核额外关闭开关混用、取消误提交、虚构助手、研发说明泄入正文、目录搜索无绑定、旧对象页头，以及历史按成员/助手/会话归属过滤的缺陷。

账本现登记 68 页的 93 个操作或待设计任务：16 个状态模拟、3 个读取、46 个导航、2 个局部演示、3 个待决策、22 个未实现和 1 个明确禁止项。数量仅指已登记项，尚未穷举全平台控件。导航不等于业务闭环；问答仍未实现轮次持久化、幂等和断线恢复，账号创建/密码重置及知识、模型、助手、评测等仍有未闭环路径。整体技术验收与人工验收均不通过，不能用局部绿灯取代整体结果。

## 六维评审

- 正确性：本轮已定位缺陷有红绿回归；其余主任务仍有缺口，整体不能通过。
- 复杂度：采用一个导航来源和一个合成状态源；不新增生产框架或通用权限树。
- 冗余：已移除旧专属导航；专属/共享渲染测试不覆盖全部真实 DOM，仍需浏览器复核。
- 性能：本轮只对小型 fixture 执行 Node 和 HTTP 检查，未评估真实浏览器性能。
- 架构：仅 WP-0016 可丢弃原型与控制面变更，没有修改生产前后端或数据库；本地存储不是安全边界。
- 测试：22 项 Node 回归和静态门禁通过；真实交互、焦点、390/1024/1440、reduced-motion 和截图未评估，质量门禁不宣称全绿。

## 工程依据

- 环境：Codex；能力来自已安装 engineering-assistant 技能和本地 OpenDesign 项目。设计由 Local Codex 在原项目内执行。
- 最小路由：`engineering-assistant:decision-interview` → `engineering-assistant:prototype` / `engineering-assistant:tdd` → `engineering-assistant:code-review`。主代理已完整加载上述技能及路由授权/wayfinding、UI、TDD/评审 security-seams 参考。
- 控制面：Charter、STATE、WP-0016、工作包全部引用 ADR，以及账号和管理员补充 ADR-0042/0047/0061/0073；ADR-0075 记录本轮确认。
- 图谱：探索 `navButton`、`consoleNavigation`、`adminShell`、`pageDialog`、`bindInteractions`；影响分析 `adminShell` 7 个符号、`bindInteractions` 4 个符号。结论促成单一导航来源及独立模拟状态验证，不扩大到生产权限实现。
- 补充图谱：`submitQuestion`、`toggleCitation` 调用路径，`renderConversation` 影响 4 个符号。嵌套新状态函数未被图谱完整返回，按已定位文件精确读取；据此将历史、引用、发送入口统一到对象授权投影。
- 本轮未产生 Runtime `StageRunResult`，阶段结果校验不适用。

## 未确认项

可配置安全策略的字段和上下限、配额调整的权限与生效规则，以及是否新增邮件邀请/自助激活尚未确认。固定默认配额、已有账户添加和不可关闭安全边界可先独立验证。
