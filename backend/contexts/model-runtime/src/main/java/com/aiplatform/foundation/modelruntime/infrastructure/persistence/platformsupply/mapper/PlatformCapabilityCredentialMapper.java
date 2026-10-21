package com.aiplatform.foundation.modelruntime.infrastructure.persistence.platformsupply.mapper;

import com.aiplatform.foundation.security.secret.AesGcmSecretCipher;
import java.time.Instant;
import org.apache.ibatis.annotations.Param;

/** 一次安全投影绑定连接修订与推理密文，不查询密码或发现用途的凭据。 */
public interface PlatformCapabilityCredentialMapper {
    /** 来源轮换、停用、未发布或过期时无结果；不回退当前其他修订。 */
    AesGcmSecretCipher.EncryptedSecret resolveForRevision(@Param("id") long id,
            @Param("revision") long revision, @Param("now") Instant now);
}
