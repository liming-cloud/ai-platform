package com.aiplatform.foundation.trustedquery.api;

import com.aiplatform.foundation.trustedquery.api.model.CandidateSearchQuery;
import com.aiplatform.foundation.trustedquery.api.model.CandidateSearchResult;

/**
 * 在可信租户边界内把自然语言查询转换为授权候选的公共用例。
 */
public interface CandidateSearchUseCase {

    CandidateSearchResult search(CandidateSearchQuery query);
}
