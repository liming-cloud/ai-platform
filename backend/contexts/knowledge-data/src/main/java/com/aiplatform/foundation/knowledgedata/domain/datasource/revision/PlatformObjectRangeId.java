package com.aiplatform.foundation.knowledgedata.domain.datasource.revision;

import com.aiplatform.foundation.shared.kernel.domain.ValueObject;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * 平台签发的对象存储授权范围标识。
 *
 * <p>该值只是不透明能力标识，不能承载端点、桶名、对象键或访问凭据。</p>
 */
public record PlatformObjectRangeId(String value) implements ValueObject {

    private static final Pattern FORMAT = Pattern.compile("rng_[A-Za-z0-9]{16,128}");

    public PlatformObjectRangeId {
        Objects.requireNonNull(value, "平台对象范围标识不能为空");
        if (!FORMAT.matcher(value).matches()) {
            throw new IllegalArgumentException("平台对象范围标识格式不正确");
        }
    }
}
