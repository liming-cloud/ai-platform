# WP-0014 对象存储资源选择器分页

## 结果

对象存储范围登记任务的知识空间选择器改为服务端分页：`/data/object-storage` 规范保存 `knowledgeSpacePage` 与 `knowledgeSpaceId`。页面只读取当前页；跨页或切换知识空间时清除当前数据源、已发布产物和索引状态，不能把已冻结范围用于另一个知识空间。共享分页操作栏不拥有业务状态、读取或权限。

## 红绿与验证

- 红灯：`console-route.test.ts` 要求对象存储深链接可恢复知识空间页码与标识；旧路由漏掉两个字段，实际失败。
- 绿灯：定向路由测试 13 项通过。
- `python3 scripts/quality/frontend_product_guard.py verify && make architecture-guard && cd frontend && npm run lint && npm test && npm run build`：24 项架构门禁测试、80 项前端测试、lint 与生产构建通过；前端产品门禁确认无浏览器累加分页或嵌套 `Dialog`。

## 边界

对象存储范围登记是独立多步骤安全任务，保留专用路由；它既不枚举范围，也不展示对象位置、桶名、对象键、正文或凭据。创建索引时自动选择第一个可用嵌入模型配置仍是独立的后续整改项。真实 HTTPS 浏览器验收继续受正式证书基础设施前置条件约束，不能通过 HTTP 或忽略证书错误替代。
