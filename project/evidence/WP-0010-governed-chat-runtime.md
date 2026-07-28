# WP-0010：受控对话短生命周期运行时

实现提交：`9608d15fa`（`feat: 增加受控对话短生命周期运行时`）。

## 范围与结果

新增 `GovernedChatRuntime`。每次对话调用均先按当前可信主体重新验证受控模型
配置、配置修订、目录条目、目录修订、模型类型和输出上限，再通过既有
`ModelConfigurationCredentialUser` 在一次调用范围内解密凭据、构造 Chat 模型并
执行请求。

`ChatModelFactory` 增加与嵌入工厂同样的短生命周期凭据入口。本地确定性厂商可
继续使用默认实现；需要 API Key 的厂商只能消费该调用内的 `SecretValue`。受控
Chat 运行时不缓存客户端，不暴露 API Key、密文、nonce、凭据引用或端点。

## 红绿验证

先执行以下命令，预期因 Chat 运行时构造器和凭据工厂尚未支持受控对话而编译
失败：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am test \
  -Dtest=ModelConfigurationRuntimeServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

实现后执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am test \
  -Dtest=ModelConfigurationRuntimeServiceTest,ModelRuntimeAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false

make architecture-guard
make validate
```

结果：全部通过。测试覆盖 Chat 规范冻结、短生命周期凭据建模、目录能力输出上限
以及配置修订变化后在调用工厂前失败关闭。

## 六维自动评审

- 正确性：Chat 请求的配置标识、修订和最大输出均与冻结快照匹配。
- 安全：凭据只传递给一次模型构造，不进入缓存或响应。
- 架构：新增最小跨上下文门面，复用既有配置查询、目录和凭据端口。
- 性能：仅做一次精确配置查询和目录读取，不保留含秘密的对象。
- 复杂度：Chat 与 Embedding 共用同一服务生命周期，未引入第二套凭据机制。
- 测试质量：实际红灯、Chat 成功路径、失效修订反例、自动配置和全仓门禁均已执行。

本证据仅完成受控 Chat 运行时原语。查询运行时尚未在配额占用后接入受控 Chat
与 Embedding 复核，真实阿里云百炼调用及 M2 人工评审均未完成；不得进入 M3。
