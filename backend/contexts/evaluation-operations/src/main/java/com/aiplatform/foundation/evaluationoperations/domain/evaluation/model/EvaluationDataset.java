package com.aiplatform.foundation.evaluationoperations.domain.evaluation.model;

import com.aiplatform.foundation.shared.kernel.domain.AggregateRoot;
import com.aiplatform.foundation.shared.kernel.tenant.TenantId;
import java.time.Instant;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * evaluation-operations 拥有的数据集清单根；它只保存私有正文引用和摘要，绝不保存正文。
 */
public final class EvaluationDataset implements AggregateRoot<EvaluationDatasetId> {

    private static final Pattern SHA256 = Pattern.compile("[0-9a-f]{64}");

    private final TenantId tenantId;
    private final EvaluationDatasetId id;
    private final String privateContentReference;
    private final String contentDigest;
    private final int entryCount;
    private final EvaluationDatasetContentClassification classification;
    private final String retentionPolicyId;
    private final EvaluationDatasetStatus status;
    private final long version;
    private final Instant createdAt;

    private EvaluationDataset(
            TenantId tenantId,
            EvaluationDatasetId id,
            String privateContentReference,
            String contentDigest,
            int entryCount,
            EvaluationDatasetContentClassification classification,
            String retentionPolicyId,
            EvaluationDatasetStatus status,
            long version,
            Instant createdAt) {
        this.tenantId = Objects.requireNonNull(tenantId, "评测数据集租户不能为空");
        this.id = Objects.requireNonNull(id, "评测数据集标识不能为空");
        this.privateContentReference = requirePrivateReference(privateContentReference);
        if (contentDigest == null || !SHA256.matcher(contentDigest).matches()) {
            throw new IllegalArgumentException("评测数据集内容摘要格式不正确");
        }
        this.contentDigest = contentDigest;
        if (entryCount <= 0) {
            throw new IllegalArgumentException("评测数据集条目数必须大于零");
        }
        this.entryCount = entryCount;
        if (classification != EvaluationDatasetContentClassification.SYNTHETIC) {
            throw new IllegalArgumentException("首批评测数据集只能使用合成内容");
        }
        this.classification = classification;
        this.retentionPolicyId = requireToken(retentionPolicyId, "保留策略标识");
        this.status = Objects.requireNonNull(status, "评测数据集状态不能为空");
        if (version < 0L) {
            throw new IllegalArgumentException("评测数据集修订不能为负数");
        }
        this.version = version;
        this.createdAt = Objects.requireNonNull(createdAt, "评测数据集创建时间不能为空");
    }

    /**
     * 创建尚未可供运行引用的数据集清单。
     */
    public static EvaluationDataset create(
            TenantId tenantId,
            EvaluationDatasetId id,
            String privateContentReference,
            String contentDigest,
            int entryCount,
            EvaluationDatasetContentClassification classification,
            String retentionPolicyId,
            Instant createdAt) {
        return new EvaluationDataset(
                tenantId, id, privateContentReference, contentDigest, entryCount, classification,
                retentionPolicyId, EvaluationDatasetStatus.DRAFT, 0L, createdAt);
    }

    /**
     * 发布不可变清单，使其可以被同租户评测运行冻结。
     *
     * @throws IllegalStateException 当数据集已发布时拒绝重复发布。
     */
    public EvaluationDataset publish() {
        if (status != EvaluationDatasetStatus.DRAFT) {
            throw new IllegalStateException("只有草稿评测数据集可以发布");
        }
        return new EvaluationDataset(
                tenantId, id, privateContentReference, contentDigest, entryCount, classification,
                retentionPolicyId, EvaluationDatasetStatus.PUBLISHED, version + 1L, createdAt);
    }

    public TenantId tenantId() {
        return tenantId;
    }

    @Override
    public EvaluationDatasetId id() {
        return id;
    }

    public String contentDigest() {
        return contentDigest;
    }

    /**
     * 返回仅供受控基础设施定位私有对象的不透明引用；接口层不得投影该值。
     */
    public String privateContentReference() {
        return privateContentReference;
    }

    /**
     * 返回由后续受控解析器核验的条目总数。
     */
    public int entryCount() {
        return entryCount;
    }

    /**
     * 返回冻结的内容分类，首批只允许 `SYNTHETIC`。
     */
    public EvaluationDatasetContentClassification classification() {
        return classification;
    }

    /**
     * 返回决定私有正文保留与清理边界的策略标识。
     */
    public String retentionPolicyId() {
        return retentionPolicyId;
    }

    public EvaluationDatasetStatus status() {
        return status;
    }

    public long version() {
        return version;
    }

    /**
     * 返回清单首次创建时间，不随发布变更。
     */
    public Instant createdAt() {
        return createdAt;
    }

    private static String requirePrivateReference(String value) {
        if (value == null || !value.startsWith("private://evaluation/") || !value.equals(value.trim())) {
            throw new IllegalArgumentException("评测私有内容引用格式不正确");
        }
        return value;
    }

    private static String requireToken(String value, String label) {
        if (value == null || value.isBlank() || !value.equals(value.trim())) {
            throw new IllegalArgumentException(label + "格式不正确");
        }
        return value;
    }
}
