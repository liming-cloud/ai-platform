# ADR-0099：方舟 default 项目的双来源候选发现

## 状态

已接受。用户于 2026-10-19 回复“同意并推进任务”，接受提交 `ff5e7c1a5ceb239ab4cada1c1104e258bb0c2161` 中的 [S1 预置候选补齐提案](../../tasks/WP-0019/design-and-slices.md#s1-方舟-default-预置候选补齐提案已接受)；接受前该节 SHA-256 为 `bbf09fa47236dfd0f1a4461a33ad298dff1010dd0cfac8d98bb6298ab3cef23e`。责任任务 WP-0019／S1，实施基线同该提交。此接受不证明源码、部署、厂商合同、S1 或 M6 通过。

## 背景

现有 `VOLCENGINE_ARK_CONTROL_PLANE` 仅调用 `ListEndpoints`。同一受保护凭据在北京 default 的自定义接入点为 0，预置总数为 2；抽查一项 Running，关联基础模型已开通。三个最小只读查询均 HTTP 200，不能据自定义空列表认定账号无模型或缺权限。事实与完整 IAM 核查限制见 [执行证据](../../tasks/WP-0019/evidence/WP-0019-S1-page-states-and-ark-diagnostics-20261019.md#default-项目与候选来源核查)。

## 决定

1. 在现有方舟扩展中合并 `ListEndpoints` 和 `InnerDescribeModelEndpoints`，两者均使用连接既有区域、`Version=2024-01-01`、显式 `ProjectName=default` 和独立 AK／SK；不新增发现配置、项目选择器、跨项目／区域扫描、公开字段或持久化结构。ADR-0070 的类型、用途和候选不变量继续有效。
2. 两来源分别完整分页，共用现有最多 100 次分页请求、10,000 个候选及单响应 1 MiB 上限。逐次执行出站校验、超时和禁止重定向；任何来源的认证、网络、结构、项目或分页不一致使整次失败，不保留部分成功候选。全部读取成功且合格候选为空时仍保存 `VOLCENGINE_ARK_DISCOVERY_NO_MODELS` 失败事实。不得通过放宽正式非空验收断言获得通过。
3. 自定义来源使用官方 `Id`；预置来源仅接收 default 下 Running 的条目，使用官方 `ModelId`，不能把管理面 `ep-m-<ProjectName>:<ModelId>` 标识当成推理编码。相同模型编码去重，缺失或畸形必需字段失败关闭。其他预置状态不进入本次可确认候选，不推导成真实推理健康。
4. 两来源候选均沿用 `REQUIRES_ADMIN_CONFIRMATION`，类型与能力保持未确认，不从名称、状态或开通事实推断。连接／凭据修订、不可变发现及独立发布继续沿用已有合同；旧发现不回填。`ListModelActivations` 只作为本轮诊断，不增加为生产必需读取；能力和推理 Key 授权仍由 S2 独立验证。
5. 当前及存量 Ark 凭据都需同时具备两个列表 Action 的读取权限；缺少新读取权限时失败关闭，禁止自动授予权限或扩大资源范围。S1 平台／租户权限、TLS／SSRF、独立推理 Key 和秘密脱敏不变。此决定不授权创建云资源、改变云端策略、推理、新修订发布或推进 S2。

## 备选与验证

不选择创建自定义接入点或扩大 IAM 权限：已有来源可读，且本轮禁止云端写入。不选择公共模型目录填充或其他项目扫描：不能证明当前 default 资源。不选择仅在自定义为空时回退预置：新增一条自定义记录会使原预置候选消失，无法保持稳定目录。

先证明“自定义 0、预置非空”的失败用例，再验证双空、两来源合并、来源单侧失败、跨项目、ModelId 转换、去重、分页一致／超限、未知分类、秘密隔离和修订竞态。实施前完成图谱影响分析，按影响执行适配器、模型服务／自动配置及完整后端验证。固定提交构建后端，最新 Compose 仅复验精确新连接的正式发现及只读持久化事实；不重复已通过且未改动的页面矩阵，不提前发布或宣称模型可调用。

原型仍为 OpenDesign Cloud 原项目 `249f81f2-6f1a-46ff-988c-da3cdedb35e7`、已接受 `cce686e4634ca3c523e4c8aeffd95752f1a04c3a`，不修改原型。权威接口参考：[预置接入点列表](https://docs.volcengine.com/docs/ark/inner-describe-model-endpoints-api?lang=zh)、[预置 Model ID 调用](https://docs.volcengine.com/docs/ark/online-inference-standard?lang=en)。
