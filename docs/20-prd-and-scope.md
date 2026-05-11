# 20 PRD 与范围边界

Stage: prd  
Category: stage-main  
Status: draft  
Review Status: agent-reviewed  
Reviewers: product owner, architect, engineering lead  
Approval: agent-approved-for-s3  
Inputs: `docs/10-discovery-brief.md`  
Outputs: 产品范围、验收标准、非目标和 NFR  
Depends On: `docs/10-discovery-brief.md`  
Load When: prd/architecture/planning  
Next: `docs/30-project-harness.md`, `docs/40-architecture-and-tech-spec.md`

## 交付目标

`production-targeted`。第一阶段可以小范围交付，但每个切片必须有真实持久化、真实鉴权、真实审计、真实配置和可验证运行证据。不得使用 demo 机制冒充完成态。

## 核心问题

企业希望使用多家模型、多类知识源和企业系统工具，但缺少一个统一平台来解决：

- 模型供应商和模型能力分散。
- 企业知识来源复杂，文档与数据库无法统一治理。
- AI 调用企业系统缺少工具治理、审批和审计。
- 调用成本、质量、知识命中和工具效果无法按租户和应用持续监控。

## MVP 范围

### 1. 多租户与身份权限

- 租户、组织、用户、角色、权限和资源归属。
- 平台管理员和租户管理员分层。
- 关键操作审计。
- 租户级资源隔离、配额和开关。

### 2. 模型供应商与模型目录

- Provider catalog：OpenAI-compatible、Azure OpenAI、Anthropic、Gemini、国内模型、私有模型。
- Model catalog：模型类型、能力标签、上下文长度、价格、限流、工具调用支持、多模态能力。
- Secret management：密钥加密存储、轮换、测试连接、租户级授权。
- Model gateway：统一调用、路由、限流、重试、超时、fallback、成本统计。

### 3. 企业知识库

- 文档知识源：PDF、DOCX、PPTX、XLSX、Markdown、HTML、TXT、图片/扫描件作为后续增强。
- 数据库知识源：MySQL、PostgreSQL、SQL Server、Oracle 等通过连接器和语义层接入。
- ingestion pipeline：解析、清洗、切分、元数据、权限、embedding、索引、同步、重建。
- retrieval pipeline：hybrid search、metadata filter、rerank、引用、置信度和命中分析。
- 知识权限：租户、知识库、文档、chunk 或 metadata 级别过滤策略。

### 4. 问答与应用运行时

- Chat / QA 应用：模型、知识库、提示词、参数、引用展示、反馈。
- 会话记录：输入、输出、上下文、检索结果、工具调用、token、耗时、错误。
- API 和 Web UI 双入口。
- 基础发布机制：内部使用、API key / app key、租户内可见范围。

### 5. Tools 与企业系统集成

- Tool catalog：OpenAPI、HTTP API、MCP server、数据库查询、内部系统动作。
- Tool policy：只读 / 写入 / 高风险动作分类。
- 凭证隔离：租户级、应用级、工具级密钥。
- 执行审计：输入、输出、调用人、调用链、风险等级、结果。
- 高风险 tool 必须支持审批或 human-in-the-loop。

### 6. 监控、评估和治理

- 模型调用监控：租户、应用、provider、model、token、成本、延迟、错误。
- 知识监控：知识库访问、文档命中、chunk 命中、未命中问题、检索质量。
- 工具监控：调用频率、成功率、失败原因、风险动作。
- 质量评估：反馈、人工评分、样本集、离线 eval、回归对比。
- 配额和告警：租户预算、token 上限、错误率、延迟、异常调用。

## 用户场景

| 场景 | 触发角色 | 结果 |
| --- | --- | --- |
| 接入新模型供应商 | 平台管理员 | 密钥加密保存，连接测试通过，模型进入目录并可授权给租户 |
| 建立企业知识库 | 知识工程师 | 文档完成解析、切分、embedding 和索引，可查看失败与质量报告 |
| 将数据库作为知识源 | 租户管理员 / 数据管理员 | 配置只读连接、schema 白名单、语义描述和 SQL 安全策略后可问答 |
| 发布知识问答应用 | AI 应用构建者 | 选择模型和知识库，完成 prompt / retrieval 配置，发布给租户成员 |
| 配置企业系统工具 | 平台管理员 / 租户管理员 | 工具 schema、凭证、风险等级和审批策略生效，可被应用调用 |
| 监控平台使用频率 | 运维人员 | 按模型、知识库、文档、工具、租户和应用查看频率、成本、质量 |

## 验收标准

- 可以创建至少 2 个租户，并证明模型配置、知识库、应用和调用记录隔离。
- 可以配置至少 2 类模型供应商，其中一个为 OpenAI-compatible endpoint。
- 可以上传文档、生成索引、完成带引用的知识库问答。
- 可以配置一个数据库只读知识源，并通过受控语义层回答问题；不得让模型直接拼接任意 SQL。
- 可以注册一个 OpenAPI / HTTP 工具，并在审计记录中看到工具调用链路。
- 可以按租户、应用、模型、知识库、文档、工具查看调用频率、成本、延迟、错误和反馈。
- 所有核心配置变更都有审计记录。

## 非目标项

- 第一阶段不做大模型训练平台。
- 第一阶段不做完整工作流画布，只做可配置应用和受控工具调用。
- 第一阶段不做跨区域多活。
- 第一阶段不做全类型 OCR 和复杂版面解析自研；可以预留 parser provider 扩展点。
- 第一阶段不承诺自然语言自动生成任意 SQL；数据库知识源必须走语义层和安全策略。

## 非功能约束

- 安全：租户隔离、RBAC、密钥加密、审计、最小权限。
- 可靠性：provider 调用超时、重试、限流、熔断和 fallback。
- 可观测：trace id 贯穿 model / retrieval / tool / API / UI。
- 可扩展：模型供应商、parser、embedding、vector store、reranker、tool executor 都必须可插拔。
- 可运营：配额、成本、使用频率、质量评估和告警必须内建数据模型。
- 合规：日志保留策略、敏感信息脱敏、数据删除和导出策略需要在架构层预留。

## PRD Gate

结论：`agent-approved-for-s3`。

已确认项：

- 部署模式：首期私有化优先；本地开发采用 Docker Compose 依赖 + 前后端本机进程。
- 前端技术栈：Vue 3 + TypeScript + Vite。
- 租户隔离等级：首期共享库共享表 + `tenant_id` 强隔离 + MyBatis-Plus tenant interceptor。
- 后端默认技术栈已由用户确认：Spring Boot + Spring AI + Sa-Token + MyBatis-Plus + Redisson + Caffeine，且不引入 Python worker。

后续仍需产品优先级确认的事项：

- 首批模型供应商和知识源优先级需要确认。
