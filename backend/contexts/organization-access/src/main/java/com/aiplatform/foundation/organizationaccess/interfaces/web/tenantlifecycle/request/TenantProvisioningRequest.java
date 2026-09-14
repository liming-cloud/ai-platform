package com.aiplatform.foundation.organizationaccess.interfaces.web.tenantlifecycle.request;

import com.aiplatform.foundation.organizationaccess.application.tenantlifecycle.command.TenantProvisioningCommand;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/** 严格开户请求；角色与操作者只来自会话，密码不参与字符串表示且请求结束即清除。 */
public final class TenantProvisioningRequest implements AutoCloseable {
    private static final Set<String> FIELDS = Set.of("tenantCode", "displayName", "administrator", "quotas");
    private static final Set<String> ADMIN_FIELDS = Set.of("loginName", "password");
    private static final Set<String> QUOTA_FIELDS = Set.of("knowledgeBases", "documentsPerKnowledgeBase",
            "bytesPerKnowledgeBase", "maxFileBytes", "maxTextBytes");
    private final TenantProvisioningCommand command;
    private final char[] password;

    private TenantProvisioningRequest(TenantProvisioningCommand command, char[] password) {
        this.command = command;
        this.password = password;
    }

    /** 从严格 JSON 对象恢复非秘密命令，所有校验完成后才复制密码数组。 */
    public static TenantProvisioningRequest from(Map<String, Object> payload) {
        var root = object(payload, FIELDS);
        var administrator = object(root.get("administrator"), ADMIN_FIELDS);
        var quotas = object(root.get("quotas"), QUOTA_FIELDS);
        var command = new TenantProvisioningCommand(text(root, "tenantCode"), text(root, "displayName"),
                text(administrator, "loginName"), positiveInteger(quotas, "knowledgeBases"),
                positiveInteger(quotas, "documentsPerKnowledgeBase"), bytes(quotas, "bytesPerKnowledgeBase"),
                bytes(quotas, "maxFileBytes"), bytes(quotas, "maxTextBytes"));
        return new TenantProvisioningRequest(command, text(administrator, "password").toCharArray());
    }

    /** 取得无秘密的开户命令。 */
    public TenantProvisioningCommand command() { return command; }

    /** 返回独立短生命周期副本，应用服务负责在所有路径清理。 */
    public char[] passwordCopy() { return password.clone(); }

    /** 释放请求持有的密码；不会保留可回显副本。 */
    @Override public void close() { Arrays.fill(password, '\0'); }

    private static Map<?, ?> object(Object value, Set<String> fields) {
        if (!(value instanceof Map<?, ?> map) || !map.keySet().equals(fields)) {
            throw new IllegalArgumentException("创建租户请求包含未允许字段或缺失必填字段");
        }
        return map;
    }

    private static String text(Map<?, ?> map, String field) {
        if (!(map.get(field) instanceof String value)) {
            throw new IllegalArgumentException("创建租户文本字段格式不正确");
        }
        return value;
    }

    private static int positiveInteger(Map<?, ?> map, String field) {
        if (!(map.get(field) instanceof Integer value) || value <= 0) {
            throw new IllegalArgumentException("配额数量必须为正整数且不能超出整数范围");
        }
        return value;
    }

    private static long bytes(Map<?, ?> map, String field) {
        var value = text(map, field);
        if (!value.matches("[1-9][0-9]{0,18}")) {
            throw new IllegalArgumentException("配额容量必须为正整数字节文本");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException invalid) {
            throw new IllegalArgumentException("配额容量超出允许范围");
        }
    }
}
