package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification;

import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilitySampleResult;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformCapabilitySampleRequest;

/** 标准 Chat／Embedding 平台样本；禁止把 RERANK 作为兼容协议处理。 */
public interface PlatformCapabilitySampleExecutor {
    /** 返回扩展拥有的运行协议，不构造租户身份。 */
    ProviderKind providerKind();
    /** 执行一个已受预算约束的合成样本；结果只在内存传递，不能自动重试或记录正文、向量。 */
    PlatformCapabilitySampleResult execute(PlatformCapabilitySampleRequest request);
}
