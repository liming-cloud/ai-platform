package com.aiplatform.foundation.modelruntime.application.runtime;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 按访问顺序维护的有界运行时对象缓存。
 *
 * <p>缓存只保存可重建的运行时对象，不保存构造异常；每次访问都会清理空闲超时对象。</p>
 */
final class RevisionedRuntimeCache<K, V> {

    private final int maximumSize;
    private final Duration idleTimeout;
    private final Clock clock;
    private final Map<K, Entry<V>> entries = new LinkedHashMap<>(16, 0.75f, true);

    RevisionedRuntimeCache(int maximumSize, Duration idleTimeout, Clock clock) {
        if (maximumSize < 1) {
            throw new IllegalArgumentException("运行时缓存容量必须大于零");
        }
        this.idleTimeout = Objects.requireNonNull(idleTimeout, "运行时缓存空闲时间不能为空");
        if (idleTimeout.isZero() || idleTimeout.isNegative()) {
            throw new IllegalArgumentException("运行时缓存空闲时间必须大于零");
        }
        this.maximumSize = maximumSize;
        this.clock = Objects.requireNonNull(clock, "运行时缓存时钟不能为空");
    }

    synchronized V get(K key, Supplier<? extends V> factory) {
        Objects.requireNonNull(key, "运行时缓存键不能为空");
        Objects.requireNonNull(factory, "运行时对象工厂不能为空");
        var now = clock.instant();
        removeExpired(now);
        var existing = entries.get(key);
        if (existing != null) {
            existing.lastAccessedAt = now;
            return existing.value;
        }

        var created = Objects.requireNonNull(factory.get(), "运行时对象工厂不得返回空值");
        entries.put(key, new Entry<>(created, now));
        evictOverflow();
        return created;
    }

    synchronized void evictIf(Predicate<? super K> condition) {
        Objects.requireNonNull(condition, "运行时缓存淘汰条件不能为空");
        entries.keySet().removeIf(condition);
    }

    private void removeExpired(Instant now) {
        entries.entrySet().removeIf(entry ->
                !entry.getValue().lastAccessedAt.plus(idleTimeout).isAfter(now));
    }

    private void evictOverflow() {
        Iterator<K> keys = entries.keySet().iterator();
        while (entries.size() > maximumSize && keys.hasNext()) {
            keys.next();
            keys.remove();
        }
    }

    private static final class Entry<V> {

        private final V value;
        private Instant lastAccessedAt;

        private Entry(V value, Instant lastAccessedAt) {
            this.value = value;
            this.lastAccessedAt = lastAccessedAt;
        }
    }
}
