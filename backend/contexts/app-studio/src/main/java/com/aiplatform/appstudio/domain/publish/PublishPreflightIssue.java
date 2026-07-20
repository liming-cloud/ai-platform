package com.aiplatform.appstudio.domain.publish;

/**
 * 发布预检问题使用稳定 code 供 API、审计和前端定位，不携带资源名称等敏感信息。
 */
public record PublishPreflightIssue(
        String code,
        String message,
        String resourceType,
        String resourceId
) {
    public PublishPreflightIssue {
        code = requireText(code, "问题编码");
        message = requireText(message, "问题说明");
        resourceType = requireText(resourceType, "资源类型");
        resourceId = requireText(resourceId, "资源 ID");
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
