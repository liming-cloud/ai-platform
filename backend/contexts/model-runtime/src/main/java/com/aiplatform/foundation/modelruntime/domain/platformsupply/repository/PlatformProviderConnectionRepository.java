package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import java.util.Optional;

/**
 * 平台供应商连接聚合的持久化边界。
 *
 * <p>实现必须只访问平台范围表，不能把调用翻译为任何租户查询。</p>
 */
public interface PlatformProviderConnectionRepository {

    PlatformProviderConnectionCreationOutcome create(PlatformProviderConnectionCreation creation);

    default Optional<PlatformProviderConnection> findById(PlatformProviderConnectionId id) {
        throw new UnsupportedOperationException("平台供应商连接仓储未实现按标识读取");
    }

    default PlatformProviderConnectionPage findPage(long pageNumber, long pageSize) {
        throw new UnsupportedOperationException("平台供应商连接仓储未实现分页读取");
    }

    default boolean update(PlatformProviderConnection connection, long expectedVersion) {
        throw new UnsupportedOperationException("平台供应商连接仓储未实现更新");
    }
}
