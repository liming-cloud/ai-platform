package com.aiplatform.foundation.persistence.identifier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.kernel.identifier.EntityIdGenerator;
import java.util.HashSet;
import java.util.stream.LongStream;
import org.junit.jupiter.api.Test;

class MybatisPlusSnowflakeIdGeneratorTest {

    @Test
    void 同一节点连续生成的标识应为正数且严格递增() {
        EntityIdGenerator generator = new MybatisPlusSnowflakeIdGenerator(3, 7);

        var identifiers = LongStream.range(0, 10_000)
                .map(ignored -> generator.nextId())
                .boxed()
                .toList();

        assertEquals(identifiers.size(), new HashSet<>(identifiers).size());
        assertTrue(identifiers.stream().allMatch(identifier -> identifier > 0));
        for (var index = 1; index < identifiers.size(); index++) {
            assertTrue(identifiers.get(index) > identifiers.get(index - 1));
        }
    }

    @Test
    void 节点编号必须处于雪花算法五位范围内() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new MybatisPlusSnowflakeIdGenerator(-1, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MybatisPlusSnowflakeIdGenerator(32, 0));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MybatisPlusSnowflakeIdGenerator(0, -1));
        assertThrows(
                IllegalArgumentException.class,
                () -> new MybatisPlusSnowflakeIdGenerator(0, 32));
    }
}
