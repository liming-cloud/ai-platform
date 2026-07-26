# WP-0002 限界上下文模块边界证据

## 证据范围

M1 建立六个 Maven 限界上下文：

1. `organization-access`
2. `knowledge-data`
3. `model-runtime`
4. `assistant-delivery`
5. `trusted-query`
6. `evaluation-operations`

每个模块具有独立 POM、与 ADR-0009 一致的 Java 根包和边界说明。`profile-consent` 按目录设计保持在 M7，不为追求目录完整度提前创建。

## 红灯证据

新增治理测试后执行：

```bash
python3 -m unittest scripts.quality.test_project_governance.ProjectGovernanceTest.test_m1_requires_planned_context_modules
```

测试因当前 M1 缺少规划模块而失败，证明此前目录设计没有可执行门禁。

## 绿灯证据

```bash
make project-verify
make backend-test
```

结果：

- 7 项治理测试通过。
- M1 六个上下文的父 POM 注册、模块 POM 和根包边界检查通过。
- Maven 全后端清理测试通过。
- 目录设计中的 `com.example` 示例已替换为批准的 `com.aiplatform` 命名空间。

## 边界

该证据只确认物理模块和命名边界，不表示每个上下文已有业务功能。后续只能通过对应纵向工作包向模块内增加领域、应用和适配器代码，禁止用空目录伪造完成度。
