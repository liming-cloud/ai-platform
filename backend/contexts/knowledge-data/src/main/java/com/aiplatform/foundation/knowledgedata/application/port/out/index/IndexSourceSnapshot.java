package com.aiplatform.foundation.knowledgedata.application.port.out.index;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.DataSourceId;
import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Objects;

/**
 * 索引版本冻结的单个已发布内容快照。
 */
public record IndexSourceSnapshot(
        IndexSourceKind kind,
        DataSourceId dataSourceId,
        long sourceRevision,
        Long syncArtifactId,
        String qualityStatus,
        String qualityRuleSetVersion,
        TextContentReference content) {

    public IndexSourceSnapshot {
        Objects.requireNonNull(kind, "来源类型不能为空");
        Objects.requireNonNull(dataSourceId, "数据源标识不能为空");
        Objects.requireNonNull(content, "内容引用不能为空");
        if (sourceRevision < 1) {
            throw new IllegalArgumentException("来源修订必须为正数");
        }
        if (kind == IndexSourceKind.LEGACY_TEXT_CONTENT
                && (syncArtifactId != null || qualityStatus != null || qualityRuleSetVersion != null)) {
            throw new IllegalArgumentException("历史文本快照不能携带同步产物质量事实");
        }
        if (kind == IndexSourceKind.GOVERNED_SYNC_ARTIFACT
                && (syncArtifactId == null || qualityStatus == null || qualityRuleSetVersion == null)) {
            throw new IllegalArgumentException("受治理同步产物快照必须冻结产物与质量事实");
        }
    }

    /** 兼容历史文本来源的调用方。 */
    public IndexSourceSnapshot(DataSourceId dataSourceId, TextContentReference content) {
        this(IndexSourceKind.LEGACY_TEXT_CONTENT, dataSourceId, content.version(), null, null, null, content);
    }

    /**
     * 返回区分来源快照的稳定摘要，供分块标识保留来源引用。
     *
     * @return 小写十六进制 SHA-256 摘要。
     */
    public String sourceIdentityDigest() {
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            update(digest, kind.name());
            update(digest, Long.toString(dataSourceId.value()));
            update(digest, Long.toString(sourceRevision));
            update(digest, syncArtifactId == null ? "" : Long.toString(syncArtifactId));
            update(digest, Integer.toString(content.version()));
            update(digest, content.sha256());
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private static void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) 0);
    }
}
