package com.aiplatform.modelhub.application.idempotency;

import com.aiplatform.shared.kernel.Sha256Digests;

/** Atomic claim result: the caller either owns the new command or replays one completed resource. */
public record ModelCreationRequestClaim(
        boolean acquired,
        String requestDigest,
        ModelCreationResultSnapshot result
) {
    public ModelCreationRequestClaim {
        requestDigest = Sha256Digests.requireLowercase(requestDigest, "模型创建请求摘要");
        if (acquired && result != null) {
            throw new IllegalArgumentException("新取得的模型创建请求不能已有结果快照");
        }
        if (!acquired && result == null) {
            throw new IllegalArgumentException("模型创建重放必须绑定已完成结果快照");
        }
    }

    public static ModelCreationRequestClaim acquired(String requestDigest) {
        return new ModelCreationRequestClaim(true, requestDigest, null);
    }

    public static ModelCreationRequestClaim replay(
            String requestDigest,
            ModelCreationResultSnapshot result
    ) {
        return new ModelCreationRequestClaim(false, requestDigest, result);
    }
}
