package com.aiplatform.foundation.organizationaccess.interfaces.web.member.request;

import com.aiplatform.foundation.organizationaccess.application.member.dto.MemberProvisioningCommand;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;

/** 严格的新成员开户合同；不接受主体、角色或租户输入，密码各路径清除。 */
public final class MemberProvisioningRequest implements AutoCloseable {
    private static final Set<String> FIELDS = Set.of("loginName", "password", "conversationFeatures", "assistantIds",
            "expectedTenantFeatureVersion", "expectedAuthorizationRevision");
    private final MemberProvisioningCommand command;
    private final char[] password;
    private MemberProvisioningRequest(MemberProvisioningCommand command, char[] password) {
        this.command = command; this.password = password;
    }
    /** 先校验所有非秘密字段，最后复制短生命周期密码。 */
    public static MemberProvisioningRequest from(Map<String, Object> body) {
        if (body == null || !body.keySet().equals(FIELDS)
                || !(body.get("loginName") instanceof String login)
                || !(body.get("password") instanceof String password)
                || !(body.get("conversationFeatures") instanceof Map<?, ?> values)
                || !values.keySet().equals(MemberProvisioningCommand.FEATURE_CODES)
                || !(body.get("assistantIds") instanceof java.util.List<?> selected)) {
            throw new IllegalArgumentException("成员开户字段不合法");
        }
        var features = new HashMap<String, Boolean>();
        for (var code : MemberProvisioningCommand.FEATURE_CODES) {
            if (!(values.get(code) instanceof Boolean enabled)) throw new IllegalArgumentException("成员功能必须为布尔值");
            features.put(code, enabled);
        }
        var assistants = new HashSet<Long>();
        for (var item : selected) {
            if (!(item instanceof String text) || !text.matches("[1-9][0-9]{0,18}")) {
                throw new IllegalArgumentException("助手引用必须为正整数文本");
            }
            try {
                if (!assistants.add(Long.parseLong(text))) throw new IllegalArgumentException("助手引用不能重复");
            } catch (NumberFormatException invalid) { throw new IllegalArgumentException("助手引用超出允许范围"); }
        }
        var command = new MemberProvisioningCommand(login, features, assistants,
                version(body.get("expectedTenantFeatureVersion")), version(body.get("expectedAuthorizationRevision")));
        if (password.isEmpty() || password.length() > 1024) throw new IllegalArgumentException("密码长度不合法");
        return new MemberProvisioningRequest(command, password.toCharArray());
    }
    public MemberProvisioningCommand command() { return command; }
    public char[] passwordCopy() { return password.clone(); }
    @Override public void close() { Arrays.fill(password, '\0'); }
    /** 版本是精确非负整数，不能接受浮点、布尔或隐式数字转换。 */
    public static long version(Object value) {
        if (value instanceof Integer number && number >= 0) return number.longValue();
        if (value instanceof Long number && number >= 0) return number;
        throw new IllegalArgumentException("版本必须为非负整数");
    }
}
