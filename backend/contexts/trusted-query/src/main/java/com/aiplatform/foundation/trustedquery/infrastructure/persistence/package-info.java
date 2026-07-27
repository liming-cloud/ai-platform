/**
 * 可信查询持久化实现；负责领域对象与数据库投影的显式转换。
 *
 * <p>MyBatis-Plus 分页和 Mapper 类型只能停留在本层，事务覆盖聚合写入、
 * 事件、幂等请求和用量事实的一致性变更。</p>
 */
package com.aiplatform.foundation.trustedquery.infrastructure.persistence;
