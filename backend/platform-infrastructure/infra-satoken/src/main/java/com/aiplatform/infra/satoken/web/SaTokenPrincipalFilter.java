package com.aiplatform.infra.satoken.web;

import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.aiplatform.infra.satoken.codec.SaTokenPrincipalCodec;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Objects;
import java.util.Optional;
import org.springframework.web.filter.OncePerRequestFilter;

public class SaTokenPrincipalFilter extends OncePerRequestFilter {
    private final StpLogic stpLogic;

    public SaTokenPrincipalFilter() {
        this(StpUtil.getStpLogic());
    }

    SaTokenPrincipalFilter(StpLogic stpLogic) {
        this.stpLogic = Objects.requireNonNull(stpLogic, "Sa-Token 逻辑组件不能为空");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        clearSecurityContext();
        try {
            Optional<AuthenticatedPrincipal> principal = currentPrincipal();
            principal.ifPresent(authenticatedPrincipal -> {
                TenantContext.set(authenticatedPrincipal.tenantId());
                PrincipalContext.set(authenticatedPrincipal);
            });
            filterChain.doFilter(request, response);
        } finally {
            clearSecurityContext();
        }
    }

    private Optional<AuthenticatedPrincipal> currentPrincipal() {
        Object loginId = stpLogic.getLoginIdDefaultNull();
        if (loginId == null) {
            return Optional.empty();
        }
        return SaTokenPrincipalCodec.decode(loginId);
    }

    private static void clearSecurityContext() {
        TenantContext.clear();
        PrincipalContext.clear();
    }
}
