package com.aiplatform.identity.presentation.dto;

import jakarta.validation.constraints.NotNull;
import java.util.List;

/**
 * IAM 菜单 feature 绑定替换请求 DTO，使用完整列表表达最终绑定事实。
 */
public record IamMenuFeaturesReplaceRequest(
        @NotNull List<String> featureCodes
) {
}
