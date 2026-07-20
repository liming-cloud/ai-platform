package com.aiplatform.modelhub.infrastructure.secret;

/** 不含任何凭证正文的安全轮换批次计数。 */
public record ModelSecretRotationBatchResult(
        int selected,
        int rotated,
        int conflicted,
        int failed,
        long backlog
) {
    public ModelSecretRotationBatchResult {
        if (selected < 0 || rotated < 0 || conflicted < 0 || failed < 0 || backlog < 0) {
            throw new IllegalArgumentException("模型密钥轮换计数不能为负数");
        }
        if (rotated + conflicted + failed != selected) {
            throw new IllegalArgumentException("模型密钥轮换计数不守恒");
        }
    }
}
