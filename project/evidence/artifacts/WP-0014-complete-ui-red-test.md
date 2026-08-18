# WP-0014 全 SPA UI 重构红灯

命令：

```bash
cd frontend
npm test -- --run packages/design-system/src/shell/ProductShell.ui.test.ts apps/console-web/src/ConsoleApp.ui.test.ts
```

结果：退出码为 1，2 个测试文件、4 项测试全部按预期失败。

- `ConsoleApp.ui.test.ts`：旧实现缺少 Fluent `TabList` 任务导航和中文会话角色标签。
- `ProductShell.ui.test.ts`：旧实现缺少共享语义令牌、无渐变根背景及 232px/80px/56px 壳层尺寸。
