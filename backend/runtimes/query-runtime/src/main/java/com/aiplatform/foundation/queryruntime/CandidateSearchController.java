package com.aiplatform.foundation.queryruntime;

import com.aiplatform.foundation.security.identity.TrustedRequestContext;
import com.aiplatform.foundation.trustedquery.api.CandidateSearchUseCase;
import com.aiplatform.foundation.trustedquery.api.model.CandidateSearchQuery;
import com.aiplatform.foundation.trustedquery.api.model.CandidateSearchResult;
import java.util.Objects;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 受信自然语言候选检索入口。
 */
@RestController
@RequestMapping("/api/v1/query-candidates")
@ConditionalOnProperty(
        name = "foundation.query.enabled",
        havingValue = "true")
public class CandidateSearchController {

    private final CandidateSearchUseCase searches;

    public CandidateSearchController(CandidateSearchUseCase searches) {
        this.searches = Objects.requireNonNull(searches, "候选检索用例不能为空");
    }

    @PostMapping
    public CandidateSearchResult search(
            TrustedRequestContext context,
            @RequestBody CandidateSearchRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("请求体不能为空");
        }
        return searches.search(new CandidateSearchQuery(
                context,
                request.knowledgeSpaceId(),
                request.text(),
                request.limit() == null ? 5 : request.limit()));
    }

    /**
     * 查询正文只包含知识空间、文本和结果上限，不包含租户。
     */
    public record CandidateSearchRequest(
            String knowledgeSpaceId,
            String text,
            Integer limit) {
    }
}
