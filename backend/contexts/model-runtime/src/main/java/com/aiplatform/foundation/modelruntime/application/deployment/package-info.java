/**
 * 提供模型部署的创建、探测、发布、分页查询与发布态门面。
 *
 * <p>允许依赖模型部署及供应商领域；禁止依赖接口层和基础设施实现。
 * 发布动作是应用事务边界，跨上下文只允许使用 facade 中的稳定快照。</p>
 */
package com.aiplatform.foundation.modelruntime.application.deployment;
