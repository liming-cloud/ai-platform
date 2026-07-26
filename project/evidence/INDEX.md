# 证据索引

所有证据必须可复现、绑定 Git 提交并由对应工作包引用。

## WP-0001

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 治理单元测试 | `python3 -m unittest scripts.quality.test_project_governance` | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 4 项通过 |
| 仓库控制面验证 | `python3 scripts/project_governance.py verify` | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 通过，识别 2 个工作包，当前前沿为 WP-0001 |
| 跨会话恢复输出 | `make resume-context` | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 通过，输出 89 行最小恢复上下文 |
| 既有交付契约 | `make delivery-contract` | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 5 项测试与 4 个脚本语法检查通过 |
| 流水线配置语法 | Ruby 标准 YAML 解析器读取 `.github/workflows/ci.yml` | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 通过 |
| 产品与架构设计评审 | 人工评审 M0 文档 | `e1e5ccced4457af85ac84ddea2db9a0663e3173b` | 等待产品负责人 |

自动证据执行时间为 2026-07-26，执行环境为当前 macOS 工作区。WP-0001 仍为进行中，人工评审未通过前不得创建实施分支。

## 证据规则

- 命令证据记录完整命令、提交、环境和结果。
- 性能证据记录数据集、硬件、并发、预热和分位值。
- RAG 证据记录数据集版本、模型部署版本、索引版本和指标。
- 安全证据记录攻击场景和被保护的信息边界。
- 截图和录像只能补充自动证据，不能替代自动证据。
