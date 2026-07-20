package com.aiplatform.infra.springai.model.runtime;

import com.aiplatform.infra.springai.model.result.SpringAiModelConnectivityResult;

/**
 * Spring AI 模型探测客户端，只允许通过 Spring AI 模型抽象访问供应商运行时。
 */
public interface SpringAiModelProbeClient {
    boolean supports(SpringAiModelRuntimeProbe probe);

    SpringAiModelConnectivityResult probe(SpringAiModelRuntimeProbe probe);
}
