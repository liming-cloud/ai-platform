/**
 * 按模型目录、持久化、Web 与运行时能力提供 Spring Boot 自动配置。
 *
 * <p>只允许显式导入 Bean，不执行宽泛组件扫描；缺少必需依赖时启动失败，可替换端口使用
 * ConditionalOnMissingBean。bootstrap 不在此之外重复声明业务 Bean。</p>
 */
package com.aiplatform.foundation.modelruntime.infrastructure.configuration;
