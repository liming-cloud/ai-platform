package com.aiplatform.foundation.modelruntime.api.model;

import com.aiplatform.foundation.kernel.tenant.TenantId;
import com.aiplatform.foundation.security.secret.SecretReference;
import java.net.URI;
import java.time.Duration;
import java.util.Objects;

/**
 * 供应商连接探测的公共输入。
 *
 * <p>该契约只允许传递密钥引用。真实凭据由基础设施适配器在调用边界按需读取，
 * 不得进入公共接口、数据库或审计记录。</p>
 */
public record ProviderProbeRequest(
        TenantId tenantId,
        ProviderKind providerKind,
        URI endpoint,
        SecretReference credentialReference,
        String modelCode,
        Duration timeout) {

    public ProviderProbeRequest {
        Objects.requireNonNull(tenantId, "租户标识不能为空");
        Objects.requireNonNull(providerKind, "供应商类型不能为空");
        Objects.requireNonNull(endpoint, "供应商地址不能为空");
        Objects.requireNonNull(credentialReference, "密钥引用不能为空");
        Objects.requireNonNull(modelCode, "探测模型编码不能为空");
        Objects.requireNonNull(timeout, "探测超时不能为空");
        if (modelCode.isBlank() || modelCode.length() > 256) {
            throw new IllegalArgumentException("探测模型编码格式不正确");
        }
        for (var index = 0; index < modelCode.length(); index++) {
            if (Character.isWhitespace(modelCode.charAt(index))
                    || Character.isISOControl(modelCode.charAt(index))) {
                throw new IllegalArgumentException("探测模型编码不能包含空白或控制字符");
            }
        }
        if (timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("探测超时必须大于零");
        }
    }
}
