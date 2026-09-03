# WP-0016 业务闭环人工复核拒绝

评审对象：`74970bb53`，本轮开始 HEAD：`a6e5acad4`。用户提出六项意见：文字代替图标；平台健康业务不清楚；恢复业务不清楚；租户安全/配额/账号/角色配置未定义；菜单不统一和“新修订”用语不当；邀请成员及全平台操作不能闭环。

结论继续为不通过。当前唯一工作包仍是 WP-0016，不进入生产前端。整改及待决策边界见 `docs/prototypes/wp-0016/BUSINESS-CLOSURE.md`。

已核实事实：

- `navButton` 使用 `label.slice(0, 1)` 生成文字图标；`adminShell` 与 `consoleNavigation` 维护不同导航，且“评测结果”指向助手发布。
- `bindInteractions` 对通用确认弹层只有打开处理，没有确认后的领域状态变更；成员 fixture 没有账户选择与添加结果。
- 创建租户的安全模板与配额档位只有名称，没有具体规则；当前按钮从第 2 步直接跳到详情。
- 平台健康打开固定处理队列；恢复演练跳到发布页面，均不能作为运行处置闭环证据。
- ADR-0067/0073 禁止自定义角色；ADR-0042/0061 不包含邮件邀请。新增此类能力需明确选择，不得静默扩张。

图谱探索 `navButton`、`consoleNavigation`、`adminShell`、`pageDialog`、`bindInteractions`。影响分析：`adminShell` 7 个符号，`bindInteractions` 4 个符号。本轮据此要求导航只有一个来源，状态变化有独立验证，拒绝以更多静态断言代替用户任务完成。

执行环境为 Codex，已加载 engineering-assistant 的路由、decision-interview、prototype（UI reference）、tdd（security-seams reference）；Charter、STATE、WP、六份引用 ADR 和账号/管理员补充 ADR 已读取。没有 Runtime `StageRunResult`，阶段校验不适用。

后续确认：产品负责人明确平台配置租户可用功能，租户管理员配置普通成员的对话功能，并要求“功能能用 → 功能好用”。已接受 ADR-0075 并绑定 `b1db144b4`，不引入自定义角色/API 勾选树。新账户邀请、安全可配置字段和配额调整规则仍未确认；这些待决策项不得阻止独立的导航和已有成员流程整改。
