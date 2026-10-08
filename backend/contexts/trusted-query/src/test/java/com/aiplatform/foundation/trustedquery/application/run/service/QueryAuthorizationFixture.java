package com.aiplatform.foundation.trustedquery.application.run.service;

import com.aiplatform.foundation.organizationaccess.application.member.facade.MemberAccessFacade;
import com.aiplatform.foundation.organizationaccess.application.member.facade.MemberEffectiveAccess;
import java.util.Map;
import java.util.Set;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 原有查询单元测试的显式外部事实替身，不用于证明真实授权或生产事务。 */
public final class QueryAuthorizationFixture {
    private QueryAuthorizationFixture() {}
    /** 只在测试代码中构造允许调用的依赖，生产没有缺失事实回退。 */
    public static QueryRunAuthorization allowed() {
        var facade = mock(MemberAccessFacade.class);
        when(facade.read(any())).thenReturn(new MemberEffectiveAccess(true, Map.of(
                "assistantSelection", true, "conversation", true, "ownHistory", true, "authorizedCitations", true), Set.of()));
        return new QueryRunAuthorization(facade);
    }
}
