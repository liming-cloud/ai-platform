package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper;

import com.aiplatform.foundation.modelruntime.application.platformsupply.dto.verification.PlatformCapabilityVerificationReservation;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelCapabilityVerification;
import com.aiplatform.foundation.modelruntime.domain.platformsupply.model.PlatformModelDeployment;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 平台能力验证的短事务锁、整轮预留、最终事实和末尾审计。 */
public interface PlatformCapabilityVerificationMapper {
    /** 与停用／发布的乐观更新共用部署根锁。 */
    Long lockDeployment(@Param("id") long id);
    /** 新请求仅退休到期状态，不退还未知用量预留，也不重发旧请求。 */
    int expire(@Param("id") long id, @Param("now") Instant now);
    /** 在任何厂商请求前冻结整轮计划，活动唯一键排斥并发。 */
    int reserve(@Param("r") PlatformCapabilityVerificationReservation reservation, @Param("samples") String samples);
    /** 精确绑定与活动状态一致才允许最终提交。 */
    String lockReservation(@Param("r") PlatformCapabilityVerificationReservation reservation);
    /** 保存无正文、无凭据的不可变验证事实。 */
    int insertVerification(@Param("v") PlatformModelCapabilityVerification verification,
            @Param("declared") String declared, @Param("observed") String observed, @Param("elapsedMillis") long elapsedMillis);
    /** 只有精确草稿修订可以变为已验证。 */
    int updateVerified(@Param("d") PlatformModelDeployment deployment, @Param("expected") long expected);
    /** 原子记录稳定终态，失败及未知用量不自动退回预留。 */
    int finish(@Param("r") PlatformCapabilityVerificationReservation reservation, @Param("outcome") String outcome,
            @Param("count") int count, @Param("passed") String passed, @Param("now") Instant now);
    /** 事务最后写独立审计，插入失败须回滚全部成功事实。 */
    int audit(@Param("r") PlatformCapabilityVerificationReservation reservation,
            @Param("verificationId") long verificationId, @Param("succeeded") boolean succeeded, @Param("now") Instant now);
}
