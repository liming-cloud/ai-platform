package com.aiplatform.foundation.organizationaccess.interfaces.web.member.request;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/** 重置请求仅含新密码及读取版本，资源与操作者只来自路径和会话。 */
public final class MemberPasswordResetRequest implements AutoCloseable {
    private final char[] password;
    private final long version;
    private final long identityVersion;
    private MemberPasswordResetRequest(char[] password, long version, long identityVersion) {
        this.password = password; this.version = version; this.identityVersion = identityVersion;
    }
    public static MemberPasswordResetRequest from(Map<String, Object> body) {
        if (body == null || !body.keySet().equals(Set.of("password", "expectedVersion", "expectedIdentityVersion"))
                || !(body.get("password") instanceof String password)) throw new IllegalArgumentException("重置密码字段不合法");
        var version = MemberProvisioningRequest.version(body.get("expectedVersion"));
        var identity = MemberProvisioningRequest.version(body.get("expectedIdentityVersion"));
        if (password.isEmpty() || password.length() > 1024) throw new IllegalArgumentException("密码长度不合法");
        return new MemberPasswordResetRequest(password.toCharArray(), version, identity);
    }
    public char[] passwordCopy() { return password.clone(); }
    public long expectedVersion() { return version; }
    public long expectedIdentityVersion() { return identityVersion; }
    @Override public void close() { Arrays.fill(password, '\0'); }
}
