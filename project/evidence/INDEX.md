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
| M0 治理回归 | `make validate` | `f1eb43bb4fb74fc2678b9bb38791f8393aeb4548` | 5 项治理测试和控制面验证通过 |
| 旧实现清理 | 检查受控文件和 `project/evidence/WP-0001-cleanup.md` | `f1eb43bb4fb74fc2678b9bb38791f8393aeb4548` | 删除 1438 个旧实现文件，禁止旧路径剩余 0 个 |
| 前端设计完整性 | 检查 `docs/design/foundation-frontend-design.md` 的标题与禁止样式 | `f1eb43bb4fb74fc2678b9bb38791f8393aeb4548` | 27 个二级章节、24 个三级章节、长破折号 0 个 |
| 规则文档中文化 | 检查根 `AGENTS.md` 的自然语言行 | `f1eb43bb4fb74fc2678b9bb38791f8393aeb4548` | 未发现整行英文说明候选 |
| 流水线配置语法 | Ruby 标准 YAML 解析器读取 `.github/workflows/ci.yml` | `f1eb43bb4fb74fc2678b9bb38791f8393aeb4548` | 通过 |
| 可扩展研发目录规划 | `make validate` 与目录章节门禁 | `8b801f405e7651b506c681b5dfcc5c9e162dbbdb` | 6 项治理测试通过，26 个二级章节和 28 个目录树完整 |
| 目录方案评审证据 | `project/evidence/WP-0001-repository-layout.md` | `8b801f405e7651b506c681b5dfcc5c9e162dbbdb` | 完成两种方案比较、依赖矩阵、扩展路径和 M1 最小落地范围 |
| 产品与架构设计评审 | `project/evidence/M0-design-approval.md` | `66930880d2e1f0b872d7f2600b82a8cee2e4fe2e` | 产品负责人已批准 M0 |

最新自动证据执行时间为 2026-07-26，执行环境为当前 macOS 工作区。WP-0001 已通过人工评审并关闭。

## WP-0002

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 工作包激活 | `project/evidence/M0-design-approval.md` | `f2f285c676c29d680d58c65b46cecd78b3267cc1` | M0 已通过，实施分支已创建 |
| 后端工程基线 | `project/evidence/WP-0002-backend-baseline.md` | `8f2275a0fec8468fef5326b13a9eb18c179edf57` | 固定工具链、三运行入口和确定性供应商契约验证通过 |
| 前端工程基线 | `project/evidence/WP-0002-frontend-baseline.md` | `ddaf319980968611f95d6367845c80d7724023c7` | 双应用、Fluent UI、状态设计和响应式浏览器验收通过 |
| 容器化运行基线 | `project/evidence/WP-0002-docker-baseline.md` | `ccc61569ca1560c7fc9b8cb0081330ab10e4c664` | 八个服务健康，五个应用入口及容器安全属性验证通过 |
| 供应商安全探测 | `project/evidence/WP-0002-provider-security.md` | `e3101ee5cb0a1e4b62b46f668f349cc6b9dc36a3` | 密钥生命周期、出站地址策略和 Spring AI 兼容协议探测通过 |
| 限界上下文边界 | `project/evidence/WP-0002-context-boundaries.md` | `925590273f718f5c72361224c634b86a457b6407` | M1 六个上下文模块及治理门禁验证通过 |
| 工作包最终验收 | `project/evidence/WP-0002-acceptance.md` | `5dad356bda6a0f51a0957705ce1c8ae6ff28820a` | 全量代码门禁和最新容器重建通过，WP-0002 关闭 |

## WP-0003

| 证据 | 命令或评审 | 提交 | 结果 |
| --- | --- | --- | --- |
| 框架使用基线 | `docs/research/WP-0003-framework-baseline.md` | `8a3a59575` | Sa-Token、MyBatis-Plus、Flyway 与租户隔离边界已固定 |
| 可信请求上下文 | `project/evidence/WP-0003-trusted-context.md` | `47cd20cb995212f3cb0d11781e06a853af2b7866` | 框架无关认证上下文红绿测试和全后端回归通过 |
| Sa-Token 可信会话 | `project/evidence/WP-0003-sa-token-session.md` | `a619aac7e1ec9804a41fddf26358f609405716f1` | 真实 Sa-Token 登录态、上下文恢复、撤销和框架隔离测试通过 |
| 演示租户认证基座 | `project/evidence/WP-0003-authentication-foundation.md` | `4e0c258b2205a7ecb21d606860366bbf28b69810` | PostgreSQL 成员事实、密码校验、Redis 会话、HTTP 登录注销和 Docker 组合部署通过 |
| 知识空间纵向切片 | `project/evidence/WP-0003-knowledge-space.md` | `02e4a64aea30c6acc485c93e6c5371a1cc9c2fa7` | 领域权限、真实事务、幂等、并发配额、游标、受控接口和 Docker 增量部署通过 |
| 文本数据源与暂存边界 | `project/evidence/WP-0003-text-staging.md` | `d66a78e761c461db2c57434ed6607c569b462e5a` | 数据源状态机、流式大小限制、严格 UTF-8、摘要和临时文件清理通过 |
| 文本数据源数据库事务 | `project/evidence/WP-0003-text-data-source-transaction.md` | `8821c0b92758878eec954969ad5e5d857786d07a` | 历史幂等重放、并发配额、租户隔离、乐观锁、回滚和事务事件通过 |
| 文本数据源受控接口 | `project/evidence/WP-0003-text-data-source-api.md` | `b7474dd72a5218f8b2160066ebe7607c38cc9546` | 应用权限、创建、游标列表、发布、错误结构和租户隔离接口测试通过 |
| 文本内容上传编排 | `project/evidence/WP-0003-text-upload-orchestration.md` | `c79ec9c4d7d214aace7781852bb004e6e2e010d3` | 流式上传、历史幂等、对象元数据核验、失败关闭和发布原子性通过 |

## 证据规则

- 命令证据记录完整命令、提交、环境和结果。
- 性能证据记录数据集、硬件、并发、预热和分位值。
- RAG 证据记录数据集版本、模型部署版本、索引版本和指标。
- 安全证据记录攻击场景和被保护的信息边界。
- 截图和录像只能补充自动证据，不能替代自动证据。
