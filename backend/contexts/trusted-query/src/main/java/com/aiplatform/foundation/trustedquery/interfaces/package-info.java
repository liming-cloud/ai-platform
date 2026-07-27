/**
 * 可信查询入口层；将 HTTP、消息或任务协议转换为应用命令和查询。
 *
 * <p>允许依赖具体应用服务，禁止包含领域规则、SQL 和基础设施实现。
 * 认证后的可信请求上下文构成安全边界。</p>
 */
package com.aiplatform.foundation.trustedquery.interfaces;
