# 竞品事实源

Stage: facts  
Category: facts  
Status: current  
Review Status: not-reviewed  
Reviewers: product owner, architect  
Approval: pending  
Inputs: 2026-05-09 公开资料检索  
Outputs: 竞品分析事实源  
Depends On: none  
Load When: discovery/prd  
Next: `docs/10-discovery-brief.md`

## 官方资料

| 产品 / 平台 | 来源 | 关键事实 |
| --- | --- | --- |
| Dify Workspace | [Dify workspace docs](https://docs.dify.ai/en/use-dify/workspace/readme) | Workspace 隔离 apps、knowledge bases、members、model providers、plugins、billing；应用可使用 workspace 配置的模型、知识库和插件。 |
| Dify Model Providers | [Dify model providers docs](https://docs.dify.ai/en/guides/model-configuration/predefined-model) | 支持 workspace 级模型供应商配置、系统 provider、自定义 provider、连接测试和多类模型。 |
| Dify Knowledge | [Dify knowledge docs](https://docs.dify.ai/en/use-dify/knowledge/readme) | Knowledge 用作 RAG 上下文源，支持把企业自有数据注入应用。 |
| Dify Plugins | [Dify plugins docs](https://docs.dify.ai/en/use-dify/workspace/plugins) | 插件连接模型供应商、外部 API 和自定义工具，workspace scoped。 |
| Dify Logs | [Dify logs docs](https://docs.dify.ai/en/use-dify/monitor/logs) | 记录交互输入输出、系统元数据、耗时、token、反馈，用于调试和持续改进。 |
| RAGFlow | [RAGFlow GitHub](https://github.com/infiniflow/ragflow) | 开源 RAG engine，强调 deep document understanding、agent capabilities、template chunking、grounded citations、heterogeneous data sources。 |
| MaxKB | [MaxKB official site](https://maxkb.pro/index.html) | 开源企业级 agent 平台，强调 RAG pipeline、agentic workflow、MCP tool-use、多模型支持和第三方系统集成。 |
| MaxKB Knowledge Base | [MaxKB docs](https://docs.maxkb.pro/user_manual/dataset/dataset/) | 知识库支持 General Knowledge Base 和 Web Site Knowledge Base，包含文档上传、网站同步、向量化等能力。 |
| LlamaCloud | [LlamaCloud docs](https://docs.cloud.llamaindex.ai/) | hosted document processing and search，包含 Parse、Extract、Index，支持 130+ 文件格式、复杂文档解析、索引和检索。 |
| LangSmith | [LangSmith observability](https://info.langchain.com/AI-Observability) | 面向 LLM / Agent 应用的 tracing、monitoring、alerts 和 production scoring。 |
| LangSmith Evaluation | [LangSmith eval docs](https://docs.langchain.com/langsmith/evaluate-llm-application) | 支持基于 dataset 的实验、反馈分数、延迟、token 用量、prompt/model/tool metadata 分组。 |
| Azure AI Foundry Tools | [Microsoft Foundry tool catalog](https://learn.microsoft.com/en-us/azure/ai-foundry/agents/concepts/tool-catalog?view=foundry) | Agent tools 支持 web search、code interpreter、file search、Azure AI Search、function calling、MCP、OpenAPI 等。 |
| Azure AI Foundry Models | [Microsoft Foundry Models overview](https://learn.microsoft.com/en-us/azure/foundry/concepts/foundry-models-overview) | Model catalog 用于发现和使用多 provider 模型。 |
| Amazon Bedrock | [AWS Bedrock documentation overview](https://aws.amazon.com/documentation-overview/bedrock/) | Bedrock 覆盖 model evaluation、guardrails、agents、knowledge bases、模型调用日志等能力。 |
| Bedrock Knowledge Bases | [AWS KB data docs](https://docs.aws.amazon.com/en_us/bedrock/latest/userguide/kb-how-data.html) | Knowledge Bases 支持非结构化和结构化数据，包含文档、表格、图像、多模态文档和自然语言到 SQL 相关机制。 |
| Bedrock Monitoring | [AWS Bedrock logging docs](https://docs.aws.amazon.com/en_us/bedrock/latest/userguide/model-invocation-logging.html) | 可采集 invocation logs、model input、model output 到 CloudWatch Logs / S3。 |
| Google Vertex AI Model Garden | [Google Model Garden docs](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-garden/available-models) | Model Garden 用于发现、测试、调优和部署 Google 与开源/第三方模型。 |
| Vertex AI Evaluation | [Vertex AI evaluation API](https://docs.cloud.google.com/vertex-ai/generative-ai/docs/model-reference/evaluation) | Gen AI evaluation service 支持自定义 criteria、模型评分、函数调用指标等。 |
| OpenAI File Search | [OpenAI file search docs](https://platform.openai.com/docs/guides/tools-file-search/) | Responses API 的 file search 通过 vector stores 做 semantic + keyword retrieval，支持 metadata filtering。 |
| OpenAI Tools | [OpenAI tools docs](https://platform.openai.com/docs/guides/tools/file-search) | Responses API 支持 web search、file search、function calling、remote MCP 等工具。 |
| OpenAI Evals | [OpenAI agent evals docs](https://platform.openai.com/docs/guides/agent-evals) | 提供 agent 质量评估、trace grading、datasets 和 evals。 |
| Databricks AI Gateway | [Databricks AI Gateway docs](https://docs.databricks.com/gcp/en/ai-gateway/overview-serving-endpoints) | 为 model serving endpoint 提供 governance、monitoring、payload logging、rate limiting、usage tracking。 |
| Databricks Vector Search | [Databricks vector search docs](https://docs.databricks.com/aws/en/vector-search/query-vector-search) | 支持 ANN、full-text、hybrid search、reranker、filter，并强调生产身份认证。 |

## 竞品结论摘要

- 行业主流平台正在从“聊天应用”转向“模型 + 知识 + 工具 + 评估 + 观测”的平台化组合。
- 云厂商优势在托管、安全和生态，但容易形成云锁定。
- 开源平台优势在私有化和集成灵活性，但企业级治理、审计、细粒度权限和可观测通常需要二次建设。
- 文档解析、数据库知识源、工具治理和租户级使用频率监控，是本产品可形成差异的关键方向。
