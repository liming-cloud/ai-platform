package com.aiplatform.foundation.knowledgedata.infrastructure.configuration;

import com.aiplatform.foundation.knowledgedata.domain.datasource.model.TextContentReference;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 知识数据上下文经过类型校验的运行参数。
 */
@ConfigurationProperties("foundation.knowledge-data")
public class KnowledgeDataProperties {

    private int knowledgeSpaceLimit = 3;
    private int dataSourceLimit = 20;
    private Path stagingDirectory = Path.of("/tmp/foundation-text-staging");
    private long maximumTextBytes = TextContentReference.MAX_BYTE_SIZE;
    private Duration indexBuildLeaseDuration = Duration.ofMinutes(1);
    private List<ObjectStorageRange> objectStorageRanges = List.of();

    public int getKnowledgeSpaceLimit() {
        return knowledgeSpaceLimit;
    }

    public void setKnowledgeSpaceLimit(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("知识空间配额必须大于零");
        }
        knowledgeSpaceLimit = value;
    }

    public int getDataSourceLimit() {
        return dataSourceLimit;
    }

    public void setDataSourceLimit(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("数据源配额必须大于零");
        }
        dataSourceLimit = value;
    }

    public Path getStagingDirectory() {
        return stagingDirectory;
    }

    public void setStagingDirectory(Path value) {
        if (value == null) {
            throw new IllegalArgumentException("文本暂存目录不能为空");
        }
        stagingDirectory = value;
    }

    public long getMaximumTextBytes() {
        return maximumTextBytes;
    }

    public void setMaximumTextBytes(long value) {
        if (value < 1 || value > TextContentReference.MAX_BYTE_SIZE) {
            throw new IllegalArgumentException("文本暂存上限超出平台安全范围");
        }
        maximumTextBytes = value;
    }

    public Duration getIndexBuildLeaseDuration() {
        return indexBuildLeaseDuration;
    }

    public void setIndexBuildLeaseDuration(Duration value) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException("索引构建租约必须是正时长");
        }
        indexBuildLeaseDuration = value;
    }

    public List<ObjectStorageRange> getObjectStorageRanges() { return objectStorageRanges; }
    public void setObjectStorageRanges(List<ObjectStorageRange> value) {
        objectStorageRanges = List.copyOf(value == null ? List.of() : value);
    }

    /** 部署侧预置的、不可由知识管理员管理的平台对象范围选择。 */
    public static class ObjectStorageRange {
        private long tenantId;
        private long knowledgeSpaceId;
        private String rangeId;
        private long selectionRevision;
        private List<ObjectStorageRangeObject> objects = List.of();
        public long getTenantId() { return tenantId; }
        public void setTenantId(long value) { tenantId = value; }
        public long getKnowledgeSpaceId() { return knowledgeSpaceId; }
        public void setKnowledgeSpaceId(long value) { knowledgeSpaceId = value; }
        public String getRangeId() { return rangeId; }
        public void setRangeId(String value) { rangeId = value; }
        public long getSelectionRevision() { return selectionRevision; }
        public void setSelectionRevision(long value) { selectionRevision = value; }
        public List<ObjectStorageRangeObject> getObjects() { return objects; }
        public void setObjects(List<ObjectStorageRangeObject> value) { objects = List.copyOf(value == null ? List.of() : value); }
    }

    /** 部署侧范围选择中冻结的不透明对象元数据。 */
    public static class ObjectStorageRangeObject {
        private String objectId;
        private String contentDigest;
        private long byteSize;
        private String mediaType;
        private String freshAt;
        public String getObjectId() { return objectId; }
        public void setObjectId(String value) { objectId = value; }
        public String getContentDigest() { return contentDigest; }
        public void setContentDigest(String value) { contentDigest = value; }
        public long getByteSize() { return byteSize; }
        public void setByteSize(long value) { byteSize = value; }
        public String getMediaType() { return mediaType; }
        public void setMediaType(String value) { mediaType = value; }
        public String getFreshAt() { return freshAt; }
        public void setFreshAt(String value) { freshAt = value; }
    }
}
