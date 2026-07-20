package com.aiplatform.infra.satoken.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpLogic;
import com.aiplatform.shared.kernel.TenantContext;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.context.PrincipalContext;
import com.aiplatform.shared.security.model.AuthenticatedPrincipal;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class SaTokenPrincipalFilterTest {
    @AfterEach
    void tearDown() {
        TenantContext.clear();
        PrincipalContext.clear();
    }

    @Test
    void authenticatedRequestSetsPrincipalAndTenantFromSaTokenLoginId() throws Exception {
        StpLogic stpLogic = mock(StpLogic.class);
        when(stpLogic.getLoginIdDefaultNull()).thenReturn("2001:user:1001");
        SaTokenPrincipalFilter filter = new SaTokenPrincipalFilter(stpLogic);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/model-providers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TenantContext.set(TenantId.of("default"));
        AtomicReference<String> actorId = new AtomicReference<>();
        AtomicReference<TenantId> tenantId = new AtomicReference<>();

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            actorId.set(PrincipalContext.currentActorId());
            tenantId.set(TenantContext.current());
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(actorId).hasValue("1001");
        assertThat(tenantId).hasValue(TenantId.of("2001"));
        assertThat(PrincipalContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::current).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void requestWithoutSaTokenLoginClearsStaleSecurityContextBeforeController() throws Exception {
        StpLogic stpLogic = mock(StpLogic.class);
        when(stpLogic.getLoginIdDefaultNull()).thenReturn(null);
        SaTokenPrincipalFilter filter = new SaTokenPrincipalFilter(stpLogic);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/model-providers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        TenantContext.set(TenantId.of("stale"));
        PrincipalContext.set(new AuthenticatedPrincipal(
                TenantId.of("stale"),
                "user",
                "stale-user"
        ));
        AtomicReference<Boolean> reachedController = new AtomicReference<>(false);

        filter.doFilter(request, response, (servletRequest, servletResponse) -> {
            assertThat(PrincipalContext.current()).isEmpty();
            assertThatThrownBy(TenantContext::current).isInstanceOf(IllegalStateException.class);
            reachedController.set(true);
        });

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(reachedController).hasValue(true);
        assertThat(PrincipalContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::current).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsMalformedLoginIdInsteadOfAnonymousContext() {
        StpLogic stpLogic = mock(StpLogic.class);
        when(stpLogic.getLoginIdDefaultNull()).thenReturn("malformed");
        SaTokenPrincipalFilter filter = new SaTokenPrincipalFilter(stpLogic);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/model-providers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> reachedController = new AtomicReference<>(false);

        assertThatThrownBy(() -> filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> reachedController.set(true)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体格式非法");

        assertThat(reachedController).hasValue(false);
        assertThat(PrincipalContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::current).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void rejectsNonStringLoginIdInsteadOfToStringCoercion() {
        StpLogic stpLogic = mock(StpLogic.class);
        when(stpLogic.getLoginIdDefaultNull()).thenReturn(new StringBuilder("2001:user:1001"));
        SaTokenPrincipalFilter filter = new SaTokenPrincipalFilter(stpLogic);
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/model-providers");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<Boolean> reachedController = new AtomicReference<>(false);

        assertThatThrownBy(() -> filter.doFilter(
                request,
                response,
                (servletRequest, servletResponse) -> reachedController.set(true)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体类型非法");

        assertThat(reachedController).hasValue(false);
        assertThat(PrincipalContext.current()).isEmpty();
        assertThatThrownBy(TenantContext::current).isInstanceOf(IllegalStateException.class);
    }
}
