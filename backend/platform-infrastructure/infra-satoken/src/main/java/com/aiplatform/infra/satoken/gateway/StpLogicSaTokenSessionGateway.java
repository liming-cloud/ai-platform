package com.aiplatform.infra.satoken.gateway;

import cn.dev33.satoken.stp.SaLoginModel;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import com.aiplatform.infra.satoken.codec.SaTokenPrincipalCodec;
import java.util.Objects;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 基于 Sa-Token `StpLogic` 的会话网关，生产运行时由 Sa-Token Redis DAO 持久化会话数据。
 */
@Component
public class StpLogicSaTokenSessionGateway implements SaTokenSessionGateway {
    private final StpLogic stpLogic;

    public StpLogicSaTokenSessionGateway() {
        this(StpUtil.getStpLogic());
    }

    StpLogicSaTokenSessionGateway(StpLogic stpLogic) {
        this.stpLogic = Objects.requireNonNull(stpLogic, "Sa-Token 逻辑组件不能为空");
    }

    @Override
    public String createLoginSession(String loginId, long timeoutSeconds) {
        loginId = SaTokenPrincipalCodec.requireLoginId(loginId);
        if (timeoutSeconds <= 0) {
            throw new IllegalArgumentException("令牌有效期必须为正数");
        }
        return stpLogic.createLoginSession(
                loginId,
                new SaLoginModel().setTimeout(timeoutSeconds).setIsWriteHeader(false)
        );
    }

    @Override
    public Optional<String> findLoginIdByToken(String tokenValue) {
        tokenValue = requireText(tokenValue, "令牌值");
        Object loginId = stpLogic.getLoginIdByToken(tokenValue);
        if (loginId == null) {
            return Optional.empty();
        }
        return Optional.of(SaTokenPrincipalCodec.requireLoginId(loginId));
    }

    @Override
    public long getTokenTimeoutSeconds(String tokenValue) {
        tokenValue = requireText(tokenValue, "令牌值");
        return stpLogic.getTokenTimeout(tokenValue);
    }

    @Override
    public void revokeToken(String tokenValue) {
        tokenValue = requireText(tokenValue, "令牌值");
        stpLogic.logoutByTokenValue(tokenValue);
    }

    private static String requireText(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + "不能为空");
        }
        return value.trim();
    }
}
