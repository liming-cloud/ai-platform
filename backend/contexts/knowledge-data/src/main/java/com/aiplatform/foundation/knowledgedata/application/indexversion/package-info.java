/**
 * 索引版本创建、构建、激活和活动索引查询用例。
 *
 * <p>允许依赖索引聚合、模型公共门面和向量端口；禁止依赖 Spring AI 或向量库实现。
 * 跨上下文只公开 facade，向量能力只公开 port/out 扩展端口。</p>
 */
package com.aiplatform.foundation.knowledgedata.application.indexversion;
