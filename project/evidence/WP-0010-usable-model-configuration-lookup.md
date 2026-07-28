# WP-0010：可用模型配置安全查询门面

实现提交：`c120cd256`（`feat: 增加可用模型配置安全查询门面`）。

## 范围与结果

新增 `UsableModelConfigurationLookup` 跨上下文门面。它只返回模型配置标识、配置修订、受控目录标识和修订、模型类型与冻结能力；不返回 API Key、密文、nonce、端点、厂家类型或任何可调用凭据。

门面在投影前同时验证模型配置已启用、当前请求主体有使用权、加密凭据已配置、目录条目已发布且模型类型匹配。任何缺失均返回不可用，调用方不得据此回退到其他配置或旧部署。

## 红绿与回归证据

先添加 `UsableModelConfigurationLookupServiceTest`，执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am test \
  -Dtest=UsableModelConfigurationLookupServiceTest \
  -Dsurefire.failIfNoSpecifiedTests=false
```

结果：预期红灯，测试编译报告 `UsableModelConfigurationLookupService` 尚不存在。

实现后执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am test \
  -Dtest=UsableModelConfigurationLookupServiceTest,ModelRuntimeAutoConfigurationTest \
  -Dsurefire.failIfNoSpecifiedTests=false
make validate
git diff --check
```

结果：全部通过。自动配置测试验证凭据状态端口存在时注册安全查询门面；模块测试覆盖个人配置跨成员拒绝、未配置凭据、未发布目录和模型类型不匹配的失败关闭。

## 六维自动评审

- 正确性：配置、授权、凭据、目录状态和模型类型五项事实缺一不可；快照构造再次验证模型能力与类型一致。
- 复杂度与冗余：复用现有配置仓储、授权服务和凭据状态端口，只新增一个跨上下文查询 seam。
- 性能：仅执行配置、目录和凭据状态的定长读取，不解密凭据、不进行外部调用或向量计算。
- 架构：门面位于 `model-runtime` 应用层，依赖方向保持为 `application -> domain`；厂家和密钥细节未泄漏给下游上下文。
- 安全：访问检查先于凭据状态投影，返回值不包含秘密材料；授权撤销仍由既有活动授权查询即时生效。
- 测试质量：记录了实际红灯、同一测试转绿、自动装配回归和全仓架构治理验证。

本证据只完成索引和助手迁移所需的安全配置查询 seam；索引受控嵌入配置冻结、运行时真实调用、助手双配置绑定及 M2 人工评审尚未完成。
