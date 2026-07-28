# WP-0010：加密模型凭据安全基座

## 范围

本证据仅覆盖 ADR-0023 所要求的加密凭据基础能力，不代表模型配置 HTTP 契约、数据库迁移、授权闭环或真实火山方舟调用已经完成。

实现提交：`2bba83074`（`feat: 新增加密模型凭据安全基座`）。

## 红绿测试

先增加 `AesGcmSecretCipherTest`，执行：

```bash
./mvnw -q -pl backend/platform/platform-security/platform-security-core -am -Dtest=AesGcmSecretCipherTest -Dsurefire.failIfNoSpecifiedTests=false test
```

红灯：缺少 `SecretEncryptionKeyProvider` 与活动主密钥契约，测试编译失败；失败原因与新增行为一致，未输出任何密钥材料。

再增加 `EnvironmentSecretEncryptionKeyProviderTest`，执行：

```bash
./mvnw -q -pl backend/platform/platform-security/platform-security-core -am -Dtest=EnvironmentSecretEncryptionKeyProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
```

红灯：缺少本地研发环境主密钥提供者，测试编译失败；未输出任何环境变量值。

实现后执行：

```bash
./mvnw -q -pl backend/platform/platform-security/platform-security-core -am -Dtest=AesGcmSecretCipherTest,EnvironmentSecretEncryptionKeyProviderTest -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -pl backend/platform/platform-security/platform-security-core -am test
git diff --check
```

结果：通过。日志只有 SLF4J 无绑定警告，不含凭据内容。

## 已验证安全边界

- 每次写入生成新的 32 字节数据密钥；数据密钥和 API Key 分别以 AES-256-GCM 加密，主密钥版本与两组 nonce、封装数据密钥及密文可供后续持久化适配器保存。
- API Key 与数据密钥仅在 `SecretValue` 临时副本和字节数组中使用，调用结束后主动清零。
- 篡改 API Key 密文或数据密钥封装材料均会失败关闭。
- 本地研发读取版本化环境变量 `FOUNDATION_SECRET_ENCRYPTION_KEY_<版本>`；主密钥须为 Base64 编码的 32 字节值。生产环境仍必须提供 KMS 或 Vault 适配器。

## 六维评审

| 维度 | 结论 | 依据 |
| --- | --- | --- |
| 正确性 | 通过 | 往返解密、主密钥版本解析、两类篡改失败测试通过。 |
| 复杂度 | 通过 | 仅新增信封加密、主密钥端口和本地环境实现，未修改 M1 供应商连接链路。 |
| 冗余 | 通过 | 加密与解密共用单一 AES-GCM 辅助方法，无并行密钥处理路径。 |
| 性能 | 通过 | 单次常数大小对称加密，不引入远程调用或缓存。 |
| 架构 | 通过 | 安全实现位于 `platform-security`，业务数据库和模型领域尚未感知主密钥来源。 |
| 测试质量 | 通过 | 记录两次实际红灯和模块绿灯；覆盖往返、两类篡改、重加密和环境缺失反例。 |

## 未完成项

- 加密凭据记录的真实 PostgreSQL 迁移与事务回滚。
- 平台受控厂家目录、平台/租户/个人配置及授权撤销。
- 安全只读 API 契约和审计扇出验证。
- 受控真实模型调用与研发验收报告。
