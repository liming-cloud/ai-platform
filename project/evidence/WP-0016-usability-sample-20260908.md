# WP-0016：整体易用性与 Claude 样板

## 当前结论与边界

阶段一、二实施中，尚未取得样板方向人工确认。全量送验已撤回；不进入其余页面全量迁移或 WP-0017，不修改生产代码。

- 调查基线：`6d4249a73585744453b528b55b742fbca0ab542a`，起点最后验证提交 `0ff24482316c58288f8dd6f8a7e19872fd097c87` 为其祖先。
- 唯一结果：全量旧页面去向地图、公共组件和“准备知识 → 发布助手 → 开放使用”连续样板，保存到原 OpenDesign 项目。
- 保留用户已暂存的五项 fixture 改名，不混入本任务提交。不自动恢复、重置或迁移用户演示账户。
- 原型是可丢弃合成实现；浏览器检查使用独立 Chromium，不读取用户 Chrome 配置。截图只存系统临时目录。

## 最小复现与结构原因

2026-09-08 在原 OpenDesign raw 预览、独立 Chromium 1440×900、租户管理员合成账号登录后得到四项失败：

| 探针 | 基线结果 | 验证方式 |
| --- | --- | --- |
| 成员目录不混入授权表单 | 失败 | tenant-members 的 main 中存在 data-member-feature 控件 |
| 内容导航不并列文档目录 | 失败 | admin-sidebar 中存在 knowledge-sources 一级导航 |
| 文档处理阶段使用中文 | 失败 | source-detail 的可见正文包含 RECEIVED 等内部枚举 |
| 文档详情不承载全库重建 | 失败 | source-detail 的 main 中存在 index-rebuild 导航 |

独立探针执行退出码为 1，不能记为通过。步骤为通过 browser-session.cjs 的 loginConsole 登录，打开上述 screen，等待目标 main，再检查可见正文及目标控件；不注入权限、不调用生产服务。临时探针在 `/private/tmp/wp0016-usability-review.JNxt5N/baseline.cjs`。

图谱探索 renderSourceDetail、renderAssistantRelease、adminShell、renderConsoleNavigation、renderMemberFeatureEditor 和 workspaceJourney。renderConsoleNavigation 的影响分析为 50 个原型符号，要求共享外壳回归。已确认发布弹窗采用 div.dialog-body，但既有内边距只定义于 dialog 内 form；应统一弹窗结构，不能继续逐页补丁。代码图谱未覆盖浏览器准备辅助文件，按已知精确路径读取 browser-session.cjs，未扩大到生产代码。

## 设计依据与执行

- 主体设计使用本机 OpenDesign 0.21.1 内置 Claude (Anthropic) 包，已读取 USAGE.md、DESIGN.md、tokens.css 和组件清单。它是整理后的参考设计，不声称为 Anthropic 官方完整企业组件规范。
- 维持用户确认的 7/5/7；按 ADR-0005 扩展中文字体、企业密度、对比度和 150/200/240ms 动效，不替换生产 Fluent UI React v9 基座。
- 通过原 OpenDesign 项目委托 Local Codex 执行，组合界面精修和动效配方；没有建立平行项目。设计体系在任务中显式指定包路径，不能以全局默认替代项目内容验证。
- 设计生成范围仅 docs/prototypes/wp-0016；主任务维护 ADR、工作包与证据，避免并发覆盖。

## 验证状态

控制面阶段检查：make decisions-sync、make project-status、make project-verify 通过，14 个治理测试通过，当前前沿仍 WP-0016。样板实现、浏览器矩阵、控件枚举、OpenDesign 保存和最终提交尚待完成，不沿用历史自动通过计数。

## 工程助手使用

当前运行环境为 Codex，能力来自已安装的 engineering-assistant 插件。显式加载 engineering-assistant:engineering-assistant、engineering-assistant:prototype 及其 UI、授权与前沿管理参考；运行时路由为 prototype、inline，基于用户已明确批准的 OpenDesign 生成并保存操作返回 proceed。使用 Charter、STATE、WP-0016 的 12 个活动 ADR 和原型设计。未创建持久 flow 或 StageRunResult，阶段结果校验不适用。

营销页设计技能经范围检查明确不适用于数据表和多步骤产品界面，因此不采用其营销页布局规则。人工拒绝持续有效，后续样板方向确认不能自动代替全量最终验收。
