# WP-0014 全 SPA UI 重构生产构建 profile

- 观察时间：2026-08-18
- 候选摘要：`sha256:4b7929f489518fefaae369f4d201dd9126768839c6e5bfe79677f37a8b9c230b`

## 命令

```bash
cd frontend
npm run build
```

结果：退出码 0；Vite 8.1.5 转换 2180 个模块，构建耗时 579ms。

## 主要产物

- 主入口 `index-CMm7mS96.js`：254.42 kB，gzip 77.60 kB。
- 共享源码包 `src-BS1m-Wsk.js`：302.93 kB，gzip 87.19 kB。
- 主样式 `index-C4afPtnw.css`：24.56 kB，gzip 4.24 kB。
- 最大按路由加载工作区 `ConsoleQueryWorkspace-CFMk2dl0.js`：61.16 kB，gzip 19.61 kB。

本切片没有新增网络请求、轮询、分页聚合或新的第三方运行时依赖。该 profile 证明候选能够按既有分块策略完成生产构建；它不是浏览器交互延迟或 Web Vitals 测量，不扩大为运行时性能结论。
