package com.aiplatform.foundation.organizationaccess.application.authorization.port.out.candidate;

import com.aiplatform.foundation.organizationaccess.application.authorization.dto.PlatformAccountCandidate;
import com.aiplatform.foundation.shared.contract.paging.PageQuery;
import com.aiplatform.foundation.shared.contract.paging.PageResult;

/** 以登录名搜索账户候选，禁止返回凭据、修订或角色绑定明细。 */
@FunctionalInterface
public interface PlatformAccountCandidateDirectory {

    PageResult<PlatformAccountCandidate> search(String query, PageQuery page);
}
