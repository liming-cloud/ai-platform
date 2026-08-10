# WP-0014 浏览器 TLS 前置条件复核

## 目的

复核 ADR-0048、ADR-0059 所要求的真实浏览器角色任务，确认本地部署入口能否在不降低 HTTPS 安全契约的前提下执行。

## 执行结果

2026-08-10，使用受控浏览器创建空白标签后导航至 `https://localhost:13443/`。浏览器返回：

```text
net::ERR_CERT_AUTHORITY_INVALID
```

该入口由 `deploy/compose/compose.browser-acceptance.yml` 的本地 HTTPS 端口和 `deploy/config/nginx-console-https.conf` 提供。错误说明浏览器未信任本地开发证书，并不代表应用接口或授权语义失效。

## 安全结论

- 不安装 macOS System keychain CA；
- 不忽略证书错误，也不改用 HTTP；
- 因而本地真实浏览器登录、跨页签会话及角色任务暂不能作为通过证据；
- 该项必须由正式部署使用受信任证书后重新执行，或由人工在已受信任的环境中验收。

## 边界

本记录不降低 ADR-0048 的 HTTPS Cookie、会话失效或 ADR-0059 角色任务验收标准，也不构成 M6 人工评审通过条件。
