package com.aiplatform.foundation.kernel.identifier;

/**
 * 为新建实体生成不透明的 64 位正整数标识。
 */
@FunctionalInterface
public interface EntityIdGenerator {

    long nextId();
}
