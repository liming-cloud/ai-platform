/**
 * 根据租户、部署修订和模型能力解析可调用的模型运行时。
 *
 * <p>允许依赖运行时 facade、扩展端口和领域仓储；禁止依赖 Web 入口与具体厂商。
 * 缓存以配置修订为失效边界，框架和厂商对象不得泄漏到公开契约。</p>
 */
package com.aiplatform.foundation.modelruntime.application.runtime;
