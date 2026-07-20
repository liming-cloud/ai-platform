package com.aiplatform.infra.springai.embedding.secret;

/**
 * Spring AI 运行时凭证材料解析端口，只允许基础设施在调用前按引用解析明文凭证。
 */
@FunctionalInterface
public interface SpringAiRuntimeSecretResolver {
    String resolveCredentialMaterial(SpringAiRuntimeSecretResolutionRequest request);
}
