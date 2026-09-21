# GOV-0002 GitHub 迁移验证证据

| 字段 | 值 |
| --- | --- |
| 责任任务 | GOV-0002 |
| 验证提交 | 90e4c5d4450c001fe46a31fcf127a8115500f2b2 |
| 验证范围 | fixture 路径、GitHub main CI、完整历史克隆、单分支与唯一工作树收口 |
| 日期 | 2026-09-21 |
| 输入提交 | 346214b1d3cec5d6394e8c344d102dc9fdb3ab0d |
| 接受依据 | 用户执行《GitHub 迁移与本地单分支收口计划》，明确纳入五项重命名并由 Agent 推送 |

## 本地验证

- 先将 fixture 单测数据根移至 project/fixtures：9项运行，4项错误、4项失败，实际失败因为生产工具仍读取旧根；修复两个 Python 工具和两个 Shell 消费者后，9项通过，真实清单2个场景、8个检索用例通过。
- 五个数据文件与输入提交的原文件逐字节一致；JSON格式、摘要、安全与完整性断言未改变，不保留旧路径兼容副本。
- project-verify：68项测试通过，28个任务及650项历史对象校验通过；architecture-guard：28项通过，既有Java内联SQL15、手工分页3，未将旧债务描述为清零。
- project-status、resume-context、两个受影响 Shell 的 bash -n、git diff --check 通过。恢复上下文3873字节，低于12KiB。
- 源码比较证明 backend、frontend、deploy 无变更；未运行真实业务开户、评测、部署或数据操作，没有新截图。

## 历史与现行文档

Git保留完整可达历史；650项来源路径、来源提交、摘要、目标与引用保持一致。更新现行主分支时，5份待办WP-0021～0025原处置为move，原文不可变校验真实拒绝了修改；按现行修正将这5项处置改为update并登记GOV-0002原因。历史原文仍从原提交及摘要核验，校验器与完成门禁均未修改。旧证据、已完成任务和历史验收时间没有改写。

## 固定差异评审

比较输入提交至验证提交。正确性由真实红绿和消费者检查证明；复杂度仅为路径和CI条件调整；无重复数据根、兼容分支或新框架；业务性能不受影响；业务分层、API及数据结构不变；回归保留摘要、合成数据与完整性反例。远端及 CI 的独立核验见下文。

代码图谱定位 demo_fixtures.verify，evaluate影响查询返回评测入口及部分同名误匹配；使用精确路径引用和离线回归补齐实际消费范围，不据此修改登录或开户代码。进一步检查治理历史校验后通过现有处置语义解决文档维护问题，没有扩大工具重构。

工程助手环境为当前 Codex 本地插件，显式加载 wayfinder、tdd、code-review 及 evidence-and-stage-audit；本轮没有活动Runtime节点，没有StageRunResult/QualitySnapshot，阶段结果校验不适用。

## 远端与最终清理

用户已完成GitHub CLI授权。目标私有仓库经git ls-remote确认没有任何引用后，仅将53ec62a02195c6039804fa224b2ac2dfc46475c8推送到main，没有标签或其他分支，默认分支为main。首次尝试被本地Gitee专用凭据助手阻断，显式使用官方gh凭据助手后成功，未输出凭据。

从 GitHub 新建 main 的 single-branch、no-tags 完整历史克隆，非浅仓库；9 项 fixture、68 项治理、28 项架构，以及 status/resume、两份 Shell 语法检查全部通过，Git 状态为空，650 项历史证据可独立恢复。

验证提交 90e4c5d4450c001fe46a31fcf127a8115500f2b2 的 [GitHub Actions 运行](https://github.com/liming-cloud/ai-platform/actions/runs/35578865882) 已完成且 success。仓库为 PRIVATE，默认分支 main，远端分支仅 main、标签为空。

确认原工作区仅有已授权的五项重命名后，使用原暂存补丁恢复基线并立即快进至已推送提交；新位置的五份数据再次逐字节比较通过。当前分支改名为 main，唯一 origin 指向 GitHub 并跟踪 origin/main，HEAD 与 origin/main 双向差异为 0 0，暂存区和工作区为空。

清理前逐一确认工作区干净、分支提交已包含于 main；已删除文档重整和本次迁移两个额外工作树，以及 master、codex/trusted-rag-foundation-design、codex/docs-control-plane-v2 和 codex/github-migration 分支。原产品分支已改名，Gitee 远端、跟踪引用和专用凭据助手配置已移除；Gitee 托管仓库未操作。本地仅保留原仓库工作树及 main，没有标签或 stash。原有 68274 项忽略路径全部仍存在，没有清理 .env、依赖、缓存、历史对象或 reflog。

收口证据提交只修改本任务正文和生成索引，是上述验证提交的后继，不以自引用 SHA 伪造验证对象。交付时必须再确认该最终提交的 CI、完整历史克隆验证及本地/远端一致，并删除本轮临时验证克隆、日志、补丁和备份；最终 SHA 与对应 CI 运行链接随交付报告提供。
