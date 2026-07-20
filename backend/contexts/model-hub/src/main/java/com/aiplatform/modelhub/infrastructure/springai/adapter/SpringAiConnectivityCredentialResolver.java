package com.aiplatform.modelhub.infrastructure.springai.adapter;

import com.aiplatform.infra.springai.embedding.secret.SpringAiRuntimeSecretResolutionRequest;

/**
 * 仅供 model-hub 首次/复测连通性使用的凭证 capability。
 *
 * <p>该端口保持包可见，App、Knowledge 与通用 Embedding 运行时无法引用其较宽松的
 * “已审批但尚未 ACTIVE/PASSED”状态窗口。</p>
 */
@FunctionalInterface
interface SpringAiConnectivityCredentialResolver {
    String resolveProbeCredentialMaterial(SpringAiRuntimeSecretResolutionRequest request);
}
