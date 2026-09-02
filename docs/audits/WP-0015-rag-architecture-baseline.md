# WP-0015 可信 RAG 架构与产品断层基线

## 结论

当前仓库不是全面失控的“屎山”：四层依赖门禁、上下文隔离、版本化索引、受控模型配置和 Spring AI 适配基础仍然成立。继续扩张仍会把既有侵蚀固化，因此 M6 必须先以 WP-0015 恢复控制面和架构基线。

## 2026-09-02 实测基线

make architecture-guard 通过 28 项测试，结果为：

- SQL 文件内联问题 0；Java 内联 SQL 15。
- UUID 主标识 0；runtime 非启动类 0。
- Sa-Token 越界 0；超预算 package 0。
- 旧 api、adapter 与 backend/runtimes 目录 0。
- 游标普通列表 0；手工分页 3。
- 前端 architecture guard 通过。

这些数字是债务基线，不是永久 allowlist。可信 RAG 链路触及的 Java 内联 SQL和手工分页必须在对应工作包内清零。

## 已确认断层

| 断层 | 当前事实 | 退出工作包 |
| --- | --- | --- |
| 伪流式 | QueryRunService 在响应前完成生成，SSE 只回放结果；SpringAiChatModelAdapter.stream 未进入真实链路 | WP-0019 |
| 普通用户产品 | /ask?assistantPage=N 是一次性问答，无会话聚合、历史恢复和独立入口 | WP-0016、WP-0019 |
| 检索治理 | 版本基础存在，但旧版／治理版分支、可空依赖和兼容构造器并存 | WP-0018 |
| 成员授权 | 已有租户和知识库边界，尚未证明成员授权在 ANN 与 FTS 候选生成前失败关闭 | WP-0018、WP-0020 |
| 数据接入 | 现有文本和结构化路径不能覆盖受限解析、OCR、恶意文件和可恢复索引流水线 | WP-0017 |
| 控制面 | STATE 历史证据过多、DECISIONS 索引漂移、WP-0014 过程记录过长 | WP-0015 |

## 框架和 HTTP 边界

- application 与 domain 的 Chat、Embedding 调用只能依赖内部 Port。
- Spring AI 的 OpenAI-compatible 实现负责标准模型协议；业务层不得直接发 HTTP。
- Rerank 通过最小 RerankerPort 与检索后处理 seam 接入，不伪造不存在的统一 Spring AI 标准。
- 供应商模型发现、控制面 API 与非标准 OCR／多模态协议可以在 infrastructure 中独立使用 HTTP，但必须落实允许域名、超时、有限重试、日志脱敏和 SSRF 防护。

## 代码图谱结论

重点探索了 AssistantVersion、IndexVersion、QueryRunService、CandidateSearchService、PgVectorIndexStore、SpringAiChatModelAdapter、QueryWorkspace 和 ProductShell。影响分析表明 AssistantVersion、QueryRunService、IndexVersion 分别影响约 229、141、540 个符号。

因此后续采用 seam 迁移：

1. 先冻结公共契约和失败语义。
2. 在单一上下文内清理旧新分支与兼容构造器。
3. 用纵向测试证明新链路，再切换唯一调用方。
4. 不在同一工作包同时重写助手、索引、查询和前端壳。

## 债务处置

| 债务 | 处置 |
| --- | --- |
| 15 处 Java 内联 SQL | 建立路径清单；WP-0017 至 WP-0019 触及项迁入 infrastructure 的 MyBatis XML，其余保持计数不可增长 |
| 3 处手工分页 | 触及普通目录时改为服务端页码、页大小和总数；事件序号、向量候选与任务批次不套普通分页 |
| legacy 分支和可空依赖 | 由各闭环工作包在增加能力前删除其触及范围，不做全仓批量替换 |
| 文档上下文膨胀 | latestEvidence、活动 ADR 和 resume-context 由自动门禁限制 |

## 本工作包不做

- 不修改生产前端。
- 不实现多格式解析、混合召回、会话或真实流式。
- 不对未触及上下文做无关重构。
- 不把本审计当作安全、性能或产品验收结论。
