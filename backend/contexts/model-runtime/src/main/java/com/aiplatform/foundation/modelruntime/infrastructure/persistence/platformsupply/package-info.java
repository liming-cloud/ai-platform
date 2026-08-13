/**
 * 提供平台范围模型供应链的 PostgreSQL 持久化和密文凭据写入。
 *
 * <p>平台表与租户表完全分离；任何事务失败都不得留下连接根或凭据材料。</p>
 */
package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply;
