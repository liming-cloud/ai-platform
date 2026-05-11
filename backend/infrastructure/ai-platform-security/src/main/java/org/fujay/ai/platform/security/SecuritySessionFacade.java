package org.fujay.ai.platform.security;

import cn.dev33.satoken.stp.StpUtil;
import org.springframework.stereotype.Component;

@Component
public class SecuritySessionFacade {

    private final AuthPrincipalCodec principalCodec;

    public SecuritySessionFacade(AuthPrincipalCodec principalCodec) {
        this.principalCodec = principalCodec;
    }

    public LoginToken login(AuthPrincipal principal) {
        StpUtil.login(principalCodec.encode(principal));
        return new LoginToken(StpUtil.getTokenName(), StpUtil.getTokenValue());
    }

    public AuthPrincipal currentPrincipal() {
        StpUtil.checkLogin();
        return principalCodec.decode(StpUtil.getLoginIdAsString());
    }

    public void logout() {
        StpUtil.logout();
    }
}
