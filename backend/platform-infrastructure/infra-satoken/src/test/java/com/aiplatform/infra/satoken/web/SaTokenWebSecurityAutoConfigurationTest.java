package com.aiplatform.infra.satoken.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.context.SaTokenContext;
import cn.dev33.satoken.filter.SaServletFilter;
import cn.dev33.satoken.fun.strategy.SaRouteMatchFunction;
import cn.dev33.satoken.exception.NotPermissionException;
import cn.dev33.satoken.strategy.SaStrategy;
import cn.dev33.satoken.stp.StpLogic;
import com.aiplatform.infra.satoken.web.permission.config.RoutePermissionProperties;
import com.aiplatform.infra.satoken.web.permission.model.RoutePermissionRule;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.http.server.PathContainer;
import org.springframework.web.util.pattern.PathPatternParser;

class SaTokenWebSecurityAutoConfigurationTest {
    private SaTokenContext originalSaTokenContext;
    private SaRouteMatchFunction originalRouteMatcher;

    @BeforeEach
    void setUp() {
        originalSaTokenContext = SaManager.getSaTokenContext();
        originalRouteMatcher = SaStrategy.instance.routeMatcher;
        SaTokenContext testContext = mock(SaTokenContext.class);
        when(testContext.isValid()).thenReturn(true);
        SaManager.setSaTokenContext(testContext);
        SaStrategy.instance.routeMatcher = (pattern, path) -> PathPatternParser.defaultInstance
                .parse(pattern)
                .matches(PathContainer.parsePath(path));
    }

    @AfterEach
    void tearDown() {
        SaManager.setSaTokenContext(originalSaTokenContext);
        SaStrategy.instance.routeMatcher = originalRouteMatcher;
        TenantContext.clear();
        PrincipalContext.clear();
    }

    @Test
    void routePermissionFilterRunsBeforePrincipalContextFilter() {
        SaTokenWebSecurityAutoConfiguration autoConfiguration = new SaTokenWebSecurityAutoConfiguration();

        FilterRegistrationBean<SaServletFilter> routeFilter = autoConfiguration.routePermissionFilter(
                routePermissionProperties()
        );
        FilterRegistrationBean<SaTokenPrincipalFilter> principalFilter = autoConfiguration.saTokenPrincipalFilter();

        assertThat(routeFilter.getOrder()).isEqualTo(2);
        assertThat(principalFilter.getOrder()).isEqualTo(3);
        assertThat(routeFilter.getFilter().includeList).containsExactly("/api/v1/**");
        assertThat(routeFilter.getFilter().excludeList).containsExactly("/api/v1/auth/login", "/api/v1/auth/activate", "/api/v1/health");
        assertThat(routeFilter.getUrlPatterns()).containsExactlyInAnyOrder("/api/v1/*");
        assertThat(principalFilter.getUrlPatterns()).containsExactlyInAnyOrder("/api/v1/*");
    }

    @Test
    void routePermissionFilterClearsThreadLocalSecurityContextBeforeAuth() {
        SaTokenWebSecurityAutoConfiguration autoConfiguration = new SaTokenWebSecurityAutoConfiguration();
        SaServletFilter routeFilter = autoConfiguration.routePermissionFilter(
                routePermissionProperties()
        ).getFilter();
        TenantContext.set(TenantId.of("2001"));
        PrincipalContext.set(new AuthenticatedPrincipal(TenantId.of("2001"), "user", "1001"));

        routeFilter.beforeAuth.run(null);

        assertThat(PrincipalContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::current).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void routePermissionCheckUsesSaRouterAndStpInterfacePermissionBridge() {
        StpLogic stpLogic = mock(StpLogic.class);

        SaTokenWebSecurityAutoConfiguration.checkRoutePermissions(
                "GET",
                "/api/v1/model-providers",
                List.of(new RoutePermissionRule("GET", "/api/v1/model-providers", "model-hub:provider:read")),
                stpLogic
        );

        verify(stpLogic).checkLogin();
        verify(stpLogic).checkPermission("model-hub:provider:read");
    }

    @Test
    void releaseEvidenceUsesReadAtTheRouteBridgeWhileRollbackKeepsPublish() {
        List<RoutePermissionRule> rules = List.of(
                new RoutePermissionRule(
                        "GET",
                        "/api/v1/ai-applications/*/releases",
                        "app-studio:application:read"
                ),
                new RoutePermissionRule(
                        "GET",
                        "/api/v1/ai-applications/*/releases/*",
                        "app-studio:application:read"
                ),
                new RoutePermissionRule(
                        "POST",
                        "/api/v1/ai-applications/*/releases/*/rollback",
                        "app-studio:application:publish"
                )
        );
        StpLogic listLogic = mock(StpLogic.class);
        StpLogic detailLogic = mock(StpLogic.class);
        StpLogic rollbackLogic = mock(StpLogic.class);

        SaTokenWebSecurityAutoConfiguration.checkRoutePermissions(
                "GET",
                "/api/v1/ai-applications/42/releases",
                rules,
                listLogic
        );
        SaTokenWebSecurityAutoConfiguration.checkRoutePermissions(
                "GET",
                "/api/v1/ai-applications/42/releases/7",
                rules,
                detailLogic
        );
        SaTokenWebSecurityAutoConfiguration.checkRoutePermissions(
                "POST",
                "/api/v1/ai-applications/42/releases/7/rollback",
                rules,
                rollbackLogic
        );

        verify(listLogic).checkLogin();
        verify(listLogic).checkPermission("app-studio:application:read");
        verify(listLogic, never()).checkPermission("app-studio:application:publish");
        verify(detailLogic).checkLogin();
        verify(detailLogic).checkPermission("app-studio:application:read");
        verify(detailLogic, never()).checkPermission("app-studio:application:publish");
        verify(rollbackLogic).checkLogin();
        verify(rollbackLogic).checkPermission("app-studio:application:publish");
        verify(rollbackLogic, never()).checkPermission("app-studio:application:read");
    }

    @Test
    void routePermissionCheckFailsClosedForUnconfiguredProtectedRoute() {
        StpLogic stpLogic = mock(StpLogic.class);

        assertThatThrownBy(() -> SaTokenWebSecurityAutoConfiguration.checkRoutePermissions(
                "POST",
                "/api/v1/unregistered",
                List.of(new RoutePermissionRule("GET", "/api/v1/model-providers", "model-hub:provider:read")),
                stpLogic
        ))
                .isInstanceOfSatisfying(NotPermissionException.class, error ->
                        assertThat(error.getPermission()).isEqualTo("route-permission:unconfigured"));

        verify(stpLogic).checkLogin();
        verify(stpLogic, never()).checkPermission("model-hub:provider:read");
    }

    @Test
    void routePermissionCheckKeepsSaTokenPermissionDenial() {
        StpLogic stpLogic = mock(StpLogic.class);
        doThrow(new NotPermissionException("approval-center:task:decide", "login"))
                .when(stpLogic)
                .checkPermission("approval-center:task:decide");

        assertThatThrownBy(() -> SaTokenWebSecurityAutoConfiguration.checkRoutePermissions(
                "POST",
                "/api/v1/approvals/tasks/2/actions",
                List.of(new RoutePermissionRule(
                        "POST",
                        "/api/v1/approvals/tasks/*/actions",
                        "approval-center:task:decide"
                )),
                stpLogic
        ))
                .isInstanceOf(NotPermissionException.class)
                .hasMessageContaining("approval-center:task:decide");

        verify(stpLogic).checkLogin();
    }

    private static RoutePermissionProperties routePermissionProperties() {
        return new RoutePermissionProperties(List.of(
                new RoutePermissionRule("GET", "/api/v1/auth/me", "identity-access:session:read")
        ));
    }
}
