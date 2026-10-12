package com.aiplatform.foundation.modelruntime.domain.platformsupply.model.publication;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnection;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformProviderConnectionId;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStateException;
import com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStatus;
import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/** 不可变的连接发布事实；发布不改变探测修订，也不证明任何模型能力或租户授权。 */
public record PlatformProviderConnectionPublication(
        long id, PlatformProviderConnectionId connectionId, long revision, long connectionRevision,
        long publishedBySubjectId, Instant probedAt, Instant publishedAt) implements ValueObject {

    public PlatformProviderConnectionPublication {
        Objects.requireNonNull(connectionId, "连接标识不能为空");
        Objects.requireNonNull(probedAt, "发布绑定的探测时间不能为空");
        Objects.requireNonNull(publishedAt, "发布时间不能为空");
        if (id <= 0 || revision <= 0 || connectionRevision < 0 || publishedBySubjectId <= 0
                || publishedAt.isBefore(probedAt)) {
            throw new IllegalArgumentException("连接发布事实不完整");
        }
    }

    /**
     * 从当前连接的有效探测建立发布事实。
     *
     * @throws com.aiplatform.foundation.modelruntime.domain.provider.model.ProviderConnectionStateException
     *         草稿、停用、探测过期或探测时间来自未来时拒绝发布。
     */
    public static PlatformProviderConnectionPublication publish(
            long id, long revision, PlatformProviderConnection connection, long actor,
            Instant now, Duration validity) {
        Objects.requireNonNull(connection, "连接不能为空");
        Objects.requireNonNull(now, "发布时间不能为空");
        Objects.requireNonNull(validity, "探测有效期不能为空");
        if (validity.isZero() || validity.isNegative()) {
            throw new IllegalArgumentException("探测有效期必须大于零");
        }
        var probedAt = connection.lastProbedAt();
        if (connection.status() != ProviderConnectionStatus.VERIFIED
                || !Boolean.TRUE.equals(connection.lastProbeSucceeded()) || probedAt == null
                || probedAt.isAfter(now) || !probedAt.plus(validity).isAfter(now)) {
            throw new ProviderConnectionStateException("连接当前修订没有有效探测，不能发布");
        }
        return new PlatformProviderConnectionPublication(id, connection.id(), revision, connection.version(),
                actor, probedAt, now);
    }
}
