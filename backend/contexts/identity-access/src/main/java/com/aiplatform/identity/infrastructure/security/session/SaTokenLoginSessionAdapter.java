package com.aiplatform.identity.infrastructure.security.session;

import com.aiplatform.identity.domain.AccessToken;
import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.LoginSession;
import com.aiplatform.identity.domain.port.AccessTokenIssuer;
import com.aiplatform.identity.domain.port.LoginSessionRepository;
import com.aiplatform.infra.satoken.codec.SaTokenPrincipalCodec;
import com.aiplatform.infra.satoken.gateway.SaTokenSessionGateway;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 身份上下文的 Sa-Token 适配器，使用租户隔离 loginId 创建和恢复登录会话。
 */
@Component
public class SaTokenLoginSessionAdapter implements AccessTokenIssuer, LoginSessionRepository {
    private final SaTokenSessionGateway sessionGateway;
    private final Duration tokenTtl;

    @Autowired
    public SaTokenLoginSessionAdapter(
            SaTokenSessionGateway sessionGateway,
            @Value("${aip.security.session.token-ttl-seconds:28800}") long tokenTtlSeconds
    ) {
        this(sessionGateway, Duration.ofSeconds(tokenTtlSeconds));
    }

    SaTokenLoginSessionAdapter(SaTokenSessionGateway sessionGateway, Duration tokenTtl) {
        this.sessionGateway = Objects.requireNonNull(sessionGateway, "Sa-Token 会话网关不能为空");
        if (tokenTtl == null || tokenTtl.isNegative() || tokenTtl.isZero()) {
            throw new IllegalArgumentException("令牌有效期必须为正数");
        }
        this.tokenTtl = tokenTtl;
    }

    @Override
    public LoginSession issue(EnterpriseUser user, Instant issuedAt) {
        Objects.requireNonNull(user, "用户不能为空");
        Objects.requireNonNull(issuedAt, "签发时间不能为空");
        String tokenValue = requireText(
                sessionGateway.createLoginSession(
                        SaTokenPrincipalCodec.userLoginId(user.tenantId(), user.userId()),
                        tokenTtl.toSeconds()
                ),
                "访问令牌"
        );
        return new LoginSession(
                user.tenantId(),
                tokenValue,
                user.userId(),
                new AccessToken(tokenValue, issuedAt.plus(tokenTtl)),
                issuedAt,
                true
        );
    }

    @Override
    public Optional<LoginSession> findActiveByToken(TenantId tenantId, String tokenValue) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        String normalizedTokenValue = requireText(tokenValue, "访问令牌");
        return sessionGateway.findLoginIdByToken(normalizedTokenValue)
                .flatMap(loginId -> toSession(tenantId, normalizedTokenValue, loginId));
    }

    @Override
    public void revokeByToken(TenantId tenantId, String tokenValue) {
        Objects.requireNonNull(tenantId, "租户ID不能为空");
        String normalizedTokenValue = requireText(tokenValue, "访问令牌");
        findActiveByToken(tenantId, normalizedTokenValue)
                .ifPresent(session -> sessionGateway.revokeToken(normalizedTokenValue));
    }

    private Optional<LoginSession> toSession(TenantId tenantId, String tokenValue, String loginId) {
        Optional<AuthenticatedPrincipal> principal = SaTokenPrincipalCodec.decode(loginId);
        if (principal.isEmpty() || !tenantId.equals(principal.orElseThrow().tenantId())) {
            return Optional.empty();
        }
        long timeoutSeconds = sessionGateway.getTokenTimeoutSeconds(tokenValue);
        if (timeoutSeconds <= 0) {
            return Optional.empty();
        }
        Instant now = Instant.now();
        return Optional.of(new LoginSession(
                tenantId,
                tokenValue,
                principal.orElseThrow().actorId(),
                new AccessToken(tokenValue, now.plusSeconds(timeoutSeconds)),
                now,
                true
        ));
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }

}
