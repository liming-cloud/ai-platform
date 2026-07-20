package com.aiplatform.modelhub.infrastructure.springai.adapter;

import com.aiplatform.modelhub.infrastructure.persistence.mapper.ModelRuntimeAuthorityDigestMapper;
import com.aiplatform.shared.kernel.Sha256Digests;
import java.util.Map;
import java.util.Objects;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

/** 将 effective options 统一交给 PostgreSQL JSONB canonical 规则签名。 */
@Component
public final class ModelRuntimeAuthorityDigest {
    private final ModelRuntimeAuthorityDigestMapper mapper;
    private final JsonMapper objectMapper;

    public ModelRuntimeAuthorityDigest(ModelRuntimeAuthorityDigestMapper mapper, JsonMapper objectMapper) {
        this.mapper = Objects.requireNonNull(mapper, "模型运行时 authority 摘要 Mapper 不能为空");
        this.objectMapper = Objects.requireNonNull(objectMapper, "JSON 序列化器不能为空");
    }

    public String digest(Map<String, Object> effectiveOptions) {
        Objects.requireNonNull(effectiveOptions, "模型 effective options 不能为空");
        try {
            return Sha256Digests.requireLowercase(
                    mapper.canonicalEffectiveOptionsDigest(objectMapper.writeValueAsString(effectiveOptions)),
                    "模型 effective options 摘要"
            );
        } catch (JacksonException exception) {
            throw new IllegalStateException("模型 effective options canonical 序列化失败", exception);
        }
    }
}
