# WP-0014 受治理文件资源选择器分页

## 结果

受治理文件任务的知识空间与文件来源选择器改为服务端分页：`/data/files` 规范保存 `knowledgeSpacePage`、`knowledgeSpaceId` 和 `sourcePage`。页面仅渲染当前页；翻页时清除跨页来源状态；登记成功后重读服务器页或回到第一页，不再将本地对象插入资源集合。

## 红绿与验证

- 红灯：`console-route.test.ts` 要求含页码和知识空间标识的深链接往返，旧路由实际缺失全部字段而失败。
- 绿灯：11 项路由测试通过；`GovernedFileDataWorkspace.test.ts` 与路由定向回归共 14 项通过。
- `cd frontend && npm run typecheck && npm run lint && npm test && npm run build`：78 项前端测试、lint 与生产构建通过。
- `make architecture-guard`：24 项架构门禁测试通过，前端产品门禁确认无累加分页或嵌套 Dialog。

## 边界

本任务本身是独立多步骤发布流程，保留在专用路由而非塞进模态框。索引版本轮询是明确任务状态协议，不伪装成普通目录分页。自动选择嵌入模型配置仍须独立改造成受控选择任务；本切片不把该遗留语义误报为已完成。真实 TLS 浏览器验收继续遵守已登记的证书前置条件。
