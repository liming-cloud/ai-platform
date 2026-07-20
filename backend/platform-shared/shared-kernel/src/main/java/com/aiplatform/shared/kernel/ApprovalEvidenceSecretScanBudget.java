package com.aiplatform.shared.kernel;

/** Bounded node counter for recursive approval-evidence secret inspection. */
final class ApprovalEvidenceSecretScanBudget {
    private final int maxNodes;
    private int nodes;

    ApprovalEvidenceSecretScanBudget(int maxNodes) {
        if (maxNodes <= 0) {
            throw new IllegalArgumentException("审批证据扫描节点上限必须大于 0");
        }
        this.maxNodes = maxNodes;
    }

    void addNode() {
        nodes++;
        if (nodes > maxNodes) {
            throw new IllegalArgumentException("审批证据扫描节点数量超过上限 " + maxNodes);
        }
    }
}
