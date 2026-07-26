package com.aiplatform.foundation.modelruntime.application.runtime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.Test;

class RevisionedRuntimeCacheTest {

    @Test
    void 同一修订应复用运行时对象() {
        var cache = new RevisionedRuntimeCache<String, Object>(
                2,
                Duration.ofMinutes(5),
                Clock.systemUTC());
        var constructions = new AtomicInteger();

        var first = cache.get("deployment:1", () -> {
            constructions.incrementAndGet();
            return new Object();
        });
        var second = cache.get("deployment:1", () -> {
            constructions.incrementAndGet();
            return new Object();
        });

        assertSame(first, second);
        assertEquals(1, constructions.get());
    }

    @Test
    void 构造失败不得污染缓存() {
        var cache = new RevisionedRuntimeCache<String, Object>(
                2,
                Duration.ofMinutes(5),
                Clock.systemUTC());
        var constructions = new AtomicInteger();

        assertThrows(IllegalStateException.class, () -> cache.get("deployment:1", () -> {
            constructions.incrementAndGet();
            throw new IllegalStateException("构造失败");
        }));
        var recovered = cache.get("deployment:1", () -> {
            constructions.incrementAndGet();
            return new Object();
        });

        assertEquals(2, constructions.get());
        assertSame(recovered, cache.get("deployment:1", Object::new));
    }

    @Test
    void 空闲超时后应重建运行时对象() {
        var clock = new MutableClock(Instant.parse("2026-07-27T00:00:00Z"));
        var cache = new RevisionedRuntimeCache<String, Object>(
                2,
                Duration.ofMinutes(5),
                clock);
        var first = cache.get("deployment:1", Object::new);

        clock.advance(Duration.ofMinutes(6));
        var second = cache.get("deployment:1", Object::new);

        assertNotSame(first, second);
    }

    @Test
    void 超出容量时淘汰最久未使用对象并支持定向淘汰() {
        var cache = new RevisionedRuntimeCache<String, Object>(
                2,
                Duration.ofMinutes(5),
                Clock.systemUTC());
        var first = cache.get("provider-a:1", Object::new);
        var second = cache.get("provider-b:1", Object::new);

        assertSame(first, cache.get("provider-a:1", Object::new));
        cache.get("provider-c:1", Object::new);
        assertNotSame(second, cache.get("provider-b:1", Object::new));

        cache.evictIf(key -> key.startsWith("provider-a:"));
        assertNotSame(first, cache.get("provider-a:1", Object::new));
    }

    private static final class MutableClock extends Clock {

        private Instant instant;

        private MutableClock(Instant instant) {
            this.instant = instant;
        }

        private void advance(Duration duration) {
            instant = instant.plus(duration);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}
