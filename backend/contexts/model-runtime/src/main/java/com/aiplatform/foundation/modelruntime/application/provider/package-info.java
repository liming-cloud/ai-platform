/**
 * 提供供应商连接的命令、查询、应用服务与公开门面。
 *
 * <p>允许依赖供应商领域模型和仓储契约；禁止引用 Web、MyBatis、Spring AI 或厂商实现。
 * 应用服务承担权限、幂等、配额和事务编排，只有 facade 包可以被其他上下文依赖。</p>
 */
package com.aiplatform.foundation.modelruntime.application.provider;
