package com.aiplatform.appstudio.application.result;

/** RESULT_RECEIVED 恢复批次的安全计数，不携带完成事实或异常内容。 */
public record ChatRunRecoveryBatchResult(int claimed, int completed, int failed) {
    public ChatRunRecoveryBatchResult {
        if (claimed < 0 || completed < 0 || failed < 0 || completed + failed != claimed) {
            throw new IllegalArgumentException("恢复批次计数不一致");
        }
    }
}
