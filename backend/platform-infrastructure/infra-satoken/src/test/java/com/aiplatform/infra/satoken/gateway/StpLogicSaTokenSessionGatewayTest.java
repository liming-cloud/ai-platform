package com.aiplatform.infra.satoken.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpLogic;
import org.junit.jupiter.api.Test;

class StpLogicSaTokenSessionGatewayTest {
    @Test
    void createLoginSessionNormalizesLoginIdAndRequiresPositiveTimeout() {
        StpLogic stpLogic = mock(StpLogic.class);
        when(stpLogic.createLoginSession(eq("2001:user:1001"), any(SaLoginModel.class)))
                .thenReturn("token-2001-1001");
        StpLogicSaTokenSessionGateway gateway = new StpLogicSaTokenSessionGateway(stpLogic);

        String token = gateway.createLoginSession(" 2001 : user : 1001 ", 3600L);

        assertThat(token).isEqualTo("token-2001-1001");
        verify(stpLogic).createLoginSession(eq("2001:user:1001"), any(SaLoginModel.class));
        assertThatThrownBy(() -> gateway.createLoginSession("2001:user:1001", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("令牌有效期必须为正数");
    }

    @Test
    void createLoginSessionRejectsBlankLoginId() {
        StpLogic stpLogic = mock(StpLogic.class);
        StpLogicSaTokenSessionGateway gateway = new StpLogicSaTokenSessionGateway(stpLogic);

        assertThatThrownBy(() -> gateway.createLoginSession(" ", 3600L))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体不能为空白");
        verifyNoInteractions(stpLogic);
    }

    @Test
    void findLoginIdByTokenNormalizesLoginIdReturnedBySaToken() {
        StpLogic stpLogic = mock(StpLogic.class);
        when(stpLogic.getLoginIdByToken("token-2001-1001")).thenReturn(" 2001 : user : 1001 ");
        StpLogicSaTokenSessionGateway gateway = new StpLogicSaTokenSessionGateway(stpLogic);

        assertThat(gateway.findLoginIdByToken(" token-2001-1001 "))
                .contains("2001:user:1001");
        verify(stpLogic).getLoginIdByToken("token-2001-1001");
    }

    @Test
    void findLoginIdByTokenRejectsMalformedLoginIdReturnedBySaToken() {
        StpLogic stpLogic = mock(StpLogic.class);
        when(stpLogic.getLoginIdByToken("token-2001-1001")).thenReturn("malformed");
        StpLogicSaTokenSessionGateway gateway = new StpLogicSaTokenSessionGateway(stpLogic);

        assertThatThrownBy(() -> gateway.findLoginIdByToken("token-2001-1001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体格式非法");
    }

    @Test
    void findLoginIdByTokenRejectsNonStringLoginIdReturnedBySaToken() {
        StpLogic stpLogic = mock(StpLogic.class);
        when(stpLogic.getLoginIdByToken("token-2001-1001")).thenReturn(new StringBuilder("2001:user:1001"));
        StpLogicSaTokenSessionGateway gateway = new StpLogicSaTokenSessionGateway(stpLogic);

        assertThatThrownBy(() -> gateway.findLoginIdByToken("token-2001-1001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Sa-Token 登录主体类型非法");
    }

    @Test
    void rejectsBlankTokenBeforeCallingSaToken() {
        StpLogic stpLogic = mock(StpLogic.class);
        StpLogicSaTokenSessionGateway gateway = new StpLogicSaTokenSessionGateway(stpLogic);

        assertThatThrownBy(() -> gateway.findLoginIdByToken(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("令牌值不能为空");
        assertThatThrownBy(() -> gateway.getTokenTimeoutSeconds(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("令牌值不能为空");
        assertThatThrownBy(() -> gateway.revokeToken(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("令牌值不能为空");
        verifyNoInteractions(stpLogic);
    }
}
