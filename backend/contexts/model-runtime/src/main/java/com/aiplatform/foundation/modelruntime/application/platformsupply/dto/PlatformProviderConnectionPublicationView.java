package com.aiplatform.foundation.modelruntime.application.platformsupply.dto;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.publication.PlatformProviderConnectionPublication;
import java.time.Instant;

/** 发布读取只返回绑定和时间，不暴露秘密、端点、发布主体或内部事实标识。 */
public record PlatformProviderConnectionPublicationView(String connectionId, boolean published,
        Long publicationRevision, long connectionRevision, Instant probedAt, Instant publishedAt) {
    /** 转换为不包含内部审计身份的发布视图。 */
    public static PlatformProviderConnectionPublicationView from(PlatformProviderConnectionPublication publication) {
        return new PlatformProviderConnectionPublicationView(publication.connectionId().toString(), true,
                publication.revision(), publication.connectionRevision(), publication.probedAt(), publication.publishedAt());
    }

    /** 当前修订没有发布事实时返回明确的未发布状态。 */
    public static PlatformProviderConnectionPublicationView unpublished(String id, long revision) {
        return new PlatformProviderConnectionPublicationView(id, false, null, revision, null, null);
    }
}
