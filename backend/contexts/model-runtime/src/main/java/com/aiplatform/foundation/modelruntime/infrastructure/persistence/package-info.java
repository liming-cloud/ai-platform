/**
 * 使用 MyBatis-Plus 与受控 XML 实现模型目录仓储。
 *
 * <p>单表操作使用 Lambda Wrapper；事务锁、幂等写入和 PostgreSQL 数组投影允许使用 XML。
 * Page 与 Mapper 类型不得离开基础设施层，租户条件是所有查询和更新的强制边界。</p>
 */
package com.aiplatform.foundation.modelruntime.infrastructure.persistence;
