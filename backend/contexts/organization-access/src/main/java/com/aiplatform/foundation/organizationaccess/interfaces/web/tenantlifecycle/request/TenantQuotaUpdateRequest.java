package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantQuotaUpdate;
import java.util.Map;
import java.util.Set;

/** 配额编辑的严格输入白名单，容量只接受十进制整数字节串。 */
public final class TenantQuotaUpdateRequest {

    private static final Set<String> FIELDS = Set.of("knowledgeBases", "documentsPerKnowledgeBase",
            "bytesPerKnowledgeBase", "maxFileBytes", "maxTextBytes", "expectedVersion");

    private TenantQuotaUpdateRequest() {
    }

    /** 从完整 JSON 对象创建编辑命令；拒绝额外字段与不精确数值。 */
    public static TenantQuotaUpdate from(Map<String, Object> value) {
        if (value == null || !value.keySet().equals(FIELDS)) {
            throw new IllegalArgumentException("配额保存请求包含未允许字段或缺少必填字段");
        }
        return new TenantQuotaUpdate(count(value, "knowledgeBases"), count(value, "documentsPerKnowledgeBase"),
                bytes(value, "bytesPerKnowledgeBase"), bytes(value, "maxFileBytes"),
                bytes(value, "maxTextBytes"), version(value));
    }

    private static int count(Map<String, Object> value, String field) {
        if (!(value.get(field) instanceof Integer number) || number < 1) {
            throw new IllegalArgumentException("配额数量必须为正整数且不能超出整数范围");
        }
        return number;
    }

    private static long bytes(Map<String, Object> value, String field) {
        if (!(value.get(field) instanceof String text) || !text.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("配额容量必须为正整数字节文本");
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("配额容量超出允许范围");
        }
    }

    private static long version(Map<String, Object> value) {
        var raw = value.get("expectedVersion");
        if (!(raw instanceof Integer || raw instanceof Long) || ((Number) raw).longValue() < 0) {
            throw new IllegalArgumentException("配额预期版本格式不正确");
        }
        return ((Number) raw).longValue();
    }
}
