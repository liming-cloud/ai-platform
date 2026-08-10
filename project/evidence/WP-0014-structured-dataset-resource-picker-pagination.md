# WP-0014 结构化数据集资源选择器分页

## 结果

结构化数据集登记任务的知识空间选择器改为服务端分页：`/data/datasets` 规范保存 `knowledgeSpacePage` 与 `knowledgeSpaceId`。页面只读取并展示当前页；切换知识空间或翻页时清除尚未完成的数据源、发布产物与索引状态，避免旧空间结果被继续操作。文件任务与本任务共用最小分页操作栏，但数据读取、权限和路由仍由各自任务拥有。

## 红绿与验证

- 红灯：`console-route.test.ts` 要求含知识空间页码与标识的结构化数据集深链接可往返；旧路由遗漏两个字段而失败。
- 绿灯：定向路由测试 12 项通过。
- `cd frontend && npm run typecheck`：类型检查通过。
- `python3 scripts/quality/frontend_product_guard.py verify && make architecture-guard && cd frontend && npm run lint && npm test && npm run build`：24 项架构门禁测试、79 项前端测试、lint 与生产构建通过；前端产品门禁确认无浏览器累加分页或嵌套 `Dialog`。

## 边界

结构化数据集登记是独立多步骤任务，保留专用路由，不应压缩进模态框。当前建立索引时仍自动选择第一个可用嵌入模型配置；该模型选择语义尚未整改为受控选择器，必须在后续独立切片处理。真实 HTTPS 浏览器验收继续遵守已登记的正式证书前置条件，不能以 HTTP 或忽略证书错误替代。
