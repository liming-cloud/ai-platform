package com.aiplatform.infra.springai.model.result;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Spring AI 模型 options 校验结果，集中记录策略发现的阻断项。
 */
public record SpringAiModelValidationResult(boolean valid, List<String> violations) {
    public SpringAiModelValidationResult {
        Objects.requireNonNull(violations, "校验失败明细不能为空");
        List<String> normalized = new ArrayList<>(violations.size());
        for (String violation : violations) {
            if (violation == null || violation.isBlank()) {
                throw new IllegalArgumentException("校验失败明细必须是非空字符串");
            }
            normalized.add(violation.trim());
        }
        if (valid && !normalized.isEmpty()) {
            throw new IllegalArgumentException("通过结果不得携带校验失败明细");
        }
        if (!valid && normalized.isEmpty()) {
            throw new IllegalArgumentException("失败结果必须携带校验失败明细");
        }
        violations = List.copyOf(normalized);
    }

    public static SpringAiModelValidationResult passed() {
        return new SpringAiModelValidationResult(true, List.of());
    }

    public static SpringAiModelValidationResult failed(List<String> violations) {
        return new SpringAiModelValidationResult(false, violations);
    }

    public String summary() {
        return String.join("；", violations);
    }
}
