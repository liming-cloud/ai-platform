# ADR-0025：M2 火山方舟 Coding Plan 受控模型目录

## 状态

已接受

## 日期

2026-07-28

## 决策负责人

产品负责人确认已开通 `doubao-embedding-vision` 与 `doubao-seed-2.1-turbo`，用于 M2 受控研发验收。

## 背景

ADR-0024 已将真实模型验证切换至火山方舟，但当时尚未确认调用计划、模型编码和嵌入维度。方舟 Coding Plan 官方文档规定 OpenAI 兼容地址为
`https://ark.cn-beijing.volces.com/api/coding/v3`，调用参数使用套餐已开通的 Model Name，不使用普通方舟推理接入点 ID；普通 `/api/v3` 地址不会消耗 Coding Plan 额度。来源：[Coding Plan 快速开始](https://www.volcengine.com/docs/82379/1928261)。

官方套餐列表记录 `doubao-seed-2.1-turbo` 的上下文窗口和最大输出均为 256K，且列出 `doubao-embedding-vision` 向量能力。来源：[Coding Plan 套餐概览](https://www.volcengine.com/docs/82379/1925114)。最小合成文本探测确认后者可通过 `/embeddings` 返回 2048 维向量；探测只记录模型名、HTTP 成功状态和维度，不保存密钥、输入或向量内容。

## 决策

1. ADR-0024 中“普通方舟端点与推理接入点 ID”的 M2 验收细节由本 ADR 取代；M2 使用 Coding Plan OpenAI 兼容端点。
2. 平台受控目录固定两条初始候选：`doubao-seed-2.1-turbo` 的 `CHAT` 能力为 262144 上下文窗口与 262144 最大输出；`doubao-embedding-vision` 的 `EMBEDDING` 能力为 131072 上下文窗口与 2048 嵌入维度。
3. 两个模型编码、端点和能力值是平台维护的目录事实。租户管理员和普通用户只能选择已发布条目，不能自行输入厂家、端点、模型编码或维度。
4. 本决策只授权 M2 在受控合成数据上进行最小研发调用，不代表生产采购、服务等级、模型质量或套餐合规结论。
5. 运行时嵌入向量的上限采用当前 pgvector `vector` 存储类型支持的 16000 维，以接受已验证的 2048 维模型输出；当前检索为精确余弦检索，不创建近似向量索引。若后续启用 HNSW 或 IVFFlat，超过 2000 维的目录条目必须先采用 `halfvec`、降维或其他经 ADR 接受的存储方案。

## 后果与验收

- `MODEL_ALLOWED_ENDPOINTS`、受控目录端点与运行时端点必须固定为 Coding Plan 地址，并继续经 ADR-0010 的 HTTPS 出站策略校验。
- 真实验收必须证明：密钥写入数据库密文、文本嵌入索引成功、受控 Chat 返回经引用校验后的回答，以及日志和证据不含密钥、正文或向量。
- 任何更换套餐、模型、端点、维度或调用计划的动作，都需要新的平台目录条目和 ADR。
- pgvector 的近似索引能力不得因本 ADR 的精确检索维度上限而被隐式启用。
