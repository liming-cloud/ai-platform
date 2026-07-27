package com.aiplatform.foundation.knowledgedata.domain.common.model;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;

/**
 * 知识数据上下文保存的创建者标识。
 *
 * <p>该值仅表达本上下文对创建者身份的稳定引用，不依赖权限平台的运行时身份类型。</p>
 */
public record CreatorId(long value) implements ValueObject {

    public CreatorId {
        if (value <= 0) {
            throw new IllegalArgumentException("创建者标识必须是正整数");
        }
    }

    public static CreatorId parse(String value) {
        try {
            return new CreatorId(Long.parseLong(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("创建者标识必须是十进制正整数", exception);
        }
    }

    @Override
    public String toString() {
        return Long.toString(value);
    }
}
