package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.modelruntime.domain.platformsupply.service.PlatformModelDeploymentVerificationResult;
import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationReservation;
import java.util.List;
import java.util.function.BooleanSupplier;

/** 外呼前短事务预留，外呼后独立短事务复核并提交；网络不持有数据库事务。 */
public interface PlatformModelDeploymentVerificationStore {
    /** 冻结整轮预留并排斥同一部署的并发验证；当前会话与事实缺失时拒绝。 */
    void reserve(PlatformCapabilityVerificationReservation reservation, BooleanSupplier sessionActive);
    /** 同一事务保存验证、CAS、预留终态和审计，取消、迟到或修订变化不得写成功。 */
    void commit(PlatformCapabilityVerificationReservation reservation,
            PlatformModelDeploymentVerificationResult verification, String outcome,
            int attemptedRequests, List<String> passedSampleDigests, BooleanSupplier sessionActive);
}
