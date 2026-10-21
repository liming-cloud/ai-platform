package com.aiplatform.foundation.modelruntime.application.platformsupply.port.out.verification;

import com.aiplatform.foundation.modelruntime.application.extension.facade.ProviderKind;
import com.aiplatform.foundation.modelruntime.application.extension.facade.verification.PlatformCapabilitySampleResult;
import com.aiplatform.foundation.modelruntime.application.extension.facade.request.PlatformCapabilitySampleRequest;

/** 独立文本重排平台端口，不经 Chat／Embedding 模型执行。 */
public interface PlatformReranker {
    /** 返回扩展拥有的运行协议，不构造租户身份。 */
    ProviderKind providerKind();
    /** 执行一个已受预算约束的合成样本；结果只在内存传递，不能自动重试或记录正文、原始响应。 */
    PlatformCapabilitySampleResult rerank(PlatformCapabilitySampleRequest request);
}
