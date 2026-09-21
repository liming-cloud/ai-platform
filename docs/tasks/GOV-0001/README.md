# GOV-0001：仓库控制面重建与文档精简

| 字段 | 值 |
| --- | --- |
| ID | GOV-0001 |
| 状态 | 进行中 |
| 负责人 | 文档与治理工具 |
| 分支 | codex/docs-control-plane-v2 |
| 里程碑 | M6 |
| ADR | ADR-0081 |
| 证据 | pending |
| 提交 | pending |
| 输入提交 | e7a1d673229acf8c7932dcae85699e517f2adab5 |
| 基线提交 | e7a1d673229acf8c7932dcae85699e517f2adab5 |
| 人工验收 | 不适用 |

## 问题

当前规范、历史设计、任务进度和执行资料混杂；根 README 仍描述 M0，部分设计被后续 ADR 取代，状态摘要重复且存在漂移。

## 范围内

统一 docs 控制面；重写当前有效设计入口；按来源和依赖精简资料；保留可验证 Git 历史；迁移治理工具、索引和质量资产。

## 范围外

不实现业务、不迁移前后端源码、不部署、不重跑开户、不改 OpenDesign、不推进 WP-0017 或 M6。

## 公共接口与数据

保留 make 治理入口及业务 WP/SLICE 参数；新增只读历史定位校验，STATE 升级为指针结构。

## 约束

ADR-0081 已由用户执行指令接受。原工作区五项 fixture 暂存 diff SHA-256 为 53b363e5904c8e93062bd9879566c0e7694b6182339e810dfffc869fc2946c43；本任务在独立工作树实施，不能提交或覆盖它们。

## 验收标准

当前文件数量实质减少；删除均可恢复；当前引用有效；校验拒绝伪造证据和完成；产品状态不变；新会话可由仓库恢复。

## 验证

make project-status；make project-verify；make architecture-guard；make resume-context；受影响运维脚本单测。日志仅存仓库外。

## 证据

待产生本任务的红绿、固定 diff 评审、迁移校验和提交绑定；不复用 RAG Runtime。

## 下一前沿

建立历史定位红灯测试，盘点文件引用后实施迁移。负责人：Agent。阻断：无。

## 已确认决定与读取基线

已接受用户执行的文档精简计划；读取输入提交的 Charter、ROADMAP、STATE、WP-0017 及其 12 项 ADR，另外读取 ADR-0004/0008 与参考仓库 document-control/task-workspace。工程助手使用 Codex 本地 wayfinder、codebase-design、tdd、code-review；不创建 Runtime 阶段产物。
