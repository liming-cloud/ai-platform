package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.application.command.InviteTenantUserCommand;
import com.aiplatform.identity.application.port.UserInvitationCommandPort;
import com.aiplatform.identity.application.result.UserInvitationResult;
import com.aiplatform.identity.application.result.UserActivationResult;
import com.aiplatform.identity.application.result.UserInvitationOverviewResult;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.identity.infrastructure.notification.InvitationNotificationPayloadCipher;
import java.util.List;
import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import java.time.Instant;
import java.util.Objects;
import org.springframework.stereotype.Repository;

@Repository
public class UserInvitationCommandAdapter implements UserInvitationCommandPort {
    private static final String PENDING_PASSWORD_HASH = "pbkdf2-sha256$1$cGVuZGluZw==$cGVuZGluZw==";
    private final IamAuthorizationMapper mapper;
    private final InvitationNotificationPayloadCipher payloadCipher;

    public UserInvitationCommandAdapter(
            IamAuthorizationMapper mapper,
            InvitationNotificationPayloadCipher payloadCipher
    ) {
        this.mapper = Objects.requireNonNull(mapper, "授权 Mapper 不能为空");
        this.payloadCipher = Objects.requireNonNull(payloadCipher, "邀请通知密文服务不能为空");
    }

    @Override
    public UserInvitationResult createInvitation(InviteTenantUserCommand command, String invitationId, String tokenDigest, String activationUrl, Instant expiresAt) {
        Long tenantId = parseRequiredLong(command.tenantId().value(), "租户 ID");
        Long actorId = parseRequiredLong(command.actorId(), "操作人 ID");
        Long roleId = parseRequiredLong(command.roleId(), "角色 ID");
        if (mapper.countInvitationAssignableRole(tenantId, roleId) != 1) {
            throw new IllegalArgumentException("初始角色不存在、停用或属于系统角色");
        }
        if (mapper.countTenantUserByUsernameOrEmail(tenantId, command.username().trim(), command.email().trim()) > 0) {
            throw new IllegalArgumentException("账号或邮箱已存在");
        }
        Long userId = Objects.requireNonNull(mapper.insertPendingActivationUser(tenantId, command.username().trim(), command.displayName().trim(), command.email().trim(), PENDING_PASSWORD_HASH, actorId), "待激活用户 ID 不能为空");
        mapper.insertIamUserRole(tenantId, userId, roleId, "invitation");
        mapper.insertUserInvitation(invitationId, tenantId, userId, roleId, command.email().trim(), tokenDigest, expiresAt, actorId);
        mapper.insertInvitationNotification(
                invitationId,
                tenantId,
                command.email().trim(),
                payloadCipher.encrypt(command.tenantId().value(), invitationId, activationUrl)
        );
        return new UserInvitationResult(invitationId, String.valueOf(userId), "PENDING_ACTIVATION", "QUEUED", expiresAt);
    }

    @Override
    public UserActivationResult activate(String tokenDigest, String passwordHash, Instant activatedAt) {
        var invitation = mapper.findPendingUserInvitationByDigest(tokenDigest);
        if (invitation == null || invitation.getExpiresAt().isBefore(activatedAt)) {
            throw new IllegalArgumentException("激活邀请无效或已过期");
        }
        if (mapper.activateInvitedUser(invitation.getTenantId(), invitation.getUserId(), passwordHash) != 1
                || mapper.acceptUserInvitation(invitation.getId(), activatedAt) != 1) {
            throw new IllegalStateException("邀请激活状态已变化，请重新获取邀请");
        }
        mapper.clearInvitationNotificationPayloads(invitation.getTenantId(), invitation.getId(), "ACTIVATED");
        return new UserActivationResult(invitation.getId(), String.valueOf(invitation.getUserId()), String.valueOf(invitation.getTenantId()), "ACTIVE");
    }

    @Override
    public List<UserInvitationOverviewResult> list(TenantId tenantId) {
        return mapper.findUserInvitations(parseRequiredLong(tenantId.value(), "租户 ID")).stream().map(row -> new UserInvitationOverviewResult(
                row.getInvitationId(), String.valueOf(row.getUserId()), row.getEmail(), row.getStatus(), row.getDeliveryStatus(),
                row.getDeliveryAttempts(), row.getDeliveryError(), row.getExpiresAt(), row.getCreatedAt())).toList();
    }

    @Override
    public UserInvitationResult reissue(TenantId tenantId, String invitationId, String tokenDigest, String activationUrl, Instant expiresAt) {
        Long databaseTenantId = parseRequiredLong(tenantId.value(), "租户 ID");
        var invitation = mapper.findUserInvitation(databaseTenantId, invitationId);
        if (invitation == null || mapper.reissueUserInvitation(databaseTenantId, invitationId, tokenDigest, expiresAt) != 1) {
            throw new IllegalArgumentException("邀请不存在或当前状态不允许重发");
        }
        String encryptedPayload = payloadCipher.encrypt(tenantId.value(), invitationId, activationUrl);
        mapper.clearInvitationNotificationPayloads(databaseTenantId, invitationId, "SUPERSEDED");
        mapper.insertInvitationNotification(invitationId, databaseTenantId, invitation.getEmail(), encryptedPayload);
        return new UserInvitationResult(invitationId, String.valueOf(invitation.getUserId()), "PENDING_ACTIVATION", "QUEUED", expiresAt);
    }

    @Override
    public void revoke(TenantId tenantId, String invitationId, String actorId) {
        Long databaseTenantId = parseRequiredLong(tenantId.value(), "租户 ID");
        var invitation = mapper.findUserInvitation(databaseTenantId, invitationId);
        if (invitation == null || mapper.revokeUserInvitation(databaseTenantId, invitationId) != 1) {
            throw new IllegalArgumentException("邀请不存在或当前状态不允许撤销");
        }
        if (mapper.disablePendingInvitationUser(databaseTenantId, invitation.getUserId(), parseRequiredLong(actorId, "操作者 ID")) != 1) {
            throw new IllegalStateException("待激活用户停用失败");
        }
        mapper.clearInvitationNotificationPayloads(databaseTenantId, invitationId, "CANCELLED");
    }
}
