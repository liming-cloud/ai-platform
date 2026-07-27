/**
 * 文本数据源创建、上传、发布与分页查询用例。
 *
 * <p>允许依赖数据源聚合仓储及内容暂存、对象存储端口；禁止依赖具体存储实现。
 * 上传和发布先完成权限与完整性校验，持久化原子性由仓储边界负责。</p>
 */
package com.aiplatform.foundation.knowledgedata.application.datasource;
