# M2 自动验收与人工评审请求

## 决定状态

| 字段 | 值 |
| --- | --- |
| 决定编号 | HITL-0003-M2-REVIEW |
| 决定类型 | 里程碑人工评审 |
| 当前结论 | 待人工评审，尚未批准进入 M3 |
| 评审基线 | `c8e66787d` |
| 自动验收时间 | 2026-07-28 |

## 本次交付范围

- 平台、租户和个人三层受控模型配置可写入数据库密文，读取路径不返回 API Key、密文、nonce 或密钥引用。
- 平台冻结火山方舟模型目录，Chat 使用 `doubao-seed-2-1-turbo-260628`，Embedding 使用 `doubao-embedding-vision-251215`；用户不能新增厂家或任意端点。
- Chat 走标准 `/api/v3/chat/completions`；Embedding 通过 `/api/v3/embeddings/multimodal` 逐条提交文本，校验实际 2048 维向量并保持输入顺序。
- 真实验收完成合成知识空间创建、索引、助手发布和带引用问答；最新查询状态为 `ANSWERED`，引用数为 1。

## 自动验收依据

| 项目 | 结论 | 证据 |
| --- | --- | --- |
| 密钥与授权边界 | 通过 | `WP-0010-encrypted-credential-foundation.md`、`WP-0010-model-configuration-persistence.md`、`WP-0010-usable-model-configuration-lookup.md` |
| 受控运行时与同源绑定 | 通过 | `WP-0010-governed-embedding-runtime.md`、`WP-0010-governed-chat-runtime.md`、`WP-0010-governed-query-runtime.md` |
| 标准方舟协议与真实调用 | 通过 | `WP-0010-volcengine-ark-provider-transition.md`；真实调用证据不包含密钥、正文、回答或向量 |
| 产品经理浏览器验收 | 通过 | `WP-0010-product-manager-browser-review.md`；模型配置创建、启用、密钥不回显、带引用问答和脱敏治理页面均已实际操作 |
| 全量工程门禁 | 通过 | `make project-verify` 与 `make validate` 均通过 |

## 请人工复核

- 确认 API Key 仅以不可回显的数据库密文和受控运行期短生命周期明文使用，且没有提交到仓库。
- 确认普通用户可使用自身、所属租户或平台已授权的模型配置，但不能读取或管理超出权限范围的配置与密钥。
- 确认冻结厂家、端点、模型类型和能力快照的治理方式满足产品要求。
- 确认真实调用只作为研发测试证据，不构成生产发布、质量、成本或 SLA 承诺。
- 确认 M3 至 M8 的覆盖计划保持产品蓝图，并将数据、助手、评测、治理、渠道和生产差距纳入后续退出证据。

人工评审明确“通过 M2，进入 M3”后，才可关闭 `WP-0010`、推进 `currentMilestone`；若需要修改，当前前沿保持不变。
