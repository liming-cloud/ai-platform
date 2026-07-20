package com.aiplatform.identity.domain.port;

import com.aiplatform.identity.domain.EnterpriseUser;
import com.aiplatform.identity.domain.LoginSession;
import java.time.Instant;

/**
 * 访问令牌签发端口，由 Sa-Token 基础设施适配器实现，领域层不依赖具体安全框架。
 */
public interface AccessTokenIssuer {
    LoginSession issue(EnterpriseUser user, Instant issuedAt);
}
