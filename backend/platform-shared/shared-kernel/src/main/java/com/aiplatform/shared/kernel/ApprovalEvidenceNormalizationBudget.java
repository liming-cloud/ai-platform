package com.aiplatform.shared.kernel;

import java.nio.charset.StandardCharsets;

/** Shared bounded accounting for approval-evidence canonicalization. */
final class ApprovalEvidenceNormalizationBudget {
    private final int maxNodes;
    private final int maxTotalScalarBytes;
    private int nodes;
    private int scalarBytes;

    ApprovalEvidenceNormalizationBudget(int maxNodes, int maxTotalScalarBytes) {
        if (maxNodes <= 0 || maxTotalScalarBytes <= 0) {
            throw new IllegalArgumentException("审批证据预算上限必须大于 0");
        }
        this.maxNodes = maxNodes;
        this.maxTotalScalarBytes = maxTotalScalarBytes;
    }

    void addNode() {
        nodes++;
        if (nodes > maxNodes) {
            throw new IllegalArgumentException("审批证据节点数量超过上限 " + maxNodes);
        }
    }

    void addScalarBytes(String value, int perValueLimit, String fieldName) {
        int bytes = value.getBytes(StandardCharsets.UTF_8).length;
        if (bytes > perValueLimit) {
            throw new IllegalArgumentException(fieldName + "超过长度上限 " + perValueLimit);
        }
        scalarBytes += bytes;
        if (scalarBytes > maxTotalScalarBytes) {
            throw new IllegalArgumentException("审批证据标量总大小超过上限");
        }
    }
}
