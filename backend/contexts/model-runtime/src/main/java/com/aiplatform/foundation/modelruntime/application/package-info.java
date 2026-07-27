/**
 * 编排模型目录、探测与运行时解析用例。
 *
 * <p>允许依赖领域层、共享契约和明确的外部技术端口；禁止依赖接口层与基础设施实现。
 * 事务边界由具体应用服务声明，跨上下文只公开 facade 与扩展端口。</p>
 */
package com.aiplatform.foundation.modelruntime.application;
