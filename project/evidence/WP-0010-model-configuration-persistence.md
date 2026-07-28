# WP-0010：模型配置与加密凭据数据库基线

实现提交：`928e568d2`（`feat: 新增模型配置加密凭据迁移`）。

追加实现提交：`b68cfb6f1`（`feat: 持久化加密模型凭据`）。

追加实现提交：`71e484790`（`feat: 接入模型配置PostgreSQL仓储`）。

追加实现提交：`6b64ccf71`（`feat: 原子写入模型配置凭据`）。

追加实现提交：`fa1a2b392`（`feat: 装配本地研发模型密钥提供者`）。

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

## 未完成项

迁移、仓储与原子写入尚不是完整产品接口：受控密钥提供者装配、脱敏读取 API、凭据轮换、授权撤销和真实模型调用仍待实现。
