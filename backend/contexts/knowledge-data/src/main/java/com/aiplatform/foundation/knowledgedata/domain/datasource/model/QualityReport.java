package com.aiplatform.foundation.knowledgedata.domain.datasource.model;

import java.util.Objects;

/**
 * 归属于单个同步产物的不可变质量结论。
 *
 * <p>只有非阻断结论可进入显式发布判断，阻断产物必须先产生新的同步或质量结果。</p>
 */
public final class QualityReport {

    private final SyncArtifact artifact;
    private final String ruleSetVersion;
    private final QualityStatus status;
    private final long warningCount;
    private final long blockingCount;

    private QualityReport(
            SyncArtifact artifact,
            String ruleSetVersion,
            QualityStatus status,
            long warningCount,
            long blockingCount) {
        this.artifact = Objects.requireNonNull(artifact, "同步产物不能为空");
        this.ruleSetVersion = requireRuleSetVersion(ruleSetVersion);
        this.status = Objects.requireNonNull(status, "质量结论不能为空");
        if (warningCount < 0 || blockingCount < 0) {
            throw new IllegalArgumentException("质量问题计数不能为负数");
        }
        if ((status == QualityStatus.PASS && (warningCount != 0 || blockingCount != 0))
                || (status == QualityStatus.WARN && (warningCount == 0 || blockingCount != 0))
                || (status == QualityStatus.BLOCK && blockingCount == 0)) {
            throw new IllegalArgumentException("质量结论与问题计数不一致");
        }
        this.warningCount = warningCount;
        this.blockingCount = blockingCount;
    }

    /**
     * 创建无告警的通过结论。
     */
    public static QualityReport passed(SyncArtifact artifact, String ruleSetVersion) {
        return new QualityReport(artifact, ruleSetVersion, QualityStatus.PASS, 0, 0);
    }

    /**
     * 创建可继续发布判断的告警结论。
     */
    public static QualityReport warned(
            SyncArtifact artifact,
            String ruleSetVersion,
            long warningCount) {
        return new QualityReport(artifact, ruleSetVersion, QualityStatus.WARN, warningCount, 0);
    }

    /**
     * 创建阻止发布的质量结论。
     */
    public static QualityReport blocked(
            SyncArtifact artifact,
            String ruleSetVersion,
            long warningCount,
            long blockingCount) {
        return new QualityReport(
                artifact,
                ruleSetVersion,
                QualityStatus.BLOCK,
                warningCount,
                blockingCount);
    }

    /**
     * 返回质量规则的汇总结论。
     */
    public QualityStatus status() {
        return status;
    }

    /**
     * 返回允许进入显式发布判断的同步产物。
     *
     * @throws DataSourceStateException 质量规则存在阻断问题时抛出。
     */
    public SyncArtifact requirePublishable() {
        if (status == QualityStatus.BLOCK) {
            throw new DataSourceStateException("同步产物存在阻断质量问题");
        }
        return artifact;
    }

    private static String requireRuleSetVersion(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("质量规则版本不能为空");
        }
        return value.trim();
    }
}
