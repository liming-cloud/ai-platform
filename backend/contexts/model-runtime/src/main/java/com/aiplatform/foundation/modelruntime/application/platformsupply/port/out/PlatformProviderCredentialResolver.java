package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out;

import com.aiplatform.foundation.security.secret.SecretValue;

/**
 * 仅为平台模型供应链解析平台专用凭据。
 *
 * <p>该端口与租户运行时的通用密钥解析器隔离，避免平台级推理密钥和控制面 AK/SK 被租户调用链读取。</p>
 */
@FunctionalInterface
public interface PlatformProviderCredentialResolver {

    /**
     * 解析短生命周期秘密；调用方必须在使用后关闭返回值。
     *
     * @param reference 平台专用凭据引用
     * @return 可主动清零的秘密值
     */
    SecretValue resolve(String reference);
}
