# GOV-0002：GitHub 迁移与本地单分支收口

| 字段 | 值 |
| --- | --- |
| ID | GOV-0002 |
| 状态 | 进行中 |
| 负责人 | 仓库迁移与治理 |
| 分支 | main |
| 里程碑 | M6 |
| ADR | ADR-0081 |
| 证据 | pending |
| 提交 | pending |
| 输入提交 | 346214b1d3cec5d6394e8c344d102dc9fdb3ab0d |
| 基线提交 | 346214b1d3cec5d6394e8c344d102dc9fdb3ab0d |
| 人工验收 | 不适用 |

## 问题

仓库转移到个人 GitHub 私有仓库，需要保留可验证历史并收敛为本地和远端唯一 main。五项已暂存 fixture 重命名尚未提交，现有消费者仍引用旧路径，CI 尚未监听 main。

## 范围内

将五项 fixture 原字节迁移到 project/fixtures，同步消费者、测试和现行说明；适配 CI；仅推送当前分支完整历史为 GitHub main；验证后清理额外本地分支、工作树和 Gitee 跟踪引用。

## 范围外

不修改业务源码、公共业务契约、部署、数据库、OpenDesign、历史验收或 M6 / WP-0017 完成事实；不删除 Gitee 托管仓库，不删除本地忽略配置、依赖和缓存。

## 公共接口与数据

保留 CLI、Make 入口及 fixture JSON 格式；唯一参考数据根改为 project/fixtures，不保留旧路径回退。GitHub 目标为 https://github.com/liming-cloud/ai-platform，唯一日常远端为 origin。

## 约束

用户 2026-09-21 明确选择将五项人工重命名纳入迁移提交，并授权 Agent 推送和清理 Git 遗留；这取代先前针对这些暂存修改的保留要求，不改写 GOV-0001 的历史保护事实。原暂存补丁 SHA-256 为 53b363e5904c8e93062bd9879566c0e7694b6182339e810dfffc869fc2946c43。

保留当前分支可达历史及原 SHA，不 squash、force push、mirror 或附带标签。清理前检查新修改；本地 .env、依赖、缓存和工具配置保留。秘密不输出、不入 Git 或日志。

## 验收标准

本地 HEAD、origin/main 与 GitHub main 一致；Git 状态为空；唯一 main、GitHub origin 和原工作树。新克隆可校验650份历史资料，CI对最终提交通过，五项 fixture 消费者通过离线回归。

## 验证

make demo-fixtures-verify；make project-status；make project-verify；make architecture-guard；make resume-context；受影响 Shell 脚本 bash -n。只做离线检查，不运行创建业务对象的验收。

## 证据

待迁移提交、全新克隆和 GitHub CI 验证后登记。

## 下一前沿

完成 fixture 路径红绿回归和迁移提交，认证后推送、校验新克隆与 CI，再收口本地 Git 遗留。当前 GitHub 本机认证待完成；产品仍停留在 WP-0017 下一独立设置切片的合同确认。

## 固定输入与决定

已读取 Charter、ROADMAP、STATE、GOV-0001、WP-0017 及其12项 ADR和 ADR-0081，并运行 project-status。输入提交是完整非浅仓库，650项历史索引的来源提交为其祖先；其他三个既有分支也在输入提交可达历史中。

使用 Codex 本地工程助手 wayfinder、tdd、code-review；不创建或复用业务 Runtime。代码图谱定位 verify/evaluate；通用符号关系含同名误匹配，实际影响以定向路径消费者和离线回归核对，不扩大为全仓重构。
