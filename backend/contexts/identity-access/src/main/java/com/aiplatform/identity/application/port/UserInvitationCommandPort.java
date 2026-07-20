package com.aiplatform.identity.application.port;

import com.aiplatform.identity.application.command.InviteTenantUserCommand;
import com.aiplatform.identity.application.result.UserInvitationResult;
import com.aiplatform.identity.application.result.UserActivationResult;
import com.aiplatform.identity.application.result.UserInvitationOverviewResult;
import com.aiplatform.shared.kernel.TenantId;
import java.util.List;
import java.time.Instant;

public interface UserInvitationCommandPort {
    UserInvitationResult createInvitation(InviteTenantUserCommand command, String invitationId, String tokenDigest, String activationUrl, Instant expiresAt);

    UserActivationResult activate(String tokenDigest, String passwordHash, Instant activatedAt);

    List<UserInvitationOverviewResult> list(TenantId tenantId);

    UserInvitationResult reissue(TenantId tenantId, String invitationId, String tokenDigest, String activationUrl, Instant expiresAt);

    void revoke(TenantId tenantId, String invitationId, String actorId);
}
