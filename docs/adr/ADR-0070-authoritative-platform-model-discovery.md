# ADR-0070：平台供应商权威模型发现与管理员能力确认

## 状态

已接受。

## 日期

2026-08-24

## 决策负责人

用户，代表产品、架构、安全、平台模型治理与前端负责人。

## 背景和待消除的矛盾

ADR-0058 与 ADR-0066 已建立“平台连接 → 模型发现 → 能力探测 → 部署 → 目录发布”的平台模型供应链，
但当前只有确定性发现适配器。`OPENAI_COMPATIBLE` 连接会稳定返回
`PLATFORM_MODEL_DISCOVERY_UNSUPPORTED`，使真实供应商无法进入后续探测、部署和目录发布。

现有 `DiscoveredModel` 又要求候选在发现时已确定 `CHAT` 或 `EMBEDDING` 及能力集合。OpenAI 的官方
`GET /v1/models` 只保证返回模型标识及所有者等基础元数据，不能据此可靠判断本平台的模型类型和能力；
火山方舟的 OpenAI-compatible 推理 API Key 与控制面 OpenAPI 的 AK/SK 也具有不同认证和权限语义。
若按模型名称、Endpoint 域名或失败响应猜测能力，会把偶然命名和厂商实现细节固化为发布依据。

## 决策内容

选择方案 A：保留 `ProviderKind.OPENAI_COMPATIBLE`，为平台连接显式冻结发现配置和独立凭据用途；
权威发现只产生供应商声明或待管理员确认的候选，真实能力探测仍是部署发布前的必要步骤。

1. 平台连接新增显式 `DiscoveryProfile`：
   - `OPENAI_MODELS_API`：使用连接的短生命周期推理 API Key 调用受控 Endpoint 下的官方
     `GET /models`。
   - `VOLCENGINE_ARK_CONTROL_PLANE`：使用独立、只写的火山方舟 AK/SK 调用 Ark
     `2024-01-01` 控制面权威列表接口，并按服务端分页读完本次受控候选集合。
   - `NONE`：只允许协议本身没有权威列表能力的受控连接；页面必须明确要求管理员从受控目录继续，
     不能把“不支持发现”伪装为成功。
2. `ProviderKind` 继续只有协议／运行时意义。火山方舟推理保持
   `OPENAI_COMPATIBLE`，不得新增 `VOLCENGINE_ARK` 核心枚举，不得从 Endpoint 域名、区域或模型名称
   推导发现方式。
3. 平台连接的推理 API Key 与 Ark 控制面 AK/SK 是三个独立秘密字段和用途：
   - 推理 API Key 只用于连接探测、OpenAI-compatible 模型列表和后续模型能力调用；
   - Ark Access Key ID 与 Secret Access Key 只用于签名后的 Ark 控制面发现；
   - 公共写入请求只在建立或轮换时接收明文，数据库分别保存加密材料；读取投影只返回是否已配置、
     凭据版本、轮换时间和不可逆指纹，不返回明文、密文、内部引用或签名材料。
4. 权威响应转换为不可变 `PlatformDiscoveredModelCandidate`。候选至少冻结连接标识及修订、发现配置、
   供应商稳定模型标识、受控显示名称、分类状态、声明来源、发现时间和发现修订；不得保存或投影供应商
   原始响应、原始错误、请求签名、Endpoint、秘密或内部追踪标识。
5. 候选分类固定为：
   - `CONFIRMED_BY_PROVIDER`：供应商权威元数据明确且能够无歧义映射到本平台 `CHAT` 或
     `EMBEDDING` 契约；
   - `CONFIRMED_BY_CONTROLLED_MAPPING`：平台版本化受控目录映射明确给出模型类型和可探测能力；
   - `REQUIRES_ADMIN_CONFIRMATION`：元数据不足或映射缺失。此时模型类型和待探测能力保持未确认，
     不得用名称、所有者、Endpoint 或当前产品默认值补齐。
6. 只有 `PLATFORM_ADMINISTRATOR` 可对当前连接修订的待确认候选提交一次受控确认，显式选择
   `CHAT` 或 `EMBEDDING` 以及该类型允许的待探测能力。确认形成新的不可变分类事实；它不是能力验证，
   仍须完成最小真实能力探测，才能建立、验证和发布部署。
7. 连接 Endpoint、发现配置或任一相关凭据轮换都会推进连接修订，并使旧发现、管理员确认和能力探测
   对新部署失效。历史事实保留审计，但不得作为当前候选或发布依据。
8. 供应商网络、鉴权、限流、响应格式和签名失败只转换为平台稳定失败类别和可行动的中文提示。
   HTTP 与 SPA 不得显示供应商原始响应、错误码、异常、内部标识、Endpoint、密钥、签名或追踪标识。
9. OpenAI-compatible 首条实现采用受控 `GET /models` 纵向切片；紧邻切片实现火山方舟 Ark
   AK/SK 签名、分页发现和管理员确认。两条适配器复用同一发现端口和候选不变量，不建立巨型厂商适配器。

## 公共契约与持久化边界

- 平台连接建立／轮换接口显式接收发现配置及该配置所需的只写秘密；缺失、冗余或用途不匹配的秘密失败关闭。
- 候选列表继续使用页码、页大小和总数，默认只返回当前连接修订的候选；管理员确认使用独立命令，
  写后从服务器重读。
- 普通候选投影只返回用户可理解的名称、确认状态、模型类型、可探测能力、发现时间和唯一下一步；
  内部供应商标识只在受控资源选择值中传输，不作为页面可见文本。
- 新的发现配置、候选分类、确认事实和双凭据持久化必须由版本化迁移建立；秘密字段遵守
  ADR-0023、ADR-0060 的 AES-256-GCM、短生命周期和失败回滚边界。

## 主要备选方案及拒绝原因

### 方案 A：显式发现配置、用途分离凭据与待确认候选

接受。它保留现有 OpenAI-compatible 运行时边界，能同时表达 OpenAI `/models` 与 Ark 控制面列表，
并把“发现了什么”和“真实验证了什么”分开。未知能力可被管理员显式确认，不需要猜测。

### 方案 B：新增 `ProviderKind.VOLCENGINE_ARK`

拒绝。它会把供应商品牌扩散到核心领域和公共契约，与 ADR-0024 的
`ProviderKind.OPENAI_COMPATIBLE` 决策冲突；同一推理协议也会出现两套工厂和授权分支。

### 方案 C：按 Endpoint 域名自动选择 Ark 控制面或按模型名称推断能力

拒绝。Endpoint 可经代理或受控网关变化，模型名称也没有稳定能力语义；该方案不可审计，会把错误分类
传播到部署和目录发布。

### 方案 D：丢弃元数据不足的候选，只保留受控映射命中的模型

拒绝。它会把供应商确实可用但目录尚未认识的模型静默隐藏，无法满足管理员确认和供应链恢复任务。

## 权威资料

- [OpenAI Models API](https://platform.openai.com/docs/api-reference/models/list)：`GET /v1/models`
  提供模型基础列表，但不承诺本平台所需的 Chat／Embedding 能力分类。
- [火山方舟 ListEndpoints](https://api.volcengine.com/api-docs/view?action=ListEndpoints&serviceCode=ark&version=2024-01-01)：
  Ark `2024-01-01` 控制面端点列表使用 AK/SK 签名与分页。
- [火山方舟 ListFoundationModels](https://api.volcengine.com/api-explorer?action=ListFoundationModels&groupName=%E7%AE%A1%E7%90%86%E5%9F%BA%E7%A1%80%E6%A8%A1%E5%9E%8B&serviceCode=ark&version=2024-01-01)：
  基础模型权威元数据可作为供应商声明来源，但只有明确元数据或受控映射可以转换为平台类型。

## 后果与验证

- 必须先以同一行为的红绿测试证明：OpenAI-compatible 从权威 `/models` 获取候选；未知名称进入管理员
  确认而不是被猜测或丢弃；原始响应和秘密不进入投影；旧连接修订不能建立部署。
- 火山方舟切片必须覆盖 AK/SK 签名规范、分页、时钟偏差、错误签名、限流、畸形响应、秘密清零、
  脱敏和管理员确认；真实供应商调用只能使用用户明确授权的最小读取接口和成本。
- 完整验收包括定向单元／合同测试、真实 PostgreSQL 原子回滚、同源 HTTP、最新 Compose、Chrome
  1024×768、390×844、真实 200% 缩放、键盘导航和 `prefers-reduced-motion`。
- 接受本 ADR 不授权自由 Endpoint、动态插件、供应商原始诊断展示、工具／MCP、M7、M8 或生产发布。
