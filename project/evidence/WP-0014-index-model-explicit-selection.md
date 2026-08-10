# WP-0014 索引模型显式选择

## 结果

文件、结构化数据集和对象存储三个索引任务不再从浏览器当前页隐式选择第一个可用嵌入模型。每个任务路由分别保存 `embeddingModelConfigurationPage` 与 `embeddingModelConfigurationId`；共享选择器只读取当前服务端模型配置页，且只呈现已启用、已配置凭据的 `EMBEDDING` 摘要。没有明确选择时，创建索引被前端拒绝；直接 URL 的伪造或过期配置仍由既有服务端 `createIndexVersion` 授权和状态校验失败关闭。

## 红绿与验证

- 红灯：三个数据任务的路由深链接要求恢复模型配置页码与选择标识，旧路由遗漏字段，3 项断言实际失败。
- 绿灯：13 项路由回归通过；类型检查证明三个任务及导航入口均提供必填页码。
- `cd frontend && npm run typecheck && npm run lint && npm test`：80 项前端测试、类型检查与 lint 通过。
- `python3 scripts/quality/frontend_product_guard.py verify && make architecture-guard && cd frontend && npm run build`：24 项架构门禁测试、前端产品门禁和生产构建通过。

## 边界

选择器不持有权限、会话、索引创建或模型配置最终有效性；模型读取失败会回传给父任务，沿用既有会话失效清理。模型配置是复杂数据生命周期流程中的按需选择区域，不是全量目录；每次只读取一页，翻页会清除选择。真实 HTTPS 浏览器验收仍须等待受信任部署证书，不能以 HTTP 或证书绕过替代。
