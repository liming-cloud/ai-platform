# ADR-0048 本地 HTTPS 验收延期记录

## 决定

2026-08-04，产品负责人确认：本地研发阶段不安装 macOS System keychain 的临时 CA，不以本机浏览器
信任根作为研发基础设施。该决定只延期真实浏览器 HTTPS cookie 验收，不改变 ADR-0048 的安全契约。

## 已验证事实

- `deploy/compose/compose.browser-acceptance.yml` 与 HTTPS Nginx 配置已提供仅本机的覆盖层；
  `python3 -m unittest scripts.quality.test_browser_session_deployment` 通过。
- 覆盖层以临时本地证书启动后，`curl --insecure https://localhost:13443/` 返回 `200`；该命令仅验证
  受控本机网关连通性，不验证浏览器信任或 cookie 安全属性。
- 内置浏览器与 Chrome 都拒绝仅安装到当前用户钥匙串的本地根证书。验收未绕过证书错误，随后已恢复
  常规 Docker 部署。
- `make validate` 在本次浏览器会话代码实现后通过；其中包含项目治理、架构、后端、前端、演示、真实模型、
  恢复、容量和交付门禁。

## 保持的安全边界

- 不安装 macOS System keychain CA，不忽略浏览器证书错误，不使用非安全 cookie，也不将 token 或口令
  写入前端存储、文档、日志或版本库。
- 临时本地 CA、私钥和本地演示口令位于被忽略的 `deploy/.local/`，私钥与口令权限均为 `0600`；本文不
  记录其内容。

## 后续退出条件

在拥有正式 HTTPS 证书基础设施的部署环境中，以真实浏览器重新执行：同源两页签登录、平台管理员生命周期
任务、无写权限运营主体直达拒绝、`Secure`／`HttpOnly`／`SameSite` cookie 属性、跨页签注销与会话自然过期。
这些证据完成前，不得把 M6 HTTPS 浏览器验收、M6 自动验收或进入 M7 标记为完成。
