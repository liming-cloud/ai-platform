package org.fujay.ai.platform.cache;

import java.util.Optional;
import java.util.function.Supplier;

public interface TwoLevelCacheFacade {

    <T> Optional<T> get(String cacheName, String key, Class<T> valueType);

    <T> T getOrLoad(String cacheName, String key, Class<T> valueType, Supplier<T> loader);

    void evict(String cacheName, String key);
}
