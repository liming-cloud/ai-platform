# WP-0010：受控模型目录与分层配置领域基线

实现提交：`d8c3b6cb3`（`feat: 建立受控模型配置领域基线`）。

## 范围

本切片固定平台受控目录与平台、租户、个人三层模型配置的领域不变量；不代表数据库持久化、API Key 写入、授权管理、运行时解析或真实模型调用已完成。

## 红绿证据

先新增 `ModelConfigurationTest`：领域类型不存在时编译失败，证明测试先于实现。再新增 `ControlledModelCatalogEntryTest`：受控目录类型与状态不存在时编译失败。

实现后执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am \
  -Dtest=ModelConfigurationTest,ControlledModelCatalogEntryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -pl backend/contexts/model-runtime -am test
git diff --check
```

结果：通过。

## 装配验证

`ModelRuntimeAutoConfigurationTest` 先断言模型配置服务尚未被装配而失败；随后在显式启用的模型目录自动配置中注册该服务，并验证：

- 必须有模型配置仓储和雪花标识生成器才可装配；
- 模型目录关闭时不装配服务；
- 持久化自动配置扫描模型配置 Mapper 时，凭据 Mapper 也必须显式纳入测试桩，避免隐藏的 MyBatis 扫描差异。

## 已验证不变量

- `PLATFORM` 配置不携带租户或成员归属；`TENANT` 配置只能归属一个可信租户；`PERSONAL` 配置必须同时归属可信租户和成员。
- 模型配置只引用 `ControlledModelCatalogEntryId`，不携带用户提交的厂家、端点或模型编码。
- 平台目录条目固定厂家类型、HTTPS 端点和模型编码；禁止用户信息、查询参数和片段，并且只有发布后才可成为可选目录项。
- 模型领域未依赖 Web、安全实现或持久化框架；成员标识通过模型上下文自己的值对象表达。

## 受控目录管理

追加提交 `9c9972448` 建立平台受控目录的创建与发布闭环：

- 目录创建请求的厂家类型只能是平台编译期定义的 `ProviderKind`，普通配置没有厂家或端点字段；
- 只有 `model-platform:write` 可创建草稿、发布目录；
- 发布通过 PostgreSQL 乐观版本更新，真实容器测试验证草稿到发布状态和修订递增；
- 租户管理员和个人用户仍只能引用已发布目录条目，不能管理厂家类型。

## 未完成项

- 加密凭据写入的真实事务回滚。
- 平台、租户和个人配置的脱敏 API、授权/撤销。
- 运行时仅在完成授权、配额和预算检查后解密凭据。
- 火山方舟受控探测和端到端可信问答回归。
