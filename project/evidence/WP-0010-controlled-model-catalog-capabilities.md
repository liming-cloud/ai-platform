# WP-0010：受控模型目录能力快照

实现提交：`e3b89585d`（`feat: 冻结受控模型目录能力快照`）。

架构收口提交：`0ae773420`（`feat: 收敛模型配置持久化映射边界`）。

## 范围与结果

受控模型目录条目现在冻结且持久化一种模型类型对应的运行时能力：上下文窗口，以及对话输出上限或嵌入维度。能力不能从厂家类型或模型编码推断，只有具备 `model-platform:write` 权限的平台管理入口可以在创建目录条目时写入。

目录发布只改变状态与修订，保留原始能力快照。配置方仍只能选择已发布目录条目，不能创建厂家类型、端点或能力。

## 安全与迁移边界

- `ModelRuntimeCapabilities.requireFor` 在领域层拒绝模型类型与能力混用；数据库约束重复保证 `CHAT` 与 `EMBEDDING` 的完整性。
- 新增迁移 `V202607280009__add_controlled_model_catalog_capabilities.sql`。既有行可以保持三列均空，以避免迁移过程虚构能力；仓储读取此类行立即失效关闭，并要求平台重新发布。
- 新能力字段仅描述模型限制，不包含 API Key、密文、nonce、密钥引用或厂商专属参数，未扩展凭据数据流。
- 为通过已存在的架构门禁，目录、配置、授权和凭据读取的普通单表操作已迁至 MyBatis-Plus；唯一保留的 XML 是 PostgreSQL 原子密文轮换和只返回存在性的凭据状态安全投影。

## 红绿与回归证据

先将 `ControlledModelCatalogEntryTest` 改为传入能力快照，执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am \
  -Dtest=ControlledModelCatalogEntryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
```

结果：预期红灯，编译器报告目录 `create` 尚未接受 `ModelRuntimeCapabilities`。

实现后执行：

```bash
./mvnw -q -pl backend/contexts/model-runtime -am \
  -Dtest=ControlledModelCatalogEntryTest,ControlledModelCatalogServiceTest,ModelConfigurationServiceTest,PostgresModelConfigurationRepositoryTest \
  -Dsurefire.failIfNoSpecifiedTests=false test
./mvnw -q -pl backend/contexts/model-runtime -am test
git diff --check
make validate
```

结果：全部通过。真实 PostgreSQL 测试覆盖对话与嵌入能力的写入、读回与发布保留，并验证旧目录行缺少能力快照时不能被重建和使用；后端架构门禁确认普通 XML SQL 为零。

## 六维自动评审

- 正确性：领域不变量、服务权限、HTTP 写入契约和 MyBatis 映射均携带同一不可变快照。
- 复杂度与冗余：复用已有 `ModelRuntimeCapabilities` 值对象，没有新增抽象层或第二套校验规则。
- 性能：读取仅增加三个定长标量列，不增加外部调用、循环或额外查询。
- 架构：依赖仍保持 `interfaces -> application -> domain` 与 `infrastructure -> application/domain`；供应商能力未进入助手或查询领域。
- 测试质量：领域反例、平台权限、配置回归、真实 PostgreSQL 迁移、写回与旧行失效关闭均已覆盖。

本证据只完成 M2 的目录能力快照切片；助手双配置绑定、受控真实调用和 M2 人工评审仍未完成。
