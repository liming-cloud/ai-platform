# 厂商连接预填设计

## 范围与来源

2026-09-07 产品负责人允许只读调查。通过 Exa 进行 7 次定向检索（35 条候选），只采用厂商官方资料。停止条件为首版预设的厂商、文本能力、地址与协议均有明确出处；不调用模型、不测试真实密钥、不承诺厂商全部能力。

| 厂商预设 | 已核实的首版能力 | 地址设计与官方依据 |
| --- | --- | --- |
| DeepSeek | Chat | `https://api.deepseek.com`；[官方接入说明](https://api-docs.deepseek.com/) |
| 阿里云百炼 · 北京 | Chat | 用户填写业务空间 ID，再生成 `https://{WorkspaceId}.cn-beijing.maas.aliyuncs.com/compatible-mode/v1`；[官方兼容说明](https://help.aliyun.com/zh/model-studio/compatibility-of-openai-with-dashscope) |
| 阿里云百炼 · 北京 | 文本 Embedding | 使用 Embedding 官方明确示例的 `https://dashscope.aliyuncs.com/compatible-mode/v1`，不推断未核实的多模态兼容性；[官方 Embedding 说明](https://help.aliyun.com/zh/model-studio/embedding-interfaces-compatible-with-openai) |
| 阿里云百炼 · 北京 | 文本 Rerank | 业务空间域名下的 `/api/v1/services/rerank/text-rerank/text-rerank`，采用原生重排适配；[官方重排说明](https://help.aliyun.com/zh/model-studio/rerank) |
| 硅基流动 · 中国区 | Chat、文本 Embedding、Rerank | Chat/Embedding 基地址为 `https://api.siliconflow.cn/v1`；重排完整端点为 `/v1/rerank`；[Chat](https://docs.siliconflow.cn/en/api-reference/chat-completions/chat-completions)、[Embedding](https://docs.siliconflow.cn/en/api-reference/embeddings/create-embeddings)、[Rerank](https://docs.siliconflow.cn/docs/api/rerank-post) |
| 火山方舟 · 北京 | Chat | `https://ark.cn-beijing.volces.com/api/v3`，不是 Coding/Agent Plan 地址；[官方接入说明](https://docs.volcengine.com/docs/82379/2160841?lang=zh) |
| 自定义 | Chat、文本 Embedding、厂商 Rerank | 用户填写公网 HTTPS；Chat/Embedding 填基地址，Rerank 填完整厂商端点。不宣称未知厂商的真实能力已经验证。 |

百炼资料已推荐业务空间专属域名，密钥与地域绑定。首版只预设上述已核实区域，其他区域用自定义连接，不静默换地域或复用密钥。方舟与 DeepSeek 的列表只是本次核实范围，不表示供应商不支持其他能力。

## 表单闭环

1. 选择厂商；能力选项只保留预设范围。自动填连接名称，用户改过名称后保留用户输入。
2. 百炼 Chat/Rerank 补业务空间 ID，其他固定地址无需手填。地址只读；自定义可编辑。
3. 选择厂商、能力或改变业务空间后清空一次性访问密钥。取消、Escape、失败和成功提交均不能保留密钥；不会发送真实模型请求。
4. 保存时状态层再次校验厂商、能力和地址组合，不能篡改固定地址或伪造能力；失败不产生连接。
5. 保存的连接仍是草稿，沿用合成探测 → 验证 → 发布流程。连接只保存非秘密厂商及预设版本、协议和脱敏 origin，不保存输入密钥；租户投影不暴露平台运行地址。

`provider-presets.js` 是这份原型的唯一预设表。Chat/Embedding 继续表达 Spring AI 适配 seam；所有 Rerank 都标为 `VENDOR_POST_PROCESSOR`。百炼 SDK 能发送自定义 `/reranks` 请求，不等于存在统一的 OpenAI/Spring AI 重排标准。

## 边界

不在连接阶段硬编码厂商模型名、向量维度或探测成功结果。现有探测返回的是明确合成候选，不是从厂商取得的真实目录。后续生产包须重新核对接口、地域、供应商合同与 SSRF 防护；此处不是可复制进生产的网络客户端。
