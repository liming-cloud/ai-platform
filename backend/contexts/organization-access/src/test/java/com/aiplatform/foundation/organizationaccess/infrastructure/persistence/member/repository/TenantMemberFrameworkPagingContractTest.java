package com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.repository;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aiplatform.foundation.organizationaccess.infrastructure.persistence.member.mapper.TenantMemberDirectoryMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import java.util.Arrays;
import org.junit.jupiter.api.Test;

/** 成员目录须通过 MyBatis-Plus 分页合同取得同范围总数，禁止手工 LIMIT/OFFSET 分页。 */
class TenantMemberFrameworkPagingContractTest {
    @Test
    void 成员目录映射器应接受并返回框架分页合同() {
        var query = Arrays.stream(TenantMemberDirectoryMapper.class.getDeclaredMethods())
                .filter(method -> method.getName().equals("selectPage"))
                .findFirst().orElseThrow();
        assertTrue(IPage.class.isAssignableFrom(query.getReturnType()), "成员目录必须返回 IPage");
        assertTrue(Arrays.stream(query.getParameterTypes()).anyMatch(IPage.class::isAssignableFrom),
                "成员目录必须接收 IPage，由分页插件管理偏移和总数");
    }
}
