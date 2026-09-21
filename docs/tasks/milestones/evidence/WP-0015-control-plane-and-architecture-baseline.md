# WP-0015 控制面与架构基线证据

## 范围

本证据只覆盖 WP-0015：控制面恢复、连续工作包、可信 RAG 架构审计和自动治理门禁。没有修改生产前端、业务 API、数据库或运行时业务代码。

## 红绿证据

红灯命令：

    python3 -m unittest scripts.quality.test_project_governance

在治理实现前新增 4 项测试，分别证明以下问题：

- latestEvidence 超过 8 条未被拒绝。
- currentFrontier 超过 12 个 ADR 未被拒绝。
- DECISIONS.md 与 ADR 元数据漂移未被拒绝。
- resume-context 超过 12 KiB 未被拒绝。

原有 9 项测试通过，新增 4 项按预期失败。

绿灯使用同一命令，13 项测试全部通过。最终门禁结果在提交前刷新。

## 实现

- scripts/project_governance.py 新增 ADR 元数据解析、决策索引生成、状态一致性、证据上限、活动 ADR 上限和恢复上下文预算。
- Makefile 新增 decisions-sync。
- project/DECISIONS.md 改为生成文件，并保留已确认人工决策区。
- WP-0014 精简为总控门禁，新增 WP-0015 至 WP-0020 连续闭环。
- docs/audits/WP-0015-rag-architecture-baseline.md 登记架构断层、债务计数、责任包和退出条件。
- ADR-0074 冻结用户已接受的产品与研发基线。

## 架构门禁基线

2026-09-02 的 make architecture-guard 通过 28 项测试：

- Java 内联 SQL 15。
- 手工分页 3。
- SQL 文件内联、UUID 主标识、runtime 非启动类、Sa-Token 越界、超预算 package、旧 api、旧 adapter、backend/runtimes、游标普通列表均为 0。
- 前端 architecture guard 通过。

## 验证结果

2026-09-02 在 codex/trusted-rag-foundation-v1 执行：

| 命令 | 结果 |
| --- | --- |
| python3 -m unittest scripts.quality.test_project_governance | 通过，14 项 |
| make decisions-sync | 通过，生成 ADR-0001 至 ADR-0074 |
| make project-verify | 通过，20 个工作包，当前前沿 WP-0015 |
| make project-status | 通过，活动包 WP-0014、WP-0015，无阻塞和待决策 |
| make resume-context | 通过，实际输出 3,512 bytes，低于 12 KiB |
| make architecture-guard | 通过，28 项测试，债务计数未增长 |
| git diff --check | 通过 |

## 评审与提交

- 固定差异规范轴、需求轴和六维质量评审通过，无未解决发现。
- 工程助手运行 run-bec56d48bbd5458c 完成全部节点，质量快照 wp0015-quality-snapshot 通过。
- 实现提交：22a23a612。
- 控制面收口提交：以本文件的后续 Git 提交为准。
