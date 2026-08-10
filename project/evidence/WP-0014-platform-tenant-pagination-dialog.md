# WP-0014 平台租户分页与受控操作

## 结果

平台租户生命周期工作区落实 ADR-0049、ADR-0059：`/platform/tenants?page=N` 是唯一可恢复页码位置；每次只读取当前服务端页的租户生命周期摘要；建立租户和激活／挂起／恢复均在单一 Fluent UI `Dialog` 中二次确认，成功后重新读取服务器事实。

## 红绿与验证

- 红灯：`console-route.test.ts` 新增平台页码往返断言，旧路由遗漏 `page`，实际失败。
- 绿灯：默认、解析、格式化和无权深链接均保留规范页码；定向路由测试 10 项通过。
- `cd frontend && npm run typecheck && npm run lint && npm test && npm run build`：77 项前端测试、lint、生产构建通过。
- `python3 scripts/quality/frontend_product_guard.py verify`：通过；无浏览器累加分页或嵌套 `Dialog`。

## 安全与范围

页面不新增 HTTP 接口、权限或主体管理能力；仍只使用已存在的 `/platform/tenants` 分页读取和生命周期命令。建立表单不展示成员详情或凭据，状态迁移显示目标状态并在失败时不返回成功。真实 HTTPS 浏览器操作仍受已登记的本地证书前置条件限制，不能以 HTTP 或证书绕过替代。
