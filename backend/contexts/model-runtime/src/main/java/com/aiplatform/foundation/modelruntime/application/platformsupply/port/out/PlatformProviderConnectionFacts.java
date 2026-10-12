package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import java.util.Map;
import java.util.Set;

/** 批量读取平台连接的配置与发布事实；不读取密文、指纹或凭据引用。 */
@FunctionalInterface
public interface PlatformProviderConnectionFacts {
    /** 仅投影指定连接，调用者仍须先检查平台身份与版本一致性。 */
    Map<Long, Fact> find(Set<Long> connectionIds);

    /** 与连接修订绑定的存在性事实，不代表密钥有效或厂商当前可用。 */
    record Fact(long connectionId, long version, boolean credentialConfigured,
            boolean discoveryCredentialsConfigured, boolean published) {}
}
