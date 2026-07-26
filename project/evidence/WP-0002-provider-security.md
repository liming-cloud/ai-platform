# WP-0002 供应商密钥与 OpenAI 兼容探测证据

## 证据范围

- 新增只依赖 JDK 的 `platform-security` 模块。
- 密钥引用由裸字符串升级为 `SecretReference` 值对象。
- `SecretValue` 在单次使用和关闭后清零字符数组，字符串表达固定脱敏。
- 默认出站策略拒绝非 HTTPS、用户信息、片段、环回、私网、链路本地、云元数据和非全局地址。
- `ProviderProbeRequest` 只携带密钥引用，并增加最小真实调用所需的模型编码。
- OpenAI 兼容适配器使用 Spring AI 2.0.0 执行最小聊天调用。
- 探测失败只返回稳定分类码，不返回密钥、认证头或原始响应体。
- Spring AI 与 OpenAI 客户端的传递依赖版本分叉已在扩展模块局部收敛，未关闭 Maven Enforcer。

## 红灯证据

```bash
./mvnw -q -pl backend/platform/platform-security -am test
./mvnw -q -pl backend/contexts/model-runtime -am test
./mvnw -q -pl backend/extensions/model-providers/provider-openai-compatible -am test
```

实际失败依次为：

1. 安全值对象、可擦除秘密和出站策略不存在，测试编译失败。
2. 供应商请求仍接受裸字符串且没有模型编码，新构造契约编译失败。
3. Maven Enforcer 先发现 Spring AI 传递依赖不收敛；局部排除旧版后，测试因 OpenAI 兼容探测器不存在而按预期失败。

## 绿灯证据

```bash
./mvnw -q -pl backend/platform/platform-security -am test
./mvnw -q -pl backend/contexts/model-runtime -am test
./mvnw -q -pl backend/extensions/model-providers/provider-deterministic -am test
./mvnw -q -pl backend/extensions/model-providers/provider-openai-compatible -am test
make backend-test
```

验证覆盖：

- 密钥引用语法和值语义。
- 临时密钥副本清零、关闭后禁止重用和字符串脱敏。
- 公网 HTTPS 地址放行与七类危险地址拒绝。
- 领域请求拒绝空白模型编码。
- Spring AI 真实 HTTP 协议请求、认证头和标准响应解析。
- 出站策略先于密钥解析执行。
- 认证失败即使返回敏感文本也只产生稳定错误码。
- 新模块架构依赖规则和全后端回归。

## 六维质量判断

- 正确性：红绿命令和实际协议测试通过。
- 复杂度：安全契约与厂商扩展各自保持单一职责，没有引入厂商万能适配器。
- 冗余：确定性扩展与 OpenAI 兼容扩展只共享模型运行时公共契约，无重复业务编排。
- 性能：连接探测位于低频控制面，单次创建客户端可接受；高频模型运行时缓存留给后续工作包。
- 架构：安全模块只依赖 JDK，厂商扩展不能依赖控制器、持久化和无关框架，均由 ArchUnit 验证。
- 测试质量：外部 HTTP 服务被替代，但安全策略、密钥对象和 Spring AI 生产适配器均使用真实实现。

## 边界

- 当前适配器尚未装配到控制面 HTTP 接口。
- 应用层 DNS 校验仍存在解析与连接之间的重绑定窗口，已登记为 R-011；生产前必须由受控出站代理、网络层策略或连接地址固定机制闭合。
- Spring AI 接受字符串 API Key，调用期间会产生不可主动擦除的短生命周期字符串；平台不持久化、不记录且不复用该字符串。
