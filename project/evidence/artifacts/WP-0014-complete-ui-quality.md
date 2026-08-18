# WP-0014 全 SPA UI 重构质量门禁

- 观察时间：2026-08-18
- 工作包：`WP-0014`
- 基线：`12ad62d46d531f9c794ff49c9443861f8b46ea14`
- 候选：当前工作区的全 SPA 共享视觉基线纵向切片

## 已观察命令

```bash
make validate
```

结果：退出码 0。

- 项目治理与 `make project-verify` 内嵌治理检查通过。
- 27 项架构／产品门禁与后端架构门禁通过。
- 后端 Maven 全反应堆通过，测试使用真实 Testcontainers PostgreSQL／pgvector、Redis 和 MinIO。
- 前端 typecheck、lint、26 个测试文件共 128 项测试和 Vite 生产构建通过。
- 演示评测、真实模型验收、恢复、容量和交付质量脚本通过。

## 定向红绿证据

- 共享语义令牌、壳层尺寸、Fluent `TabList` 与中文角色标签：红灯 2 个文件／4 项失败，绿灯 2 个文件／4 项通过。
- 三种 `ProductShell` 分支登出回调：红灯 3 项中 1 项失败，修复后 3 项通过。

## 结论边界

自动质量门禁支持源码实现与构建结论，不支持人工 UI 接受。Mac 仍锁定；1024×768、390×844、真实 200% 缩放、`prefers-reduced-motion` 和登录后完整角色主流程均保持待验证。
