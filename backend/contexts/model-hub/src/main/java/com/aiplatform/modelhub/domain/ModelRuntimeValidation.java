package com.aiplatform.modelhub.domain;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * 模型运行时配置校验结果，由基础设施策略返回给应用层作为入库前门禁。
 */
public record ModelRuntimeValidation(boolean valid, List<String> violations) {
    public ModelRuntimeValidation {
        Objects.requireNonNull(violations, "模型配置校验明细不能为空");
        List<String> normalized = new ArrayList<>(violations.size());
        for (String violation : violations) {
            if (violation == null || violation.isBlank()) {
                throw new IllegalArgumentException("模型配置校验明细必须是非空字符串");
            }
            normalized.add(violation.trim());
        }
        if (valid && !normalized.isEmpty()) {
            throw new IllegalArgumentException("通过结果不得携带模型配置校验失败明细");
        }
        if (!valid && normalized.isEmpty()) {
            throw new IllegalArgumentException("失败结果必须携带模型配置校验失败明细");
        }
        violations = List.copyOf(normalized);
    }

    public static ModelRuntimeValidation passed() {
        return new ModelRuntimeValidation(true, List.of());
    }

    public static ModelRuntimeValidation failed(List<String> violations) {
        return new ModelRuntimeValidation(false, violations);
    }

    public String summary() {
        return String.join("；", violations);
    }
}
