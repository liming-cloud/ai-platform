# WP-0010：受控模型配置选择与索引创建契约

实现提交：`1d720a692`（`feat: 支持受控模型配置选择`）。

## 范围与结果

新增 `GET /api/v1/model-configurations` 页码列表。数据库查询在返回前按当前可信租户与成员过滤个人配置、租户配置和已获活动授权的平台配置；控制台只接收不包含 API Key、密文、nonce、指纹、端点或密钥引用的安全投影。

知识发布控制台改为读取当前主体可见的 `EMBEDDING` 配置，并只将已启用且已配置凭据的配置标识提交给创建索引接口。新索引的 HTTP 契约使用 `embeddingModelConfigurationId`，不再把旧 `ModelDeployment` 标识写入新索引路径。

## 可复现验证

```bash
./mvnw -q -pl backend/contexts/model-runtime -am test \
  -Dtest=ModelConfigurationReadServiceTest,ModelConfigurationReadControllerTest,PostgresModelConfigurationRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false

cd frontend && npm test -- --run apps/console-web/src/api/control-plane-client.test.ts
cd frontend && npm run typecheck
make architecture-guard
make validate
make project-verify
```

结果：全部通过。PostgreSQL 集成测试覆盖个人、租户、已授权平台与未授权平台配置的分页可见性；应用测试覆盖目录模型类型和凭据状态的安全投影；前端测试覆盖新索引请求字段与无秘密配置列表响应。

## 六维自动评审

- 正确性：页面总数和列表使用同一授权谓词，避免跨范围配置或数量泄露。
- 安全：平台配置要求活动授权；响应只投影配置状态和目录模型类型，不携带凭据材料。
- 架构：复杂授权 SQL 置于 MyBatis XML 并登记为 `SECURITY_PROJECTION`，`Page` 类型未离开 infrastructure。
- 性能：SQL 在数据库端完成范围过滤和分页；目录与凭据状态仅在当前页投影。
- 复杂度：复用现有模型配置、授权和凭据状态端口，未增加厂商或密钥抽象。
- 测试质量：覆盖真实 PostgreSQL 授权反例、服务投影、HTTP 控制器、浏览器客户端契约及全仓门禁。

本证据只完成受控模型配置的选择与索引创建入口；M2 的助手双配置绑定、Chat 运行时、真实调用验收和人工评审仍未完成。
