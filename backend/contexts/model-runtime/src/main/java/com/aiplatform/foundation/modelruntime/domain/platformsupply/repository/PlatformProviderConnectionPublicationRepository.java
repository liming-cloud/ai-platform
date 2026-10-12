package com.aiplatform.foundation.modelruntime.domain.platformsupply.repository;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.publication.PlatformProviderConnectionPublication;
import java.time.Duration;
import java.util.Optional;

/** 连接发布的不可变持久化边界；锁定当前连接复验后提交，重复发布返回原事实。 */
public interface PlatformProviderConnectionPublicationRepository {
    /** 当前修订或有效探测不匹配时拒绝；发布事实和最小操作者审计原子提交。 */
    PlatformProviderConnectionPublication publish(PlatformProviderConnectionId id, long expectedVersion,
            long publicationId, long actor, Duration validity);

    /** 只读取精确连接修订的发布事实，不把旧修订提升为当前发布。 */
    Optional<PlatformProviderConnectionPublication> find(PlatformProviderConnectionId id, long connectionRevision);
}
