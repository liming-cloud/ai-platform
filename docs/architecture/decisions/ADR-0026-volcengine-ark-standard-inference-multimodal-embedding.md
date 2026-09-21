# ADR-0026：M2 方舟标准推理与多模态嵌入协议

## 状态

已接受

## 日期

2026-07-28

## 决策负责人

产品负责人确认以下模型已开通，并各有 50 万 Token 研发额度：
`doubao-seed-2-1-turbo-260628` 与 `doubao-embedding-vision-251215`。

## 背景

ADR-0025 的 Coding Plan 模型编码在真实调用时被方舟拒绝。产品负责人提供的方舟官方示例明确使用标准推理基础地址
`https://ark.cn-beijing.volces.com/api/v3`：对话接口为 `/chat/completions`，多模态嵌入接口为 `/embeddings/multimodal`。

真实最小探针确认对话模型可用，嵌入模型返回 `data.embedding` 单个 2048 维组合向量。多模态嵌入把一个输入数组组合为一个向量，不能直接作为 RAG 批量文本嵌入协议。

## 决策

1. ADR-0025 中关于 Coding Plan 端点和模型编码的 M2 细节由本 ADR 取代；受控目录使用标准 `/api/v3` 基础地址、`doubao-seed-2-1-turbo-260628` 与 `doubao-embedding-vision-251215`。
2. `doubao-embedding-vision-251215` 由 `OPENAI_COMPATIBLE` 受控扩展内的方舟多模态适配实现；RAG 每个文本片段单独调用 `/embeddings/multimodal`，按原输入顺序组装向量批次，并严格校验 2048 维。
3. 该协议差异只保留在供应商扩展中；领域、公共契约、租户权限和模型配置 API 不新增方舟专属字段。
4. 真实调用仍仅限 M2 合成知识验收，且不得记录密钥、文本、模型回答、向量或厂商响应正文。

## 后果与验收

- `MODEL_ALLOWED_ENDPOINTS`、受控目录和真实验收脚本必须固定为 `https://ark.cn-beijing.volces.com/api/v3`。
- 多模态嵌入适配必须证明逐条调用、顺序保持、冻结维度校验和错误脱敏。
- M2 全链路验收必须重新验证真实嵌入索引、受控对话、引用回答和密钥密文保存。
