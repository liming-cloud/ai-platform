package com.aiplatform.foundation.knowledgedata.application.indexversion.facade;


/**
 * 索引版本冻结的嵌入模型绑定；历史部署与受控模型配置必须二选一。
 */
public record EmbeddingConfiguration(
        Long deploymentId,
        Long deploymentRevision,
        Long configurationId,
        Long configurationRevision,
        Long catalogEntryId,
        Long catalogEntryRevision,
        int dimension) {

    // 当前索引使用 pgvector 精确检索，允许 `vector` 存储类型支持的最大 16000 维输出。
    public static final int MAX_VECTOR_DIMENSION = 16000;

    public EmbeddingConfiguration(long deploymentId, long deploymentRevision, int dimension) {
        this(deploymentId, deploymentRevision, null, null, null, null, dimension);
    }

    public EmbeddingConfiguration {
        var legacy = deploymentId != null || deploymentRevision != null;
        var governed = configurationId != null
                || configurationRevision != null
                || catalogEntryId != null
                || catalogEntryRevision != null;
        if (legacy == governed) {
            throw new IllegalArgumentException("嵌入绑定必须且只能选择历史部署或受控模型配置");
        }
        if (legacy && (positive(deploymentId) == false || positive(deploymentRevision) == false)) {
            throw new IllegalArgumentException("嵌入部署标识和修订必须大于零");
        }
        if (governed && (positive(configurationId) == false
                || positive(configurationRevision) == false
                || positive(catalogEntryId) == false
                || positive(catalogEntryRevision) == false)) {
            throw new IllegalArgumentException("受控嵌入配置与目录修订必须大于零");
        }
        if (dimension < 1 || dimension > MAX_VECTOR_DIMENSION) {
            throw new IllegalArgumentException("嵌入维度超出平台范围");
        }
    }

    /**
     * 创建供新索引版本使用的受控模型配置绑定。
     */
    public static EmbeddingConfiguration governed(
            long configurationId,
            long configurationRevision,
            long catalogEntryId,
            long catalogEntryRevision,
            int dimension) {
        return new EmbeddingConfiguration(
                null,
                null,
                configurationId,
                configurationRevision,
                catalogEntryId,
                catalogEntryRevision,
                dimension);
    }

    public boolean isLegacyDeployment() {
        return deploymentId != null;
    }

    public boolean isGoverned() {
        return configurationId != null;
    }

    private static boolean positive(Long value) {
        return value != null && value > 0;
    }
}
