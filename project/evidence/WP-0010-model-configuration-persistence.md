# WP-0010：模型配置与加密凭据数据库基线

实现提交：`928e568d2`（`feat: 新增模型配置加密凭据迁移`）。

追加实现提交：`b68cfb6f1`（`feat: 持久化加密模型凭据`）。

追加实现提交：`71e484790`（`feat: 接入模型配置PostgreSQL仓储`）。

追加实现提交：`6b64ccf71`（`feat: 原子写入模型配置凭据`）。

追加实现提交：`fa1a2b392`（`feat: 装配本地研发模型密钥提供者`）。

追加实现提交：`b38e8b22c`（`feat: 新增安全模型配置创建接口`）。

追加实现提交：`2997db59d`（`feat: 支持模型配置凭据受控轮换`）。

追加实现提交：`a21a80a9b`（`feat: 新增模型配置授权与撤销`）。

追加实现提交：`376ccb533`（`feat: 支持模型配置显式启用`）。

## 范围与结果

新增受控模型目录、平台/租户/个人模型配置和加密凭据三张 PostgreSQL 事实表。凭据表只保存 AES-GCM 信封材料、主密钥版本、不可逆指纹与轮换时间，不包含 `api_key` 字段。

## 红绿证据

`ModelConfigurationSchemaMigrationTest` 首次执行时因受控目录表不存在失败。添加 Flyway 迁移后，真实 PostgreSQL 容器中验证以下行为：

- 个人配置缺少成员所有者被数据库拒绝；
- 受控目录端点携带查询参数被数据库拒绝；
- 合法个人配置可关联密文凭据记录；
- `information_schema` 中凭据表不存在 `api_key` 列。

执行命令：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am \
  -Dtest=ModelConfigurationSchemaMigrationTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -pl backend/contexts/model-runtime -am test
git diff --check
```

结果：通过。

## 凭据持久化适配器

`PostgresEncryptedModelCredentialStoreTest` 在真实 PostgreSQL 中写入 API Key 后确认：

- `ciphertext` 与输入明文不相同；
- 密钥只能经 `useCredential` 的短生命周期 `SecretValue` 回调获取；
- 信封密文、nonce、封装数据密钥仅在持久化 Mapper 与适配器中流转；
- 指纹计算不创建不可擦除的临时明文字符串。

## 受控配置仓储

`PostgresModelConfigurationRepositoryTest` 在真实 PostgreSQL 中验证：

- 只能从已迁移的受控目录表读取目录条目；
- 目录条目重建保留厂商类型、HTTPS 端点、模型编码和发布状态；
- 配置保存通过 MyBatis XML 写入，租户与个人所有者字段由领域对象决定；
- 持久化自动配置显式提供 `ModelConfigurationRepository`，不改变 M1 的 `ProviderConnection` 装配。

## 原子凭据写入

`ModelConfigurationCredentialService` 通过应用层端口将配置创建和凭据轮换置于同一事务中：

- API Key 只作为 `SecretValue` 传入写入器，不进入聚合、日志或返回对象；
- 任一写入失败时由 `SpringModelConfigurationTransaction` 回滚配置与密文；
- `PostgresEncryptedModelCredentialStore` 是凭据写入端口的唯一 PostgreSQL 实现；
- 未提供 `SecretEncryptionKeyProvider` 时不注册凭据写入服务，避免降级为明文或不可加密写入。

本地研发如需启用加密写入，必须同时显式设置 `foundation.model-credentials.local-development-key-version` 和对应版本的环境变量；生产环境必须提供覆盖该接口的 KMS 或 Vault 实现。本证据不记录任何环境变量内容。

## 脱敏写入与轮换接口

`ModelConfigurationControllerTest` 与 `ModelConfigurationServiceTest` 验证：

- `POST /api/v1/model-configurations` 仅在可用密钥提供者和事务写入器装配后开放；
- 入站 `apiKey` 转为 `SecretValue` 后传给应用服务并在请求结束时关闭，响应不含密钥材料；
- `POST /api/v1/model-configurations/{id}/credential` 轮换前从 PostgreSQL 重建配置，平台、租户和个人配置分别按照可信上下文的权限、租户与成员归属验证；
- 跨成员个人配置统一返回“模型配置不存在”，不暴露资源是否真实存在；
- 数据库读取路径恢复所有权、状态和修订，但不读取或映射任何凭据材料。

## 使用授权与撤销

新增 `model_configuration_access_grant` 事实表与授权服务，测试证明：

- 平台管理员可以向指定租户或成员授予平台配置的使用权；
- 租户管理员只能授予自身租户的租户配置；个人配置不可转授；
- 授权查询只读取活动授权状态，不触及凭据表；
- 撤销以乐观版本更新为 `REVOKED`，真实 PostgreSQL 测试确认撤销后活动授权查询立刻为否；
- 授权与撤销接口不包含 API Key、密文或密钥引用。

## 显式启用门禁

模型配置创建后保持 `DRAFT`。只有归属范围内的管理者可以调用启用接口使其变为 `ACTIVE`；`ModelConfigurationAccessService.mayUse` 对非活动配置一律返回否。真实 PostgreSQL 测试验证状态与修订通过乐观版本更新保存。

## 未完成项

迁移、仓储、创建、轮换、授权撤销与配置启用尚不是完整产品接口：脱敏读取及授权后的真实模型调用仍待实现。
