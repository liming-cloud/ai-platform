/**
 * 提供仅由平台治理会话调用的租户生命周期 HTTP 入口。
 *
 * <p>当前操作者始终由 `PlatformRequestContext` 从 Bearer 会话恢复；请求仅能表达受管租户及初始管理员
 * 业务资源目标。</p>
 */
package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle;
