package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification;

import com.aiplatform.foundation.security.secret.SecretValue;

/** 只解析精确已发布连接修订的推理 Key，不读取其他修订或方舟独立发现 AK/SK。 */
@FunctionalInterface
public interface PlatformCapabilityCredentialResolver {
    /**
     * 由一次数据库快照核对冻结修订并读取密文；返回值仅在当前样本内存中使用。
     * @throws IllegalStateException 来源修订、发布或推理凭据不可用。
     */
    SecretValue resolve(String reference, long expectedConnectionRevision);
}
