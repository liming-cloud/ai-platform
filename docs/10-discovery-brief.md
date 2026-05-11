# 10 竞品分析与产品发现

Stage: discovery  
Category: stage-main  
Status: draft  
Review Status: not-reviewed  
Reviewers: product owner, architect, engineering lead  
Approval: pending  
Inputs: 用户产品愿景、`docs/facts/market-sources.md`  
Outputs: 产品定位、机会点、初始范围候选  
Depends On: `docs/facts/market-sources.md`  
Load When: discovery/prd  
Next: `docs/20-prd-and-scope.md`

## 产品目标

建设一个企业级多租户 AI 平台，统一管理模型供应商、知识来源、知识库问答、工具集成、应用运行、监控分析和治理审计。平台不应只是聊天应用生成器，而应成为企业内部 AI 能力的控制面、运行时和观测面。

## 目标用户

- 平台管理员：配置租户、用户、权限、模型供应商、密钥、配额和审计策略。
- 租户管理员：管理本租户知识库、应用、工具、成员和预算。
- 知识工程师：接入文档、网页、数据库、对象存储，处理解析、切分、索引、质量评估和权限映射。
- AI 应用构建者：组合模型、知识库、提示词、工具、工作流和发布渠道。
- 业务用户：通过企业门户、嵌入式组件或 API 使用问答和智能助手。
- 运维与治理人员：监控模型调用、成本、延迟、失败、知识命中、工具调用和安全事件。

## 竞品分层

| 分层 | 代表产品 | 强项 | 对本产品的启示 |
| --- | --- | --- | --- |
| 开源 LLM 应用平台 | Dify | Workspace、模型供应商、知识库、插件、日志一体化 | 快速应用编排很重要，但企业级租户、权限、审计和可观测需要更强控制面 |
| 开源 RAG / Agent 平台 | RAGFlow、MaxKB | 文档理解、RAG pipeline、workflow、MCP/tool use、本地化部署 | 文档解析质量和可解释 chunk 是核心差异点，不能只做简单向量库 |
| 文档处理 / RAG 托管服务 | LlamaCloud | 多格式解析、抽取、索引和检索托管 | 对复杂文档、表格、扫描件，需要独立 ingestion plane 和质量评估 |
| 云厂商 AI 平台 | Azure AI Foundry、Amazon Bedrock、Google Vertex AI | 模型目录、Agent、工具、知识源、云原生安全和监控 | 企业会要求同类能力，但私有化、混合云和供应商中立是差异机会 |
| LLM 可观测与评估 | LangSmith、Databricks / MLflow | tracing、eval、dataset、experiment、监控、质量回归 | 监控不能只统计 token；必须覆盖 trace、知识命中、工具调用、质量和成本 |
| API 原语平台 | OpenAI Platform | Responses、tools、file search、vector stores、evals | 平台应兼容 OpenAI 风格 API，但不能只绑定单一 provider |

## 竞品关键事实

- Dify 的 workspace 模型把应用、知识库、成员、模型配置、插件和账单隔离在同一组织单元内；这说明企业平台需要先建资源域和权限域，而不是先建聊天页面。
- RAGFlow 强调深度文档理解、模板化切分、可视化 chunk、引用追踪、多源数据和 agent 能力；这说明知识库问答的竞争点在解析质量、可解释检索和可调优 pipeline。
- MaxKB 以企业级 agent、RAG pipeline、workflow、MCP 工具调用和多模型支持作为核心卖点；这说明国内企业场景会重视低门槛部署、私有模型和现有系统集成。
- LlamaCloud 把 parse、extract、index 拆成独立能力；这说明知识 ingestion 不应隐藏在问答应用里，应成为可观测、可重跑、可评估的子系统。
- Azure AI Foundry 和 Amazon Bedrock 都把模型、知识、tools / action、guardrails / governance、monitoring 组合成云原生 Agent 平台；这说明本产品必须把 tool governance 和 observability 放进第一阶段架构。
- LangSmith / Databricks 等强调 tracing、evaluation、dataset、质量监控和 inference logging；这说明平台监控应形成 AI telemetry 数据模型，而不是普通 API 日志。

## 市场机会

1. 供应商中立的企业 AI 控制面  
   企业不会长期只用单一模型厂商。平台需要统一 OpenAI、Azure OpenAI、Anthropic、Gemini、Bedrock、国内大模型、私有模型和 OpenAI-compatible endpoint。

2. 多租户 + 知识权限一体化  
   多数开源平台强调 workspace，但对企业内部组织、角色、知识 ACL、数据行列权限、模型配额和审计闭环不足。

3. 文档和数据库统一知识来源  
   企业知识不只在文档里。数据库、数据仓库、BI 指标、业务系统 API 都应成为可治理知识源。

4. Tool use 的治理和审批  
   让 AI 调用企业系统必须有工具目录、凭证隔离、输入输出 schema、风险等级、审批策略、执行审计和回滚策略。

5. 面向 AI 平台的观测和成本治理  
   需要按租户、应用、模型、知识库、文档、chunk、工具和用户统计调用量、命中率、延迟、失败、质量评分和成本。

## 产品定位

本产品建议定位为：

> 企业 AI 平台控制面 + 知识运行时 + 工具治理 + AI 可观测系统。

不是第一优先做“低代码聊天机器人生成器”，而是先构建企业级平台底座：租户、模型、知识、工具、审计、监控、配额、质量评估和 API / UI 双入口。

## 初始非目标

- 不直接训练大模型。
- 不第一阶段做完整 no-code workflow 画布。
- 不第一阶段替代企业现有 IAM、数据仓库、BI 或 ESB。
- 不承诺自动理解所有数据库业务语义；数据库知识源必须经过 schema、权限、语义层和 SQL 安全策略。

## 主要风险

- 知识 ingestion 复杂度被低估，尤其是 PDF 表格、扫描件、多语言、权限继承和增量同步。
- 多模型统一抽象过度简化，导致不同模型能力、上下文、工具调用和多模态差异无法表达。
- 工具调用若缺少治理，会引入越权、误操作和审计缺失。
- 监控若只做 API 统计，无法支撑 RAG 质量、回答可信度和业务价值分析。
- 如果首期范围过大，容易做成很多半成品模块。必须以 early runnable vertical slice 先打通真实链路。
