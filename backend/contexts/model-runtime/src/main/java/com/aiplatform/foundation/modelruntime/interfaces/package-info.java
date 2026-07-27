/**
 * 承载模型目录的 Web、消息和任务入口。
 *
 * <p>允许依赖具体应用服务和公共响应契约；禁止直接依赖仓储、Mapper、厂商客户端或领域
 * 基础设施实现。身份与租户事实必须由可信服务端上下文取得。</p>
 */
package com.aiplatform.foundation.modelruntime.interfaces;
