# WP-0010：模型配置与加密凭据数据库基线

实现提交：`928e568d2`（`feat: 新增模型配置加密凭据迁移`）。

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

## 未完成项

迁移不是产品接口：受控仓储、密文写入与轮换、配置权限、脱敏读取 API、授权撤销和真实模型调用仍待实现。
