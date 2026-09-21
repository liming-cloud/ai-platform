# WP-0016：原型 UI 人工验收通过

## 人工结论与验收版本

- 2026-09-13，产品负责人在视觉精修交付后明确回复：“UI工作验收完毕”。本记录将其登记为 WP-0016 原型 UI 人工验收通过，不再保留“等待最终视觉确认”的当前阻塞。
- 验收实现提交：`21b45c347a4008f5959b1b972e3ffd8a5aadcc6f`。精修交付证据提交：`0951272501d9ef83accff8858cd72e06f06fb23f`。两者均在当前分支历史中，后者为本轮登记前 HEAD。
- 技术证据：`project/evidence/WP-0016-visual-polish-20260913.md` 及其机器报告。原型、代码和设计仍由原 OpenDesign 项目统一保存。本轮只同步验收元数据和控制面，不改变已验收的 HTML、CSS、JavaScript、合成业务规则或用户浏览器状态。
- 已接受的布局、任务流程、Claude 参考风格、7/5/7 和 36/32/44px 控件规范成为后续逐工作包研发的原型对照基线；不以新需求为由无边界重做整个平台。

## 通过范围与保留边界

页面清单共 78 个入口：66 个 M6 页面属于原型交互范围，10 个 M7/M8 页面仅涉及视觉与信息架构，另 2 个暂缓页面及邮件邀请对应的三项操作仍暂缓。渠道绑定、设备凭据、邮件邀请没有因本次确认恢复实施。

本次是整体 UI 确认，不代表用户提供了逐项操作日志；不据此编造实际 200% 浏览器缩放、系统原生选择弹层或独立新用户实验的逐项执行证据。既有技术证据对等效重排、文字放大及人工目视范围的限制继续保留。

本次不表示真实解析、服务端授权、真实模型流式、数据库原子事务、准确性、生产性能、部署或 M6 全产品验收通过。WP-0014 与 M6 保持进行中，M7/M8 不激活。历史 ADR、阶段迁移表与机器报告中的当时“待人工/拒绝”状态保留，当前 UI 人工结论以本后继记录为准；不改写历史机器通过范围。

## 验证与保存

原型运行代码未变，已验收实现的全量结果仍绑定 `21b45c347`：169 项 Node、179 项浏览器、1638 页面状态/尺寸场景、7989 控件检查、静态与可达性检查及 OpenDesign lint 均通过。完整命令、环境和范围见视觉精修证据；这些结果不是本轮重新执行的浏览器或 lint 结果。

本轮因只变更验收记录，实际复验：

```bash
node --test docs/prototypes/wp-0016/scripts/*.test.mjs
OPENDESIGN_SAVE_REPORT=/private/tmp/wp0016-human-acceptance.BpDapC/opendesign-save.json node docs/prototypes/wp-0016/scripts/save-opendesign.cjs
make project-status
make project-verify
make resume-context
git diff --check
```

- Node 169/169 通过；14 项治理测试及控制面验证通过。
- 会话中的 OpenDesign 工具连接返回 `Transport closed`，但本机原预览服务可达。使用已有锁定绝对路径的保存脚本重新建立本机 MCP 连接，核对项目目录后保存 113 文件并逐文件比对 raw 预览 SHA-256，结果 PASS。未新建项目、未启动设计生成或生产服务。
- 原始 TAP 与保存报告仅在 `/private/tmp/wp0016-human-acceptance.BpDapC/`；不生成或提交截图。OpenDesign 项目目录保持 `docs/prototypes/wp-0016`，本轮更新的设计资产为 `ACCEPTANCE.md`、`DESIGN.md`、`ACTION-LEDGER.json`。
- 用户已有五项 fixture 暂存重命名保持不变，不纳入验收提交。

## 交接

先提交本人工确认和原型验收资产，再由后续控制面提交将 WP-0016 标记完成、唯一前沿交接到 WP-0017。WP-0017 的“进行中”只表示接收前沿并准备契约，不表示生产实现已开始。

下一步先核对 knowledge-data 现有链路与触及债务，冻结配额原子预留、来源版本、受限解析状态机、失败与重试、单文件和全库重建的专属 ADR，建立正常及恶意输入的失败测试，再逐闭环对照原型实现。具体 API、表和事件未冻结前不得直接编码。后续认证实现包的登录策略上下限和已登录会话生效时机仍为未决项。

## 工程助手与独立核对

运行环境：Codex；能力来源：已安装 engineering-assistant 插件。实际路由到 `engineering-assistant:wayfinder`，inline/proceed；显式加载 engineering-assistant、wayfinder 及人工门禁、授权参考。技能要求本轮记录用户明确决定、关闭已解决前沿并保留范围外事项，不把人工 UI 确认扩大为生产认可。

控制面使用 Charter、STATE、WP-0016 全部 12 个活动 ADR、WP-0014/0017、设计资产与既有精修证据。独立子 Agent 只读核对当前状态和验收边界，指出总控包旧前沿仍指向 WP-0015，本轮随交接修正。

代码图谱聚焦 `project_governance.py` 的 WorkPackage、parse_work_package、build_resume_context 与 verify，确认前沿必须是进行中或受阻包；因此交接同时更新 STATE 和 WP-0017 状态，不修改治理脚本。没有非局部代码修改，未执行实现影响分析。

未产生持久 flow、StageRunResult 或等价阶段执行 JSON，阶段结果校验不适用。
