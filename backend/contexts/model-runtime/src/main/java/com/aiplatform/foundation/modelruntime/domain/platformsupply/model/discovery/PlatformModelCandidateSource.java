package com.aiplatform.foundation.modelruntime.domain.platformsupply.model.discovery;

import java.util.Objects;

/** 发现候选的非秘密来源凭证；与发现根一同冻结，不能由管理员参数声明替代。 */
public record PlatformModelCandidateSource(String profile, String listSource, String mappingVersion, String mappingDigest) {
    /** 当前已接受受控映射；历史来源仍可恢复，但不能参与新的草稿选择。 */
    public static final String CURRENT_MAPPING_VERSION = "siliconflow-rerank-2026-10-21";
    /** 对版本和精确模型／类型／能力映射的规范正文计算 SHA-256，不含秘密。 */
    public static final String CURRENT_MAPPING_DIGEST = "8ddabe5d591868ba2399a2d1ae7e847f0ab05f0fb8d08b51202849515f2b2c1e";

    /**
     * 拒绝不完整或伪造的来源；仅保存已接受的固定官方列表与受控映射摘要。
     * @throws IllegalArgumentException 来源、版本或摘要不符合固定合同。
     */
    public PlatformModelCandidateSource {
        if (!"SILICONFLOW_RERANK_MODELS_API".equals(profile)
                || !"https://api.siliconflow.cn/v1/models?sub_type=reranker".equals(listSource)
                || !Objects.requireNonNull(mappingVersion, "映射版本不能为空").matches("siliconflow-rerank-[0-9]{4}-[0-9]{2}-[0-9]{2}")
                || !Objects.requireNonNull(mappingDigest, "映射摘要不能为空").matches("[0-9a-f]{64}")) {
            throw new IllegalArgumentException("候选来源与已接受发现合同不一致");
        }
    }
}
