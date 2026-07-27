package com.aiplatform.foundation.persistence.identifier;

import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import com.baomidou.mybatisplus.core.incrementer.DefaultIdentifierGenerator;
import com.baomidou.mybatisplus.core.incrementer.IdentifierGenerator;

/**
 * 使用 MyBatis-Plus 雪花序列统一生成平台实体标识。
 */
public final class MybatisPlusSnowflakeIdGenerator
        implements EntityIdGenerator, IdentifierGenerator {

    private static final long MAX_NODE_ID = 31;

    private final DefaultIdentifierGenerator delegate;

    public MybatisPlusSnowflakeIdGenerator(long workerId, long datacenterId) {
        this.delegate = new DefaultIdentifierGenerator(
                requireNodeId(workerId, "工作节点编号"),
                requireNodeId(datacenterId, "数据中心编号"));
    }

    @Override
    public long nextId() {
        return nextId(null);
    }

    @Override
    public Long nextId(Object entity) {
        var identifier = delegate.nextId(entity);
        if (identifier == null || identifier <= 0) {
            throw new IllegalStateException("雪花标识生成结果必须为正数");
        }
        return identifier;
    }

    private static long requireNodeId(long value, String field) {
        if (value < 0 || value > MAX_NODE_ID) {
            throw new IllegalArgumentException(field + "必须处于 0 至 31");
        }
        return value;
    }
}
