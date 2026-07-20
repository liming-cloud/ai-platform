package com.aiplatform.identity.infrastructure.persistence.repository;

import static com.aiplatform.shared.kernel.DatabaseIdParser.parseRequiredLong;

import com.aiplatform.identity.infrastructure.persistence.mapper.IamAuthorizationMapper;
import com.aiplatform.shared.kernel.TenantId;
import com.aiplatform.shared.security.feature.port.TenantFeatureAccessPort;
import java.util.List;
import java.util.Objects;
import org.springframework.stereotype.Repository;

/**
 * IAM 租户功能开关适配器，复用 `iam_tenant_feature` 作为产品功能事实源。
 */
@Repository
public class IamTenantFeatureAccessAdapter implements TenantFeatureAccessPort {
    private final IamAuthorizationMapper mapper;

    public IamTenantFeatureAccessAdapter(IamAuthorizationMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "授权查询 Mapper 不能为空");
    }

    @Override
    public boolean isFeatureEnabled(TenantId tenantId, String featureCode) {
        Long tenantDatabaseId = parseTenantId(tenantId);
        String normalizedFeatureCode = requireFeatureCode(featureCode);
        return mapper.countTenantFeatures(tenantDatabaseId, List.of(normalizedFeatureCode)) == 1;
    }

    private static Long parseTenantId(TenantId tenantId) {
        Objects.requireNonNull(tenantId, "租户 ID 不能为空");
        return parseRequiredLong(tenantId.value(), "租户 ID");
    }

    private static String requireFeatureCode(String featureCode) {
        if (featureCode == null || featureCode.isBlank()) {
            throw new IllegalArgumentException("功能编码不能为空");
        }
        return featureCode.trim();
    }
}
