package com.aiplatform.identity.application;

import com.aiplatform.identity.application.command.InviteTenantUserCommand;
import com.aiplatform.identity.application.port.UserInvitationCommandPort;
import com.aiplatform.identity.application.port.PasswordHashEncoder;
import com.aiplatform.identity.application.result.UserActivationResult;
import com.aiplatform.identity.application.result.UserInvitationResult;
import com.aiplatform.identity.application.result.UserInvitationOverviewResult;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.audit.model.AuditEvent;
import com.aiplatform.shared.audit.port.AuditEventPublisher;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.List;
import java.util.Locale;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserInvitationApplicationService {
    private static final Duration INVITATION_TTL = Duration.ofHours(24);
    private final UserInvitationCommandPort commandPort;
    private final AuditEventPublisher auditPublisher;
    private final Clock clock;
    private final byte[] signingKey;
    private final PasswordHashEncoder passwordHashEncoder;
    private final String activationBaseUrl;

    public UserInvitationApplicationService(UserInvitationCommandPort commandPort, AuditEventPublisher auditPublisher, Clock clock,
            PasswordHashEncoder passwordHashEncoder,
            @Value("${aip.identity.invitation-signing-key}") String signingKey,
            @Value("${aip.identity.activation-base-url:http://127.0.0.1:5173/activate}") String activationBaseUrl) {
        this.commandPort = Objects.requireNonNull(commandPort, "用户邀请写入端口不能为空");
        this.auditPublisher = Objects.requireNonNull(auditPublisher, "审计发布端口不能为空");
        this.clock = Objects.requireNonNull(clock, "时钟不能为空");
        this.signingKey = requireStrongSecret(signingKey, "邀请签名密钥", 32).getBytes(StandardCharsets.UTF_8);
        this.passwordHashEncoder = Objects.requireNonNull(passwordHashEncoder, "密码哈希编码器不能为空");
        this.activationBaseUrl = Objects.requireNonNull(activationBaseUrl, "激活地址不能为空").replaceAll("/+$", "");
    }

    @Transactional
    public UserActivationResult activate(String token, String password) {
        if (token == null || token.isBlank()) throw new IllegalArgumentException("激活令牌不能为空");
        if (password == null || password.length() < 15 || password.length() > 128) {
            throw new IllegalArgumentException("密码长度必须为 15 到 128 个字符");
        }
        UserActivationResult result = commandPort.activate(digest(token.trim()), passwordHashEncoder.encode(password), Instant.now(clock));
        auditPublisher.publish(new AuditEvent(UUID.randomUUID().toString(), result.tenantId(), result.userId(),
                "IAM_USER", result.userId(), "USER_INVITATION_ACTIVATED", "MEDIUM", null, UUID.randomUUID().toString(), Instant.now(clock)));
        return result;
    }

    @Transactional
    public UserInvitationResult invite(InviteTenantUserCommand command) {
        Objects.requireNonNull(command, "用户邀请命令不能为空");
        String invitationId = UUID.randomUUID().toString();
        Instant now = Instant.now(clock);
        String token = issueToken(invitationId);
        UserInvitationResult result = commandPort.createInvitation(command, invitationId, digest(token),
                activationBaseUrl + "?token=" + token, now.plus(INVITATION_TTL));
        auditPublisher.publish(new AuditEvent(UUID.randomUUID().toString(), command.tenantId().value(), command.actorId(),
                "IAM_USER", result.userId(), "USER_INVITATION_CREATED", "MEDIUM", null, command.traceId(), now,
                Map.of("invitationId", invitationId, "deliveryStatus", result.deliveryStatus())));
        return result;
    }

    public List<UserInvitationOverviewResult> list(TenantId tenantId) {
        return commandPort.list(Objects.requireNonNull(tenantId, "租户 ID 不能为空"));
    }

    @Transactional
    public UserInvitationResult reissue(TenantId tenantId, String invitationId, String actorId) {
        String normalizedInvitationId = requireText(invitationId, "邀请 ID");
        Instant now = Instant.now(clock);
        String token = issueToken(normalizedInvitationId + "." + UUID.randomUUID());
        UserInvitationResult result = commandPort.reissue(tenantId, normalizedInvitationId, digest(token),
                activationBaseUrl + "?token=" + token, now.plus(INVITATION_TTL));
        auditPublisher.publish(new AuditEvent(UUID.randomUUID().toString(), tenantId.value(), actorId,
                "IAM_USER", result.userId(), "USER_INVITATION_REISSUED", "MEDIUM", null, UUID.randomUUID().toString(), now));
        return result;
    }

    @Transactional
    public void revoke(TenantId tenantId, String invitationId, String actorId, String traceId, String reason) {
        TenantId normalizedTenantId = Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        String normalizedInvitationId = requireText(invitationId, "邀请 ID");
        String normalizedActorId = requireText(actorId, "操作者 ID");
        String normalizedTraceId = requireText(traceId, "调用链 ID");
        String normalizedReason = requireText(reason, "撤销原因");
        if (normalizedReason.length() > 500) throw new IllegalArgumentException("撤销原因不能超过 500 个字符");
        commandPort.revoke(normalizedTenantId, normalizedInvitationId, normalizedActorId);
        auditPublisher.publish(new AuditEvent(UUID.randomUUID().toString(), normalizedTenantId.value(), normalizedActorId,
                "IAM_USER_INVITATION", normalizedInvitationId, "USER_INVITATION_REVOKED", "MEDIUM", null,
                normalizedTraceId, Instant.now(clock), Map.of("reason", normalizedReason)));
    }

    private String issueToken(String invitationId) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(signingKey, "HmacSHA256"));
            return invitationId + "." + Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(invitationId.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("邀请令牌签名失败", exception);
        }
    }

    private static String digest(String token) {
        try {
            return Base64.getUrlEncoder().withoutPadding().encodeToString(MessageDigest.getInstance("SHA-256")
                    .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("SHA-256 不可用", exception);
        }
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(fieldName + "不能为空");
        return value.trim();
    }

    private static String requireStrongSecret(String value, String fieldName, int minimumLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        if (!value.equals(value.trim())) {
            throw new IllegalArgumentException(fieldName + "不能包含首尾空白");
        }
        String secret = value;
        String comparable = secret.toLowerCase(Locale.ROOT);
        if (comparable.startsWith("local-dev")
                || comparable.startsWith("replace-")
                || comparable.startsWith("change-")
                || comparable.startsWith("changeme")
                || comparable.startsWith("example")
                || comparable.startsWith("password")) {
            throw new IllegalArgumentException(fieldName + "不能使用占位或已知弱值");
        }
        if (secret.length() < minimumLength) {
            throw new IllegalArgumentException(fieldName + "长度不能少于 " + minimumLength + " 个字符");
        }
        return secret;
    }
}
