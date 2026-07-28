# WP-0010：受控模型配置控制台

实现提交：`e587fb823b3f3dbfbeadd4b3b3fbd71a6a3242ef`（`feat: 补齐受控模型配置控制台`）。

## 范围与结果

新增已发布受控模型目录的页码读取接口，以及控制台“模型”工作区。成员只能从平台已发布目录选择模型，再按既有授权边界创建个人、租户或平台配置；厂家类型、端点与模型能力不由控制台输入。

控制台的 API Key 输入使用非受控密码表单字段：创建和轮换操作从本次表单读取后立即重置字段，只发送到既有加密写入接口。列表、创建和轮换响应只展示配置状态和“凭据已配置”事实，不展示 API Key、密文、nonce、指纹或密钥引用。

## 红绿验证

先新增浏览器客户端契约测试，调用尚未实现的 `listControlledModelCatalog` 与 `createModelConfiguration`：

```bash
cd frontend && npm run test -- --run apps/console-web/src/api/control-plane-client.test.ts
```

红灯：`client.listControlledModelCatalog is not a function`，证明已发布目录读取与安全写入客户端尚未实现。

实现后执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am test \
  -Dtest=ControlledModelCatalogServiceTest,ControlledModelCatalogControllerTest,PostgresModelConfigurationRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false

cd frontend && npm run typecheck
cd frontend && npm run test -- --run apps/console-web/src/api/control-plane-client.test.ts
cd frontend && npm run lint
cd frontend && npm run build
make validate
```

结果：全部通过。PostgreSQL 集成测试验证草稿目录不进入已发布分页；服务和 HTTP 测试验证安全目录投影；浏览器客户端测试验证 API Key 仅出现在写请求，不出现在响应对象。全量 `make validate` 同时通过治理、架构门禁和后端测试。

## 六维自动评审

- 正确性：目录读取只筛选 `PUBLISHED`，控制台以页码读取目录和可见配置，并可继续加载后续页。
- 安全：目录读取不包含凭据；创建、启用和轮换复用已有权限与加密事务，浏览器不把 API Key 放入状态或列表。
- 架构：页码契约在应用层返回，MyBatis-Plus 查询仍限制在 infrastructure；厂家类型继续由受控目录拥有。
- 性能：目录和配置均在数据库端分页，控制台按需追加下一页。
- 复杂度：复用既有 `ModelConfigurationController`、凭据服务和 Fluent UI 组件，未增加新的密钥或厂商抽象。
- 测试质量：覆盖红绿客户端契约、服务、HTTP 控制器、真实 PostgreSQL、类型检查、静态检查、前端生产构建和全量门禁。

本证据不代表 M2 完成：真实模型 Chat 与 Embedding 调用及其脱敏验收报告仍未产生。M2 完成后必须先记录人工评审通过，才可进入 M3。
