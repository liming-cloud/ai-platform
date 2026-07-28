# WP-0010：受控嵌入短生命周期运行时

实现提交：`ed5f41c5f`（`feat: 增加受控嵌入短生命周期运行时`）。

## 范围与结果

新增 `GovernedEmbeddingRuntime`。它在一次嵌入调用内复核当前主体授权、冻结模型配置快照、受控目录发布状态与目录修订，并使用加密凭据端口执行模型调用；调用结束后不缓存包含该 API Key 的模型对象。

`OPENAI_COMPATIBLE` 嵌入工厂新增短生命周期凭据入口。受控配置调用不会走通用 `SecretResolver` 或构造可长期解析的凭据引用；M1 已发布部署路径保持原有行为。

## 红绿与回归证据

先添加 `ModelConfigurationRuntimeServiceTest` 和凭据使用端口断言，执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am test \
  -Dtest=ModelConfigurationRuntimeServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：预期红灯，编译器报告运行时服务、短生命周期凭据端口和工厂重载尚不存在。

实现后执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am test \
  -Dtest=ModelConfigurationRuntimeServiceTest,ModelRuntimeAutoConfigurationTest,PostgresEncryptedModelCredentialStoreTest \
  -Dsurefire.failIfNoSpecifiedTests=false
make validate
git diff --check
```

结果：全部通过。测试覆盖冻结快照不一致拒绝、目录能力构造、短生命周期凭据回调和自动装配；记录型工厂在误走长期密钥引用路径时会直接失败。

## 六维自动评审

- 正确性：运行时重新比较配置快照、目录修订、模型类型与请求嵌入引用；不一致即拒绝。
- 复杂度与冗余：复用已有目录、授权、凭据与厂商工厂端口，仅增加受控嵌入这一最小调用 seam。
- 性能：受控配置不进入模型对象缓存；每次调用增加定长目录与授权读取，换取撤销立即生效和密钥生命周期边界。
- 架构：跨上下文只暴露 `GovernedEmbeddingRuntime`；凭据端口不返回明文、密文、端点或厂商实现。
- 安全：通用密钥引用不会用于受控配置的 `OPENAI_COMPATIBLE` 嵌入调用，解密秘密由回调自动关闭。
- 测试质量：已记录实际红灯、模块与真实 PostgreSQL 回归、自动装配回归和全仓治理验证。

本证据只完成受控嵌入运行时；索引版本持久化配置快照、助手双配置绑定、对话短生命周期运行时和真实调用验收仍未完成。
