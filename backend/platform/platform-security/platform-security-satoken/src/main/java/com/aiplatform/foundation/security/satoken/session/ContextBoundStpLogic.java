package com.aiplatform.foundation.security.satoken.session;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.stp.StpLogic;
import java.util.Objects;

/**
 * 把 Sa-Token 逻辑对象绑定到所属应用上下文的数据访问器和配置。
 *
 * <p>Sa-Token 默认从全局管理器读取这两个对象。显式绑定后，不同应用上下文不会互相
 * 覆盖会话存储，同时认证请求之间也不需要全局互斥。
 */
public final class ContextBoundStpLogic extends StpLogic {

    private final SaTokenDao saTokenDao;

    public ContextBoundStpLogic(
            String loginType,
            SaTokenDao saTokenDao,
            SaTokenConfig saTokenConfig) {
        super(loginType);
        this.saTokenDao = Objects.requireNonNull(
                saTokenDao,
                "Sa-Token 数据访问器不能为空");
        setConfig(Objects.requireNonNull(
                saTokenConfig,
                "Sa-Token 配置不能为空"));
    }

    @Override
    public SaTokenDao getSaTokenDao() {
        return saTokenDao;
    }
}
