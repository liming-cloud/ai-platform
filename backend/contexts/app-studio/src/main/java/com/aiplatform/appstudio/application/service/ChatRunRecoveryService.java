package com.aiplatform.appstudio.application.service;

import com.aiplatform.appstudio.application.port.ChatRunRepository.Claim;
import com.aiplatform.appstudio.application.port.ChatRunRepository.ClaimState;
import com.aiplatform.appstudio.application.result.ChatRunRecoveryBatchResult;
import com.aiplatform.shared.observability.TraceContext;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * 重放已经持久化的 RESULT_RECEIVED 最终事务。该服务不依赖检索、策略或模型端口，因此不会重复模型调用。
 */
@Service
public class ChatRunRecoveryService {
    private final ChatRunTransactionService transactions;

    public ChatRunRecoveryService(ChatRunTransactionService transactions) {
        this.transactions = Objects.requireNonNull(transactions, "聊天运行事务服务不能为空");
    }

    public ChatRunRecoveryBatchResult recoverBatch(int limit) {
        List<Claim> claims = transactions.claimRecoverableResults(limit);
        int completed = 0;
        int failed = 0;
        for (Claim claim : claims) {
            if (claim.state() != ClaimState.RESULT_RECEIVED || claim.finalization() == null) {
                throw new IllegalStateException("恢复扫描返回了非 RESULT_RECEIVED claim");
            }
            try {
                transactions.complete(claim.ownership(), claim.finalization());
                completed++;
            } catch (RuntimeException ignored) {
                // 只持久化固定安全摘要与 trace；异常消息及 payload 均不进入日志或失败字段。
                try {
                    transactions.recordFinalizationFailure(claim.ownership(), TraceContext.currentTraceId());
                } catch (RuntimeException ignoredRecordingFailure) {
                    // 记录失败时仍保留原租约/fence；到期耗尽扫描会把达到上限的行转为死信。
                }
                failed++;
            }
        }
        return new ChatRunRecoveryBatchResult(claims.size(), completed, failed);
    }
}
